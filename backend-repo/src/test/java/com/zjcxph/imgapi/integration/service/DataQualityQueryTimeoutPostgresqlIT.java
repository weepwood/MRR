package com.zjcxph.imgapi.integration.service;

import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.service.impl.DataQualityServiceImpl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MybatisTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DataQualityQueryTimeoutPostgresqlIT extends PostgresqlIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;

    @Test
    void oneRowSlowSampleIsCancelledAndFailureDoesNotBlockTheNextRun() throws Exception {
        var metrics = new SimpleMeterRegistry();
        var service = new DataQualityServiceImpl(jdbc, metrics);
        ReflectionTestUtils.setField(service, "sampleLimit", 1);
        ReflectionTestUtils.setField(service, "retentionDays", 90);
        ReflectionTestUtils.setField(service, "checks", List.of(check(
                "SYNTHETIC_TIMEOUT", "synthetic", "WARNING", "SELECT 1::bigint",
                "SELECT 'mr_scan' AS entity_type, '1' AS entity_id, NULL::text AS bah, NULL::text AS sjh, "
                        + "'synthetic' AS detail FROM pg_sleep(12)")));
        String trigger = "synthetic-timeout-test";
        try {
            long started = System.nanoTime();
            assertThatThrownBy(() -> service.runChecks(trigger))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("数据质量检查失败")
                    .hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class);
            assertThat((System.nanoTime() - started) / 1_000_000_000.0).isLessThan(20);
            var failed = jdbc.queryForMap("SELECT id,status,error_message FROM app.mrr_data_quality_run "
                    + "WHERE triggered_by=? ORDER BY id DESC LIMIT 1", trigger);
            assertThat(failed.get("status")).isEqualTo("FAILED");
            assertThat(failed.get("error_message")).isEqualTo("QueryTimeoutException");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app.mrr_data_quality_check_result WHERE run_id=?",
                    Long.class, failed.get("id"))).isZero();
            assertThat(metrics.get("mrr.data.quality.running").gauge().value()).isZero();

            ReflectionTestUtils.setField(service, "checks", List.of(check(
                    "SYNTHETIC_RECOVERED", "synthetic", "WARNING", "SELECT 0::bigint", "SELECT 1")));
            service.runChecks(trigger);
            assertThat(jdbc.queryForObject("SELECT status FROM app.mrr_data_quality_run "
                    + "WHERE triggered_by=? ORDER BY id DESC LIMIT 1", String.class, trigger)).isEqualTo("SUCCESS");
            assertThat(metrics.get("mrr.data.quality.running").gauge().value()).isZero();
        } finally {
            jdbc.update("DELETE FROM app.mrr_data_quality_run WHERE triggered_by=?", trigger);
            metrics.close();
        }
    }

    private Object check(String code, String name, String severity, String countSql, String sampleSql)
            throws ReflectiveOperationException {
        // 测试限定的慢规则，不为夹具扩大生产检查目录的可见性。
        var constructor = Class.forName("com.zjcxph.imgapi.service.impl.DataQualityCheckDefinition")
                .getDeclaredConstructor(String.class, String.class, String.class, String.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(code, name, severity, countSql, sampleSql);
    }
}
