package com.zjcxph.imgapi.integration.database;

import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.service.DataQualityRepairOrderService;
import com.zjcxph.imgapi.service.DataQualityService;
import com.zjcxph.imgapi.service.DeploymentReadinessService;
import com.zjcxph.imgapi.service.MaintenanceModeService;
import com.zjcxph.imgapi.service.impl.DataQualityServiceImpl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

// This cached Spring context only tests repair transactions; it must not claim
// quality jobs belonging to independent JVM/other integration test fixtures.
@MybatisTest(properties={"app.data-quality.repair-enabled=true","app.data-quality.async-enabled=false"})
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({DataQualityRepairOrderService.class,RepairOrderTransactionPostgresqlIT.Dependencies.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class RepairOrderTransactionPostgresqlIT extends PostgresqlIntegrationTestSupport {
    @Autowired TestJdbc jdbc;
    @Autowired DataSource dataSource;
    @Autowired DataQualityRepairOrderService service;

    static class TestJdbc extends JdbcTemplate {
        boolean failAudit;
        TestJdbc(DataSource source) { super(source); }
        @Override public int update(String sql,Object... args) {
            if (failAudit && sql.contains("INSERT INTO app.mrr_data_quality_repair_event") && "APPLIED".equals(args[2]))
                throw new IllegalStateException("synthetic audit failure");
            return super.update(sql,args);
        }
    }

    @TestConfiguration
    static class Dependencies {
        @Bean TestJdbc jdbcTemplate(DataSource ds) { return new TestJdbc(ds); }
        @Bean DataQualityService quality(JdbcTemplate jdbc) {
            return new DataQualityServiceImpl(jdbc,new SimpleMeterRegistry());
        }
        @Bean DeploymentReadinessService readiness() { return mock(DeploymentReadinessService.class); }
        @Bean MaintenanceModeService maintenance() { return mock(MaintenanceModeService.class); }
    }

    @Test
    void realProxyRollsBackAndIndependentConnectionRejectsContendedRows() throws Exception {
        assertThat(AopUtils.isAopProxy(service)).isTrue();
        String bah="7654319";
        int scan=jdbc.queryForObject("INSERT INTO app.mr_scan(bah,filename,uploadflag) VALUES (?,'synthetic.png',1) RETURNING id",Integer.class,bah);
        long archive=0,run=0,order=0;
        try {
            archive=jdbc.queryForObject("INSERT INTO app.mr_archive(bah) VALUES (?) RETURNING id",Long.class,bah);
            run=jdbc.queryForObject("INSERT INTO app.mrr_data_quality_run(status,triggered_by) VALUES ('SUCCESS','test') RETURNING id",Long.class);
            long issue=jdbc.queryForObject("""
                INSERT INTO app.mrr_data_quality_issue(run_id,check_code,check_name,severity,entity_type,entity_id,bah,detail)
                VALUES (?,'SCAN_ARCHIVE_LINK_MISSING_ESTIMATED','synthetic','WARNING','mr_scan',?,?,'synthetic') RETURNING id
                """,Long.class,run,Integer.toString(scan),bah);
            long id=service.propose(issue,11).id();
            order=id;
            assertThat(service.list(archive,issue,null,11).orders()).extracting(DataQualityRepairOrderService.Order::id).containsExactly(id);
            assertThat(service.list(archive,issue,id,11).orders()).isEmpty();
            assertThat(service.approve(id,12).status()).isEqualTo("APPROVED");
            try (Connection lock=dataSource.getConnection()) {
                lock.setAutoCommit(false);
                try (var stmt=lock.prepareStatement("SELECT id FROM app.mr_scan WHERE id=? FOR UPDATE")) {
                    stmt.setInt(1,scan);
                    try (var rows=stmt.executeQuery()) { assertThat(rows.next()).isTrue(); }
                }
                assertThatThrownBy(()->service.apply(id,12)).isInstanceOf(DataAccessException.class);
                lock.rollback();
            }
            try (Connection lock=dataSource.getConnection()) {
                lock.setAutoCommit(false);
                try (var stmt=lock.createStatement()) { stmt.execute("LOCK TABLE app.mr_archive IN ROW EXCLUSIVE MODE"); }
                assertThatThrownBy(()->service.apply(id,12)).isInstanceOf(DataAccessException.class);
                lock.rollback();
            }
            assertThat(service.get(id).status()).isEqualTo("APPROVED");
            assertThat(service.events(id,0)).hasSize(2);
            assertThat(jdbc.queryForObject("SELECT archive_id FROM app.mr_scan WHERE id=?",Long.class,scan)).isNull();
            jdbc.failAudit=true;
            assertThatThrownBy(()->service.apply(id,12)).hasMessageContaining("synthetic audit failure");
            jdbc.failAudit=false;
            assertThat(jdbc.queryForObject("SELECT archive_id FROM app.mr_scan WHERE id=?",Long.class,scan)).isNull();
            assertThat(service.get(id).status()).isEqualTo("APPROVED");
            assertThat(service.events(id,0)).hasSize(2);
            var contenders=java.util.concurrent.Executors.newFixedThreadPool(2);
            var gate=new java.util.concurrent.CountDownLatch(1);
            try {
                java.util.concurrent.Callable<Boolean> apply=()->{
                    gate.await();
                    try { return "APPLIED".equals(service.apply(id,12).status()); }
                    catch (DataAccessException | com.zjcxph.imgapi.exception.BusinessException conflict) { return false; }
                };
                var first=contenders.submit(apply);var second=contenders.submit(apply);
                gate.countDown();
                assertThat(first.get(8,java.util.concurrent.TimeUnit.SECONDS)
                        ^second.get(8,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                assertThat(service.get(id).status()).isEqualTo("APPLIED");
                assertThat(service.events(id,0)).extracting(DataQualityRepairOrderService.Event::action)
                        .containsExactly("PROPOSED","APPROVED","APPLIED");
            } finally { contenders.shutdownNow(); }
            assertThat(service.verify(id,13).status()).isEqualTo("VERIFIED");
            assertThat(service.events(id,0)).extracting(DataQualityRepairOrderService.Event::action)
                .containsExactly("PROPOSED","APPROVED","APPLIED","VERIFIED");
            assertThat(jdbc.queryForObject("SELECT archive_id FROM app.mr_scan WHERE id=?",Long.class,scan)).isEqualTo(archive);
        } finally {
            jdbc.failAudit=false;
            if(order!=0) {
                jdbc.update("DELETE FROM app.mrr_data_quality_repair_event WHERE order_id=?",order);
                jdbc.update("DELETE FROM app.mrr_data_quality_repair_order WHERE id=?",order);
            }
            if(run!=0) jdbc.update("DELETE FROM app.mrr_data_quality_run WHERE id=?",run);
            jdbc.update("DELETE FROM app.mr_scan WHERE id=?",scan);
            if(archive!=0) jdbc.update("DELETE FROM app.mr_archive WHERE id=?",archive);
        }
    }
}
