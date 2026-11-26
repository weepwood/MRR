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

    @Test
    void latestRunSampleQueryKeepsSeverityOrderLimitAndEmptyRunSemantics() {
        var metrics = new SimpleMeterRegistry();
        var service = new DataQualityServiceImpl(jdbc, metrics);
        String trigger = "synthetic-sample-query-test";
        try {
            long older = jdbc.queryForObject("INSERT INTO app.mrr_data_quality_run(status,triggered_by) VALUES ('SUCCESS',?) RETURNING id", Long.class, trigger);
            long latest = jdbc.queryForObject("INSERT INTO app.mrr_data_quality_run(status,triggered_by) VALUES ('SUCCESS',?) RETURNING id", Long.class, trigger);
            String insert = """
                    INSERT INTO app.mrr_data_quality_issue
                        (run_id,check_code,check_name,severity,entity_type,entity_id,detail)
                    VALUES (?,'SYNTHETIC','synthetic',?,'mr_scan',?,'synthetic')
                    """;
            jdbc.update(insert, older, "CRITICAL", "old");
            jdbc.update(insert, latest, "WARNING", "new-warning");
            jdbc.update(insert, latest, "CRITICAL", "new-critical");
            assertThat(service.getIssues(1)).extracting(row -> row.get("entity_id"))
                    .containsExactly("new-critical");
            assertThat(service.getIssues(500)).extracting(row -> row.get("entity_id"))
                    .containsExactly("new-critical", "new-warning");
            jdbc.update("INSERT INTO app.mrr_data_quality_run(status,triggered_by) VALUES ('RUNNING',?)", trigger);
            assertThat(service.getIssues(500)).isEmpty();
        } finally {
            jdbc.update("DELETE FROM app.mrr_data_quality_run WHERE triggered_by=?", trigger);
            metrics.close();
        }
    }

    @Test
    void invalidSecondSampleDoesNotPublishHalfOfRuleAndNextRunRecovers() throws Exception {
        var metrics = new SimpleMeterRegistry();
        var service = new DataQualityServiceImpl(jdbc, metrics);
        ReflectionTestUtils.setField(service, "sampleLimit", 1000);
        ReflectionTestUtils.setField(service, "retentionDays", 90);
        String trigger = "synthetic-atomic-samples";
        String samples = """
                SELECT 'mr_scan' AS entity_type,
                       CASE WHEN n=2 THEN repeat('x',161) ELSE n::text END AS entity_id,
                       NULL::text AS bah, NULL::text AS sjh, 'synthetic' AS detail
                FROM generate_series(1,2) n ORDER BY n
                """;
        try {
            ReflectionTestUtils.setField(service, "checks", List.of(check(
                    "SYNTHETIC_ATOMIC", "synthetic", "WARNING", "SELECT 2::bigint", samples)));
            assertThatThrownBy(() -> service.runChecks(trigger)).isInstanceOf(IllegalStateException.class);
            var failed = jdbc.queryForMap("SELECT id,status FROM app.mrr_data_quality_run WHERE triggered_by=? ORDER BY id DESC LIMIT 1", trigger);
            assertThat(failed.get("status")).isEqualTo("FAILED");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app.mrr_data_quality_issue WHERE run_id=?", Long.class, failed.get("id"))).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app.mrr_data_quality_check_result WHERE run_id=?", Long.class, failed.get("id"))).isZero();
            ReflectionTestUtils.setField(service, "checks", List.of(check(
                    "SYNTHETIC_ATOMIC", "synthetic", "WARNING", "SELECT 1000::bigint",
                    "SELECT 'mr_scan' AS entity_type, n::text AS entity_id, NULL::text AS bah, NULL::text AS sjh, 'synthetic' AS detail FROM generate_series(1,1000) n ORDER BY n")));
            service.runChecks(trigger);
            var success = jdbc.queryForMap("SELECT id,status FROM app.mrr_data_quality_run WHERE triggered_by=? ORDER BY id DESC LIMIT 1", trigger);
            assertThat(success.get("status")).isEqualTo("SUCCESS");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app.mrr_data_quality_issue WHERE run_id=?", Long.class, success.get("id"))).isEqualTo(1000L);
            assertThat(jdbc.queryForObject("SELECT sampled_count FROM app.mrr_data_quality_check_result WHERE run_id=?", Integer.class, success.get("id"))).isEqualTo(1000);
            assertThat(metrics.get("mrr.data.quality.running").gauge().value()).isZero();
        } finally {
            jdbc.update("DELETE FROM app.mrr_data_quality_run WHERE triggered_by=?", trigger);
            metrics.close();
        }
    }

    @Test
    void pinnedRunDoesNotReadNewerSamplesAndMissingRunIsNotEmpty() {
        var metrics = new SimpleMeterRegistry();
        var service = new DataQualityServiceImpl(jdbc, metrics);
        String trigger = "synthetic-pinned-run";
        try {
            long old = jdbc.queryForObject("INSERT INTO app.mrr_data_quality_run(status,triggered_by) VALUES ('SUCCESS',?) RETURNING id", Long.class, trigger);
            jdbc.update("INSERT INTO app.mrr_data_quality_issue(run_id,check_code,check_name,severity,entity_id) VALUES (?,'TEST','test','WARNING','old')", old);
            long newer = jdbc.queryForObject("INSERT INTO app.mrr_data_quality_run(status,triggered_by) VALUES ('RUNNING',?) RETURNING id", Long.class, trigger);
            assertThat(service.getSummary().get("latestRun")).isInstanceOfSatisfying(java.util.Map.class,
                    row -> assertThat(row.get("latest_successful_run_id")).isEqualTo(old));
            assertThat(service.getIssues(10, old)).extracting(row -> row.get("entity_id")).containsExactly("old");
            assertThat(service.getIssues(10, old)).extracting(row -> row.get("run_id")).containsExactly(old);
            assertThat(service.getIssues(10, newer)).isEmpty();
            jdbc.update("DELETE FROM app.mrr_data_quality_run WHERE id=?", old);
            assertThatThrownBy(() -> service.getIssues(10, old)).isInstanceOf(com.zjcxph.imgapi.exception.BusinessException.class);
        } finally {
            jdbc.update("DELETE FROM app.mrr_data_quality_run WHERE triggered_by=?", trigger);
            metrics.close();
        }
    }

    @Test
    void smallRunBudgetCancelsSlowCountAndAllowsNextRun() throws Exception {
        var metrics = new SimpleMeterRegistry();
        var service = new DataQualityServiceImpl(jdbc, metrics);
        ReflectionTestUtils.setField(service, "runBudgetSeconds", 1);
        ReflectionTestUtils.setField(service, "retentionDays", 90);
        String trigger = "synthetic-budget";
        try {
            ReflectionTestUtils.setField(service, "checks", List.of(check(
                    "BUDGET", "synthetic", "WARNING", "SELECT 1::bigint FROM pg_sleep(5)", "SELECT 1")));
            long start = System.nanoTime();
            assertThatThrownBy(() -> service.runChecks(trigger)).isInstanceOf(IllegalStateException.class)
                    .hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class);
            assertThat((System.nanoTime() - start) / 1_000_000_000.0).isLessThan(4);
            assertThat(jdbc.queryForObject("SELECT status FROM app.mrr_data_quality_run WHERE triggered_by=? ORDER BY id DESC LIMIT 1", String.class, trigger)).isEqualTo("FAILED");
            ReflectionTestUtils.setField(service, "runBudgetSeconds", 10);
            ReflectionTestUtils.setField(service, "checks", List.of());
            service.runChecks(trigger);
            assertThat(jdbc.queryForObject("SELECT status FROM app.mrr_data_quality_run WHERE triggered_by=? ORDER BY id DESC LIMIT 1", String.class, trigger)).isEqualTo("SUCCESS");
        } finally {
            jdbc.update("DELETE FROM app.mrr_data_quality_run WHERE triggered_by=?", trigger);
            metrics.close();
        }
    }

    @Test
    void ruleWriteLockIsCancelledWithinBudgetWithoutPublishingSamples() throws Exception {
        var metrics = new SimpleMeterRegistry();
        var service = new DataQualityServiceImpl(jdbc, metrics);
        ReflectionTestUtils.setField(service, "runBudgetSeconds", 1);
        ReflectionTestUtils.setField(service, "sampleLimit", 1);
        ReflectionTestUtils.setField(service, "retentionDays", 90);
        ReflectionTestUtils.setField(service, "checks", List.of(check("WRITE_BUDGET", "synthetic", "WARNING",
                "SELECT 1::bigint", "SELECT 'mr_scan' AS entity_type, '1' AS entity_id, NULL::text AS bah, NULL::text AS sjh, 'synthetic' AS detail")));
        String trigger = "synthetic-write-budget";
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        var locked = new java.util.concurrent.CountDownLatch(1);
        try {
            var holder = executor.submit(() -> {
                try (var connection = jdbc.getDataSource().getConnection()) {
                    connection.setAutoCommit(false);
                    try (var statement = connection.createStatement()) {
                        statement.execute("LOCK TABLE app.mrr_data_quality_issue IN ACCESS EXCLUSIVE MODE");
                        locked.countDown();
                        Thread.sleep(3000);
                    } finally {
                        connection.rollback();
                    }
                }
                return null;
            });
            assertThat(locked.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> service.runChecks(trigger)).isInstanceOf(IllegalStateException.class)
                    .hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class);
            holder.get(5, java.util.concurrent.TimeUnit.SECONDS);
            var failed = jdbc.queryForMap("SELECT id,status FROM app.mrr_data_quality_run WHERE triggered_by=? ORDER BY id DESC LIMIT 1", trigger);
            assertThat(failed.get("status")).isEqualTo("FAILED");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app.mrr_data_quality_issue WHERE run_id=?", Long.class, failed.get("id"))).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app.mrr_data_quality_check_result WHERE run_id=?", Long.class, failed.get("id"))).isZero();
            assertThat(metrics.get("mrr.data.quality.running").gauge().value()).isZero();
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
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
