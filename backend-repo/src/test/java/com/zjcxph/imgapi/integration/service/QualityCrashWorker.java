package com.zjcxph.imgapi.integration.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zjcxph.imgapi.service.impl.DataQualityServiceImpl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;

/** Child JVM fixture. Only invoked by the isolated PostgreSQL integration test. */
public final class QualityCrashWorker {
    public static void main(String[] args) throws Exception {
        var config = new HikariConfig();
        config.setJdbcUrl(System.getenv("MRR_CRASH_JDBC"));
        config.setUsername(System.getenv("MRR_CRASH_USER"));
        config.setPassword(System.getenv("MRR_CRASH_PASSWORD"));
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(2000);
        var metrics = new SimpleMeterRegistry();
        try (var ds = new HikariDataSource(config)) {
            var service = new DataQualityServiceImpl(new JdbcTemplate(ds), metrics);
            ReflectionTestUtils.setField(service, "retentionDays", 90);
            var ctor = Class.forName("com.zjcxph.imgapi.service.impl.DataQualityCheckDefinition")
                    .getDeclaredConstructor(String.class,String.class,String.class,String.class,String.class);
            ctor.setAccessible(true);
            var first = ctor.newInstance("CRASH_FIRST","synthetic","WARNING","SELECT 0::bigint","SELECT 1");
            var second = ctor.newInstance("CRASH_SECOND","synthetic","WARNING",
                    args[0].equals("slow") ? "SELECT 0::bigint FROM pg_sleep(9)" : "SELECT 0::bigint","SELECT 1");
            ReflectionTestUtils.setField(service, "checks", List.of(first,second));
            service.processPending();
            var row=new JdbcTemplate(ds).queryForMap("SELECT id,status,error_message,deadline_at,clock_timestamp() AS observed FROM app.mrr_data_quality_run WHERE task_managed ORDER BY id DESC LIMIT 1");
            System.out.println("MRR_CHILD_OUTCOME "+row);
        } finally { metrics.close(); }
    }
}
