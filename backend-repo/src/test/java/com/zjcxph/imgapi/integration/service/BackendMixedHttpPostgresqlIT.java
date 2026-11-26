package com.zjcxph.imgapi.integration.service;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.config.ImageDeliveryProperties;
import com.zjcxph.imgapi.controller.DataQualityController;
import com.zjcxph.imgapi.controller.ScanController;
import com.zjcxph.imgapi.handler.GlobalExceptionHandler;
import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.interceptors.AuthorizationInterceptor;
import com.zjcxph.imgapi.interceptors.LoginInterceptor;
import com.zjcxph.imgapi.mapper.AuthUserMapper;
import com.zjcxph.imgapi.mapper.ScanMapper;
import com.zjcxph.imgapi.mapper.SystemSettingMapper;
import com.zjcxph.imgapi.security.TokenBlacklist;
import com.zjcxph.imgapi.service.DeveloperApiAccessService;
import com.zjcxph.imgapi.service.DeveloperModeService;
import com.zjcxph.imgapi.service.impl.DataQualityServiceImpl;
import com.zjcxph.imgapi.service.impl.ScanServiceImpl;
import com.zjcxph.imgapi.utils.JwtUtil;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Real loopback HTTP, production MVC handlers, JWT authorization, MyBatis/JDBC and isolated PG16.
 *  Does not load the deployed application, Nginx, logging interceptors or actual storage. */
@MybatisTest
@org.springframework.test.context.ContextConfiguration(classes = com.zjcxph.imgapi.ImageApiApplication.class)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {"mybatis.configuration.map-underscore-to-camel-case=true",
        "mybatis.mapper-locations=classpath*:mapper/*.xml"})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BackendMixedHttpPostgresqlIT extends PostgresqlIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired AuthUserMapper users;
    @Autowired ScanMapper scans;
    @Autowired SystemSettingMapper settings;
    @TempDir Path directory;

    @Test
    void mixedReadsUseRealAuthorizationAndPoolWhileQualityRunExecutes() throws Exception {
        String role = "SYNTHETIC_HTTP_TEST";
        String username = "synthetic-http-test";
        long run = 0;
        long manualRun = 0;
        var metrics = new SimpleMeterRegistry();
        var service = new DataQualityServiceImpl(jdbc, metrics);
        ReflectionTestUtils.setField(service, "sampleLimit", 10);
        ReflectionTestUtils.setField(service, "retentionDays", 90);
        var constructor = Class.forName("com.zjcxph.imgapi.service.impl.DataQualityCheckDefinition")
                .getDeclaredConstructor(String.class, String.class, String.class, String.class, String.class);
        constructor.setAccessible(true);
        ReflectionTestUtils.setField(service, "checks", List.of(constructor.newInstance("HTTP_TEST", "synthetic", "WARNING",
                "SELECT 0::bigint FROM pg_sleep(2)", "SELECT 1")));
        var blacklist = new TokenBlacklist(jdbc);
        var mode = new DeveloperModeService(settings, false, "127.0.0.1,::1", new ImageDeliveryProperties());
        var login = new LoginInterceptor(users, mode, new DeveloperApiAccessService(settings, mode));
        var tomcat = new Tomcat();
        tomcat.setBaseDir(directory.toString());
        tomcat.setPort(0);
        var connector = tomcat.getConnector();
        connector.setProperty("address", "127.0.0.1");
        var context = tomcat.addContext("", directory.toString());
        var mvc = new AnnotationConfigWebApplicationContext();
        mvc.register(MvcConfig.class);
        mvc.addBeanFactoryPostProcessor(factory -> {
            factory.registerSingleton("loginInterceptor", login);
            factory.registerSingleton("qualityController", new DataQualityController(service));
            factory.registerSingleton("scanController", new ScanController(new ScanServiceImpl(scans, null), null));
        });
        var servlet = Tomcat.addServlet(context, "mvc", new DispatcherServlet(mvc));
        servlet.setLoadOnStartup(1);
        context.addServletMappingDecoded("/", "mvc");
        String token = null;
        var workers = Executors.newFixedThreadPool(9);
        try {
            jdbc.update("INSERT INTO app.mr_auth_role(code,name,permissions) VALUES (?,?,'system:read,system:manage,record:read')", role, role);
            long userId = jdbc.queryForObject("INSERT INTO app.mr_auth_user(username,display_name,password_hash,role_code,status,must_change_password,password_version) VALUES (?,?, 'synthetic-not-a-password',?,'active',false,1) RETURNING id", Long.class, username, username, role);
            jdbc.update("UPDATE app.mr_auth_user SET contact_info='synthetic-contact' WHERE id=?", userId);
            var fullUser = users.findById(userId);
            var sessionUser = users.findSessionById(userId);
            assertThat(fullUser.getPasswordHash()).isNotNull();
            assertThat(fullUser.getContactInfo()).isEqualTo("synthetic-contact");
            assertThat(sessionUser.getPasswordHash()).isNull();
            assertThat(sessionUser.getContactInfo()).isNull();
            assertThat(sessionUser.getPermissions()).containsExactlyElementsOf(fullUser.getPermissions());
            assertThat(sessionUser.effectivePasswordVersion()).isEqualTo(fullUser.effectivePasswordVersion());
            run = jdbc.queryForObject("INSERT INTO app.mrr_data_quality_run(status,triggered_by) VALUES ('SUCCESS','synthetic-http-test') RETURNING id", Long.class);
            jdbc.update("INSERT INTO app.mrr_data_quality_issue(run_id,check_code,check_name,severity,entity_id) VALUES (?,'HTTP_TEST','synthetic','WARNING','synthetic-http')", run);
            jdbc.update("""
                    INSERT INTO app.mr_scan(bah,brxh,filename,folder,uploadflag,pages)
                    SELECT '7654300','7654300','synthetic-http-' || n || '.jpg','synthetic-http',1,n
                    FROM generate_series(1,10000) n
                    """);
            jdbc.execute("ANALYZE app.mr_scan");
            long anchor = jdbc.queryForObject("SELECT id FROM app.mr_scan ORDER BY id LIMIT 1 OFFSET 7979", Long.class);
            for (String plan : List.of(
                    "EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM app.mr_scan ORDER BY id LIMIT 20 OFFSET 7980",
                    "EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM app.mr_scan WHERE id > " + anchor + " ORDER BY id LIMIT 20",
                    "EXPLAIN (ANALYZE, BUFFERS) SELECT count(*) FROM app.mr_scan")) {
                System.out.println("MRR_HTTP_QUERY_PLAN " + plan);
                jdbc.queryForList(plan, String.class).forEach(System.out::println);
            }
            JwtUtil.configure("synthetic-http-test-key-not-used-outside-test-0123456789");
            var session = new AuthSession();
            session.setId(userId); session.setUsername(username); session.setPasswordVersion(1); session.setStatus("active");
            token = JwtUtil.getToken(session);
            final String bearer = token;
            tomcat.start();
            String base = "http://127.0.0.1:" + connector.getLocalPort();
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            List<String> paths = List.of("/api/v1/system/data-quality/summary",
                    "/api/v1/system/data-quality/issues?limit=10&runId=" + run,
                    "/api/v1/scan/page?page=400&size=20",
                    "/api/v1/scan/cursor?afterId=0&size=20");
            for (int i=0; i<16; i++) assertOk(send(client, base, paths.get(i%4), bearer, false));
            // The optional SQL counters are enabled only by the dedicated isolated lab runner.
            boolean sqlStats = "1".equals(System.getenv("MRR_TEST_HTTP_SQL_STATS"));
            long[] before = sqlStats ? sqlCounts() : new long[0];
            var pool = dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean();
            var activePeak = new AtomicInteger();
            var waitPeak = new AtomicInteger();
            var sampler = Executors.newSingleThreadScheduledExecutor();
            sampler.scheduleAtFixedRate(() -> {
                activePeak.accumulateAndGet(pool.getActiveConnections(), Math::max);
                waitPeak.accumulateAndGet(pool.getThreadsAwaitingConnection(), Math::max);
            }, 0, 5, TimeUnit.MILLISECONDS);
            long started = System.nanoTime();
            List<Long> durations = new ArrayList<>();
            try {
                var background = workers.submit(() -> send(client, base, "/api/v1/system/data-quality/run", bearer, true));
                List<Future<List<Long>>> jobs = new ArrayList<>();
                for (int i=0; i<8; i++) {
                    final int worker = i;
                    jobs.add(workers.submit(() -> {
                        List<Long> local = new ArrayList<>();
                        for (int j=0; j<500; j++) {
                            long tick = System.nanoTime();
                            assertOk(send(client, base, paths.get((j+worker)%4), bearer, false));
                            local.add(System.nanoTime()-tick);
                        }
                        return local;
                    }));
                }
                for (var job : jobs) durations.addAll(job.get(40, TimeUnit.SECONDS));
                long elapsed = System.nanoTime() - started;
                var backgroundResponse = background.get(10, TimeUnit.SECONDS);
                assertOk(backgroundResponse);
                manualRun = new com.fasterxml.jackson.databind.ObjectMapper().readTree(backgroundResponse.body())
                        .path("data").path("latestRun").path("id").asLong();
                assertThat(manualRun).isGreaterThan(run);
                assertThat(durations).hasSize(4000);
                long[] sorted = durations.stream().mapToLong(Long::longValue).sorted().toArray();
                System.out.printf(Locale.ROOT, "MRR_HTTP_MIXED requests=%d errors=0 seconds=%.3f rps=%.2f p50_ms=%.3f p95_ms=%.3f p99_ms=%.3f pool_active_peak=%d pool_wait_peak=%d%n",
                        sorted.length, elapsed/1e9, sorted.length/(elapsed/1e9), percentile(sorted,.50), percentile(sorted,.95), percentile(sorted,.99), activePeak.get(), waitPeak.get());
                if (sqlStats) {
                    long[] after = sqlCounts();
                    for(int i=0;i<after.length;i++) after[i]-=before[i];
                    System.out.println("MRR_HTTP_SQL_COUNTS [revocation,user_lookup,scan_count,scan_page,scan_cursor,quality_pinned]=" + Arrays.toString(after));
                    assertThat(after).containsExactly(0,4001,1000,1000,1000,1000);
                }
            } finally {
                sampler.shutdownNow();
            }
            // Real role change and token revocation must affect the next request.
            jdbc.update("UPDATE app.mr_auth_role SET permissions='system:read' WHERE code=?", role);
            assertOk(send(client, base, paths.getFirst(), bearer, false));
            var forbidden = send(client, base, paths.get(1), bearer, false);
            assertThat(forbidden.statusCode()).isEqualTo(403);
            assertThat(forbidden.body()).doesNotContain("synthetic-http");
            var verified = JwtUtil.verifyToken(bearer);
            blacklist.revoke(verified.jti(), verified.expiresAtMillis());
            assertThat(send(client, base, paths.getFirst(), bearer, false).statusCode()).isEqualTo(401);
            assertThat(pool.getActiveConnections()).isZero();
        } finally {
            workers.shutdownNow();
            try { tomcat.stop(); } finally { tomcat.destroy(); mvc.close(); }
            metrics.close();
            jdbc.update("DELETE FROM app.mr_scan WHERE folder='synthetic-http'");
            if (run > 0) jdbc.update("DELETE FROM app.mrr_data_quality_run WHERE id=?", run);
            if (manualRun > 0) jdbc.update("DELETE FROM app.mrr_data_quality_run WHERE id=? AND triggered_by='manual'", manualRun);
            jdbc.update("DELETE FROM app.mr_auth_user WHERE username=?", username);
            jdbc.update("DELETE FROM app.mr_auth_role WHERE code=?", role);
            if (token != null) jdbc.update("DELETE FROM app.token_revocation WHERE token_hash=?", TokenBlacklist.tokenHash(JwtUtil.verifyToken(token).jti()));
        }
    }

    @Test
    void authenticationOnlyPairedRoundsKeepDataAndPoolFixed() throws Exception {
        String role = "SYNTHETIC_AUTH_AB";
        String username = "synthetic-auth-ab";
        var blacklist = new TokenBlacklist(jdbc);
        long id = 0;
        var workers = Executors.newFixedThreadPool(8);
        try {
            jdbc.update("INSERT INTO app.mr_auth_role(code,name,permissions) VALUES (?,?,'record:read')",role,role);
            id = jdbc.queryForObject("INSERT INTO app.mr_auth_user(username,display_name,password_hash,role_code,status,must_change_password,password_version) VALUES (?,?,'synthetic',?,'active',false,1) RETURNING id",Long.class,username,username,role);
            final long uid = id;
            String hash = TokenBlacklist.tokenHash("synthetic-ab");
            Runnable a = () -> {
                assertThat(blacklist.isRevokedHash(hash)).isFalse();
                assertThat(users.findSessionById(uid).getPermissions()).contains("record:read");
            };
            Runnable b = () -> {
                var state = users.observeSession(uid,hash);
                assertThat(state.getTokenRevoked()).isFalse();
                assertThat(state.getPermissions()).contains("record:read");
            };
            for (int j=0;j<100;j++) { a.run(); b.run(); }
            var pool = dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean();
            var recording = new jdk.jfr.Recording(jdk.jfr.Configuration.getConfiguration("profile"));
            recording.setMaxSize(32L*1024*1024);
            for(String event:List.of("jdk.InitialEnvironmentVariable","jdk.InitialSystemProperty","jdk.SystemProcess")) recording.disable(event);
            recording.start();
            try {
                for (String variant : List.of("A","B","B","A")) {
                    var sampler = Executors.newSingleThreadScheduledExecutor();
                    var waits = new java.util.concurrent.ConcurrentLinkedQueue<Integer>();
                    sampler.scheduleAtFixedRate(()->waits.add(pool.getThreadsAwaitingConnection()),0,2,TimeUnit.MILLISECONDS);
                    var gate = new CountDownLatch(1);
                    var jobs = new ArrayList<Future<long[]>>();
                    Runnable action = variant.equals("A") ? a : b;
                    for (int w=0;w<8;w++) jobs.add(workers.submit(()->{
                        gate.await();
                        long[] values=new long[250];
                        for(int k=0;k<values.length;k++) {
                            long start=System.nanoTime(); action.run(); values[k]=System.nanoTime()-start;
                        }
                        return values;
                    }));
                    long start=System.nanoTime(); gate.countDown();
                    var times=new ArrayList<Long>();
                    try {
                        for(var job:jobs) for(long value:job.get(20,TimeUnit.SECONDS)) times.add(value);
                    } finally { sampler.shutdownNow(); }
                    double elapsed=(System.nanoTime()-start)/1e9;
                    long[] sorted=times.stream().mapToLong(Long::longValue).sorted().toArray();
                    System.out.printf(Locale.ROOT,"MRR_AUTH_AB %s n=%d rps=%.2f p50=%.3f p95=%.3f p99=%.3f wait_samples=%d wait_nonzero=%d wait_peak=%d%n",
                            variant,sorted.length,sorted.length/elapsed,percentile(sorted,.50),percentile(sorted,.95),percentile(sorted,.99),
                            waits.size(),waits.stream().filter(v->v>0).count(),waits.stream().mapToInt(Integer::intValue).max().orElse(0));
                    assertThat(pool.getActiveConnections()).isZero();
                }
            } finally {
                recording.stop();
                java.nio.file.Path out=java.nio.file.Path.of("target","mrr-auth-ab.jfr");
                recording.dump(out); recording.close();
            }
            // Same mapper query shape, parameters bound; no production token or medical data.
            String query = "EXPLAIN (ANALYZE, BUFFERS) SELECT " + AuthUserMapper.SESSION_COLUMNS +
                ", v.revoked FROM (SELECT EXISTS(SELECT 1 FROM app.token_revocation WHERE token_hash=? AND expires_at>CURRENT_TIMESTAMP) AS revoked) v " +
                "LEFT JOIN app.mr_auth_user u ON u.id=? LEFT JOIN app.mr_auth_role r ON r.code=u.role_code";
            jdbc.queryForList(query,String.class,hash,uid).forEach(line->System.out.println("MRR_AUTH_PLAN "+line));
        } finally {
            workers.shutdownNow();
            if(id>0) jdbc.update("DELETE FROM app.mr_auth_user WHERE id=?",id);
            jdbc.update("DELETE FROM app.mr_auth_role WHERE code=?",role);
        }
    }

    private long[] sqlCounts() {
        var row = jdbc.queryForMap("""
                SELECT COALESCE(sum(calls) FILTER (WHERE query LIKE 'SELECT EXISTS(%FROM app.token_revocation%'),0) AS revocation,
                       COALESCE(sum(calls) FILTER (WHERE query LIKE 'select u.id as id,%' AND query LIKE '%from app.token_revocation%'),0) AS user_lookup,
                       COALESCE(sum(calls) FILTER (WHERE query LIKE 'SELECT COUNT(*) FROM mr_scan%'),0) AS scan_count,
                       COALESCE(sum(calls) FILTER (WHERE query LIKE 'SELECT * FROM mr_scan ORDER BY id LIMIT%OFFSET%'),0) AS scan_page,
                       COALESCE(sum(calls) FILTER (WHERE query LIKE 'SELECT * FROM mr_scan WHERE id >%'),0) AS scan_cursor,
                       COALESCE(sum(calls) FILTER (WHERE query LIKE 'SELECT i.id, r.id AS run_id%'),0) AS quality_pinned
                FROM public.pg_stat_statements WHERE dbid = (SELECT oid FROM pg_database WHERE datname=current_database())
                """);
        return java.util.stream.Stream.of("revocation","user_lookup","scan_count","scan_page","scan_cursor","quality_pinned")
                .mapToLong(key -> ((Number)row.get(key)).longValue()).toArray();
    }

    private static double percentile(long[] values, double p) {
        return values[Math.max(0, (int)Math.ceil(values.length*p)-1)]/1e6;
    }

    private static HttpResponse<String> send(HttpClient client, String base, String path, String token, boolean post) throws Exception {
        var req = HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(8))
                .header("Authorization","Bearer "+token);
        return client.send(post ? req.POST(HttpRequest.BodyPublishers.noBody()).build() : req.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void assertOk(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).containsPattern("\"code\"\\s*:\\s*200");
    }

    @TestConfiguration
    @EnableWebMvc
    static class MvcConfig implements WebMvcConfigurer {
        @Autowired LoginInterceptor login;
        @Bean GlobalExceptionHandler errors() { return new GlobalExceptionHandler(); }
        @Override public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(login);
            registry.addInterceptor(new AuthorizationInterceptor());
        }
    }
}
