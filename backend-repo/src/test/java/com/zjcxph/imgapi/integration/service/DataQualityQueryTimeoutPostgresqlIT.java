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

    @Test
    void managedQueueSurvivesServiceRecreationAndSerializesIndependentAdmission() throws Exception {
        String trigger = "synthetic-managed-queue";
        var metrics1 = new SimpleMeterRegistry();
        var metrics2 = new SimpleMeterRegistry();
        var a = managedService(metrics1);
        var b = managedService(metrics2);
        ReflectionTestUtils.setField(b, "checks", List.of(check("QUEUE_TEST", "synthetic", "WARNING",
                "SELECT 0::bigint", "SELECT 1")));
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var gate = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Long> submitA = () -> {
                gate.await();
                try { return runId(a.submitRun(trigger, 101L)); }
                catch (com.zjcxph.imgapi.exception.BusinessException e) {
                    assertThat(e.getCode()).isEqualTo(409); return 0L;
                }
            };
            java.util.concurrent.Callable<Long> submitB = () -> {
                gate.await();
                try { return runId(b.submitRun(trigger, 102L)); }
                catch (com.zjcxph.imgapi.exception.BusinessException e) {
                    assertThat(e.getCode()).isEqualTo(409); return 0L;
                }
            };
            var one = executor.submit(submitA);
            var two = executor.submit(submitB);
            gate.countDown();
            long first = one.get(10, java.util.concurrent.TimeUnit.SECONDS);
            long second = two.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(first == 0L ^ second == 0L).isTrue();
            long id = Math.max(first, second);
            assertThat(status(b.getRun(id))).isEqualTo("PENDING");
            assertThatThrownBy(() -> b.runChecks(trigger)).isInstanceOf(com.zjcxph.imgapi.exception.BusinessException.class);
            // Only the new service instance is used to process the persisted submission.
            b.processPending();
            assertThat(status(b.getRun(id))).isEqualTo("SUCCESS");
            assertThat(b.getRun(id).get("running")).isEqualTo(false);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app.mrr_data_quality_check_result WHERE run_id=?", Long.class, id)).isEqualTo(1L);
            long cancelled = runId(a.submitRun(trigger, 101L));
            assertThat(status(a.cancelRun(cancelled, 103L))).isEqualTo("CANCELLED");
            b.processPending();
            assertThat(status(a.cancelRun(cancelled, 104L))).isEqualTo("CANCELLED");
            assertThat(jdbc.queryForObject("SELECT cancelled_by FROM app.mrr_data_quality_run WHERE id=?", Long.class, cancelled)).isEqualTo(103L);
            long crashed = runId(a.submitRun(trigger, 101L));
            jdbc.update("UPDATE app.mrr_data_quality_run SET status='RUNNING',claimed_at=clock_timestamp(),deadline_at=clock_timestamp()-interval '1 second' WHERE id=?", crashed);
            b.processPending();
            assertThat(status(b.getRun(crashed))).isEqualTo("FAILED");
            assertThat(jdbc.queryForObject("SELECT error_message FROM app.mrr_data_quality_run WHERE id=?", String.class, crashed)).isEqualTo("RUN_DEADLINE_EXPIRED");
            long recovered = runId(b.submitRun(trigger, 102L));
            b.processPending();
            assertThat(status(b.getRun(recovered))).isEqualTo("SUCCESS");
        } finally {
            executor.shutdownNow();
            jdbc.update("DELETE FROM app.mrr_data_quality_run WHERE triggered_by=?", trigger);
            metrics1.close(); metrics2.close();
        }
    }

    @Test
    void cancelRunningCheckPreventsRulePublicationAndReleasesDurableSlot() throws Exception {
        String trigger = "synthetic-managed-cancel";
        var metrics = new SimpleMeterRegistry();
        var service = managedService(metrics);
        ReflectionTestUtils.setField(service, "checks", List.of(check("CANCEL_TEST", "synthetic", "WARNING",
                "SELECT 1::bigint FROM pg_sleep(2)", "SELECT 'mr_scan' AS entity_type,'1' AS entity_id,NULL::text AS bah,NULL::text AS sjh,'synthetic' AS detail")));
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            long id = runId(service.submitRun(trigger, 101L));
            var worker = executor.submit(service::processPending);
            long until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < until && !status(service.getRun(id)).equals("RUNNING")) Thread.sleep(10);
            assertThat(status(service.getRun(id))).isEqualTo("RUNNING");
            long cancelStarted = System.nanoTime();
            assertThat(status(service.cancelRun(id, 102L))).isIn("CANCEL_REQUESTED", "CANCELLED");
            worker.get(10, java.util.concurrent.TimeUnit.SECONDS);
            System.out.printf("MRR_CANCEL_LATENCY_MS %.3f%n",(System.nanoTime()-cancelStarted)/1e6);
            assertThat(status(service.getRun(id))).isEqualTo("CANCELLED");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app.mrr_data_quality_issue WHERE run_id=?", Long.class, id)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app.mrr_data_quality_check_result WHERE run_id=?", Long.class, id)).isZero();
            assertThat(runId(service.submitRun(trigger, 101L))).isGreaterThan(id);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
            jdbc.update("DELETE FROM app.mrr_data_quality_run WHERE triggered_by=?", trigger);
            metrics.close();
        }
    }

    @Test
    void historicalErrorTextIsNotPublishedInQualitySummaryOrRelationOverview() {
        var metrics = new SimpleMeterRegistry();
        var service = managedService(metrics);
        String trigger = "synthetic-historical-error";
        try {
            long id = jdbc.queryForObject("INSERT INTO app.mrr_data_quality_run(status,triggered_by,error_message) VALUES ('FAILED',?,'SYNTHETIC private patient and directory') RETURNING id", Long.class, trigger);
            assertThat(((java.util.Map<?, ?>)service.getRun(id).get("latestRun")).get("error_message"))
                    .isEqualTo("HISTORICAL_ERROR_REDACTED");
            var repository = new com.zjcxph.imgapi.repository.DataRelationRepository(jdbc);
            assertThat(repository.latestQualityRun().get("errorMessage")).isEqualTo("HISTORICAL_ERROR_REDACTED");
            assertThat(jdbc.queryForObject("SELECT error_message FROM app.mrr_data_quality_run WHERE id=?", String.class,id))
                    .isEqualTo("SYNTHETIC private patient and directory");
        } finally {
            jdbc.update("DELETE FROM app.mrr_data_quality_run WHERE triggered_by=?", trigger);
            metrics.close();
        }
    }

    @Test
    void killedJvmCannotPublishMoreRulesAndRestartRecoversDurableSlot() throws Exception {
        String trigger = "synthetic-jvm-crash";
        var metrics = new SimpleMeterRegistry();
        var service = managedService(metrics);
        ReflectionTestUtils.setField(service, "runBudgetSeconds", 16);
        Process victim = null, competitor = null, restarted = null;
        try {
            long id = runId(service.submitRun(trigger, 101L));
            victim = child("slow");
            long until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
            while (System.nanoTime() < until && jdbc.queryForObject(
                    "SELECT count(*) FROM app.mrr_data_quality_check_result WHERE run_id=?", Long.class,id) == 0)
                Thread.sleep(25);
            assertThat(status(service.getRun(id))).isEqualTo("RUNNING");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app.mrr_data_quality_check_result WHERE run_id=?",Long.class,id)).isEqualTo(1L);
            competitor = child("fast");
            assertThat(competitor.waitFor(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(competitor.exitValue()).isZero();
            assertThat(status(service.getRun(id))).isEqualTo("RUNNING");
            victim.destroyForcibly();
            assertThat(victim.waitFor(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(victim.exitValue()).isNotZero();
            assertThat(status(service.getRun(id))).isEqualTo("RUNNING");
            // Wait for the real persisted database deadline; no synthetic status or time rewrite.
            until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
            while (System.nanoTime() < until && Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT deadline_at>clock_timestamp() FROM app.mrr_data_quality_run WHERE id=?",Boolean.class,id)))
                Thread.sleep(50);
            restarted = child("fast");
            assertThat(restarted.waitFor(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(restarted.exitValue()).isZero();
            assertThat(status(service.getRun(id))).isEqualTo("FAILED");
            assertThat(jdbc.queryForObject("SELECT error_message FROM app.mrr_data_quality_run WHERE id=?",String.class,id))
                    .isEqualTo("RUN_DEADLINE_EXPIRED");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app.mrr_data_quality_check_result WHERE run_id=?",Long.class,id)).isEqualTo(1L);
            long next = runId(service.submitRun(trigger, 102L));
            restarted = child("fast");
            assertThat(restarted.waitFor(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(restarted.exitValue()).isZero();
            assertThat(status(service.getRun(next))).as("Recovered run: %s",service.getRun(next)).isEqualTo("SUCCESS");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app.mrr_data_quality_check_result WHERE run_id=?",Long.class,next)).isEqualTo(2L);
            System.out.println("MRR_CRASH_RECOVERY child JVM killed; competing JVM excluded; partial rule retained; deadline recovery; new JVM succeeds");
        } finally {
            for (Process process : new Process[]{victim,competitor,restarted}) {
                if (process != null && process.isAlive()) { process.destroyForcibly(); process.waitFor(5,java.util.concurrent.TimeUnit.SECONDS); }
            }
            jdbc.update("DELETE FROM app.mrr_data_quality_run WHERE triggered_by=?",trigger);
            metrics.close();
        }
    }

    private Process child(String mode) throws java.io.IOException {
        String cp = System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
        var builder = new ProcessBuilder(java.nio.file.Path.of(System.getProperty("java.home"),"bin","java").toString(),
                "-Xmx128m","-cp",cp,QualityCrashWorker.class.getName(),mode);
        builder.environment().put("MRR_CRASH_JDBC",postgresqlJdbcUrl());
        builder.environment().put("MRR_CRASH_USER",postgresqlUsername());
        builder.environment().put("MRR_CRASH_PASSWORD",postgresqlPassword());
        java.nio.file.Path output=java.nio.file.Files.createTempFile("mrr-quality-crash-worker-",".log");
        System.out.println("MRR_CRASH_CHILD_LOG "+output);
        return builder.redirectErrorStream(true).redirectOutput(output.toFile()).start();
    }

    @Test
    void expiredAdmissionAfterPoolWaitDoesNotCreateRun() throws Exception {
        String trigger="synthetic-pool-budget";
        var config=new com.zaxxer.hikari.HikariConfig();
        config.setJdbcUrl(postgresqlJdbcUrl());
        config.setUsername(postgresqlUsername());
        config.setPassword(postgresqlPassword());
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(3000);
        var metrics=new SimpleMeterRegistry();
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
        try(var pool=new com.zaxxer.hikari.HikariDataSource(config)) {
            var service=new DataQualityServiceImpl(new JdbcTemplate(pool),metrics);
            ReflectionTestUtils.setField(service,"runBudgetSeconds",1);
            java.util.concurrent.Future<?> result;
            long start=System.nanoTime();
            try(var held=pool.getConnection()) {
                result=executor.submit(()->assertThatThrownBy(()->service.submitRun(trigger,101L))
                        .isInstanceOf(org.springframework.dao.QueryTimeoutException.class));
                // Require timeout while the only connection is STILL held.
                // The old implementation waits for the pool's independent timeout.
                result.get(1400,java.util.concurrent.TimeUnit.MILLISECONDS);
                assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isEqualTo(1);
            }
            result.get(5,java.util.concurrent.TimeUnit.SECONDS);
            System.out.printf("MRR_POOL_BUDGET rejection_ms=%.3f%n",(System.nanoTime()-start)/1e6);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app.mrr_data_quality_run WHERE triggered_by=?",Long.class,trigger)).isZero();
            assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
        } finally {executor.shutdownNow();metrics.close();}
    }

    private DataQualityServiceImpl managedService(SimpleMeterRegistry metrics) {
        var service = new DataQualityServiceImpl(jdbc, metrics);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "retentionDays", 90);
        ReflectionTestUtils.setField(service, "runBudgetSeconds", 30);
        return service;
    }

    private static long runId(java.util.Map<String, Object> summary) {
        return ((Number)((java.util.Map<?, ?>)summary.get("latestRun")).get("id")).longValue();
    }

    private static String status(java.util.Map<String, Object> summary) {
        return String.valueOf(((java.util.Map<?, ?>)summary.get("latestRun")).get("status"));
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
