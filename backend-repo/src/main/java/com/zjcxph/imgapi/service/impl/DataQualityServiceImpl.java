package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.service.DataQualityService;
import com.zjcxph.imgapi.service.DeploymentReadinessService;
import com.zjcxph.imgapi.service.MaintenanceModeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.PreparedStatementSetter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class DataQualityServiceImpl implements DataQualityService {

    private static final Logger logger = LoggerFactory.getLogger(DataQualityServiceImpl.class);
    private static final Set<String> LINKABLE_ENTITY_TYPES = Set.of(
            "mr_scan",
            "mr_statistics",
            "mr_archive_box_record"
    );

    private final JdbcTemplate jdbcTemplate;
    private final DeploymentReadinessService readiness;
    private final MaintenanceModeService maintenance;
    private static final String RUN_FIELDS = """
            id, status, triggered_by, check_count, total_issues, critical_count,
            warning_count, started_at, completed_at, %s AS error_message,
            task_managed, deadline_at, claimed_at, requested_by, cancelled_by,
            (SELECT id FROM app.mrr_data_quality_run WHERE status = 'SUCCESS'
             ORDER BY id DESC LIMIT 1) AS latest_successful_run_id
            """.formatted(com.zjcxph.imgapi.repository.DataRelationRepository.SAFE_QUALITY_RUN_ERROR);
    private final MultiGauge issuesGauge;
    private final AtomicLong totalIssuesGauge = new AtomicLong();
    private final AtomicLong lastSuccessTimestampGauge = new AtomicLong();
    private final AtomicLong runningGauge = new AtomicLong();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final List<DataQualityCheckDefinition> checks = DataQualityCheckCatalog.standardChecks();

    @Value("${app.data-quality.enabled:true}")
    private boolean enabled;

    // enabled controls the automatic cron only; existing manual operation remains available.
    @Value("${app.data-quality.async-enabled:true}")
    private boolean asyncEnabled = true;

    @Value("${app.data-quality.sample-limit:200}")
    private int sampleLimit;

    @Value("${app.data-quality.retention-days:90}")
    private int retentionDays;

    @Value("${app.data-quality.run-budget-seconds:180}")
    private int runBudgetSeconds = 180;

    public DataQualityServiceImpl(JdbcTemplate jdbcTemplate, MeterRegistry meterRegistry) {
        this(jdbcTemplate, meterRegistry, null, null);
    }

    @Autowired
    public DataQualityServiceImpl(JdbcTemplate jdbcTemplate, MeterRegistry meterRegistry,
                                  DeploymentReadinessService readiness, MaintenanceModeService maintenance) {
        this.jdbcTemplate = jdbcTemplate;
        this.readiness = readiness;
        this.maintenance = maintenance;
        this.issuesGauge = MultiGauge.builder("mrr.data.quality.issues")
                .description("MRR data quality issue count grouped by check and severity")
                .register(meterRegistry);
        Gauge.builder("mrr.data.quality.total.issues", totalIssuesGauge, AtomicLong::get)
                .description("Total issues from the latest completed data quality run")
                .register(meterRegistry);
        Gauge.builder("mrr.data.quality.last.success.timestamp", lastSuccessTimestampGauge, AtomicLong::get)
                .description("Epoch seconds of the latest successful data quality run")
                .register(meterRegistry);
        Gauge.builder("mrr.data.quality.running", runningGauge, AtomicLong::get)
                .description("Whether a data quality run is currently executing")
                .register(meterRegistry);
    }

    @PostConstruct
    public void initializeMetrics() {
        try {
            refreshMetricsFromLatestRun(deadlineFromNow(3));
        } catch (Exception exception) {
            logger.warn("Unable to initialize data quality metrics: {}", exception.getMessage());
        }
    }

    @Scheduled(cron = "${app.data-quality.cron:0 15 2 * * ?}")
    public void scheduledRun() {
        if (!enabled || !asyncEnabled) {
            return;
        }
        try {
            submitRun("scheduled", null);
        } catch (Exception exception) {
            logger.error("Scheduled data quality run failed", exception);
        }
    }

    @Override
    public Map<String, Object> getSummary() {
        return summary(jdbcTemplate.queryForList("SELECT " + RUN_FIELDS
                + " FROM app.mrr_data_quality_run ORDER BY id DESC LIMIT 1"));
    }

    @Override
    public Map<String, Object> getRun(long runId) {
        if (runId <= 0) throw new BusinessException(400, "检查批次编号无效");
        var runs = jdbcTemplate.queryForList("SELECT " + RUN_FIELDS
                + " FROM app.mrr_data_quality_run WHERE id=?", runId);
        if (runs.isEmpty()) throw new BusinessException(404, "检查批次不存在或已清理");
        return summary(runs);
    }

    private Map<String, Object> summary(List<Map<String, Object>> runs) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("running", running.get());
        result.put("enabled", enabled);
        result.put("asyncEnabled", asyncEnabled);
        if (runs.isEmpty()) {
            result.put("latestRun", null);
            result.put("checks", List.of());
            return result;
        }
        Map<String, Object> latestRun = runs.getFirst();
        if (Boolean.TRUE.equals(latestRun.get("task_managed"))) {
            result.put("running", Set.of("PENDING", "RUNNING", "CANCEL_REQUESTED").contains(String.valueOf(latestRun.get("status"))));
        }
        result.put("latestRun", latestRun);
        result.put("checks", jdbcTemplate.queryForList("""
                SELECT check_code, check_name, severity, issue_count, sampled_count, checked_at
                FROM app.mrr_data_quality_check_result
                WHERE run_id = ?
                ORDER BY CASE severity WHEN 'CRITICAL' THEN 1 ELSE 2 END,
                         issue_count DESC, check_code
                """, latestRun.get("id")));
        return result;
    }

    @Override
    public List<Map<String, Object>> getIssues(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        // Select the run and its samples in one database statement/snapshot.
        return jdbcTemplate.queryForList("""
                SELECT id, run_id, check_code, check_name, severity, entity_type, entity_id,
                       bah, sjh, detail, detected_at
                FROM app.mrr_data_quality_issue
                WHERE run_id = (SELECT id FROM app.mrr_data_quality_run ORDER BY id DESC LIMIT 1)
                ORDER BY CASE severity WHEN 'CRITICAL' THEN 1 ELSE 2 END, id DESC
                LIMIT ?
                """, safeLimit);
    }

    @Override
    public List<Map<String, Object>> getIssues(int limit, long runId) {
        if (runId <= 0) throw new BusinessException(400, "检查批次编号无效");
        // One snapshot distinguishes an existing empty run from a missing/expired run.
        var rows = jdbcTemplate.queryForList("""
                SELECT i.id, r.id AS run_id, i.check_code, i.check_name, i.severity,
                       i.entity_type, i.entity_id, i.bah, i.sjh, i.detail, i.detected_at
                FROM app.mrr_data_quality_run r
                LEFT JOIN LATERAL (
                    SELECT * FROM app.mrr_data_quality_issue
                    WHERE run_id = r.id
                    ORDER BY CASE severity WHEN 'CRITICAL' THEN 1 ELSE 2 END, id DESC
                    LIMIT ?
                ) i ON true
                WHERE r.id = ?
                ORDER BY CASE i.severity WHEN 'CRITICAL' THEN 1 ELSE 2 END, i.id DESC
                """, Math.max(1, Math.min(limit, 500)), runId);
        if (rows.isEmpty()) throw new BusinessException(404, "检查批次不存在或已清理");
        return rows.getFirst().get("id") == null ? List.of() : rows;
    }

    @Override
    public Map<String, Object> getIssue(long issueId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT i.id, i.run_id AS "runId", i.check_code AS "checkCode",
                       i.check_name AS "checkName", i.severity,
                       i.entity_type AS "entityType", i.entity_id AS "entityId",
                       i.bah, i.sjh, i.detail, i.detected_at AS "detectedAt",
                       r.status AS "runStatus", r.triggered_by AS "triggeredBy",
                       r.started_at AS "runStartedAt", r.completed_at AS "runCompletedAt"
                FROM app.mrr_data_quality_issue i
                JOIN app.mrr_data_quality_run r ON r.id = i.run_id
                WHERE i.id = ?
                """, issueId);
        if (rows.isEmpty()) {
            throw new BusinessException(404, "数据质量异常不存在");
        }
        return rows.getFirst();
    }

    @Override
    public Map<String, Object> previewRepair(long issueId) {
        Map<String, Object> issue = getIssue(issueId);
        String checkCode = normalize(issue.get("checkCode"));
        String entityType = normalize(issue.get("entityType"));
        String entityId = normalize(issue.get("entityId"));
        String bah = normalize(issue.get("bah"));
        String sjh = normalize(issue.get("sjh"));

        Map<String, Object> currentEntity = loadCurrentEntity(entityType, entityId);
        String suggestedAction = suggestedAction(checkCode);
        // Historic samples are not current authority. Do not propose a link from stale codes,
        // inactive rows or a high BAH without a valid SJH.
        String currentBah = normalize(currentEntity.get("bah"));
        String currentSjh = normalize(currentEntity.get("sjh"));
        boolean current = entityType != null && LINKABLE_ENTITY_TYPES.contains(entityType)
                && currentEntity.get("entityId") != null
                && java.util.Objects.equals(bah, currentBah)
                && java.util.Objects.equals(sjh, currentSjh)
                && (!"mr_scan".equals(entityType) || currentEntity.get("uploadFlag") instanceof Number flag && flag.intValue() != 0)
                && (!"mr_archive_box_record".equals(entityType) || "NORMAL".equals(currentEntity.get("status")));
        boolean validCodes = currentBah != null && currentBah.matches("[0-9]+")
                && (currentSjh == null || currentSjh.matches("[0-9]+"))
                && (!com.zjcxph.imgapi.utils.MedicalRecordCodeUtils.requiresSjhForBah(currentBah)
                    || currentSjh != null && currentSjh.length() >= 4);
        List<Map<String, Object>> candidates = current && validCodes
                ? findArchiveCandidates(currentBah, currentSjh) : List.of();
        boolean deterministic = "LINK_ARCHIVE_ID".equals(suggestedAction)
                && current && validCodes && candidates.size() == 1
                && currentEntity.get("archiveId") == null
                && com.zjcxph.imgapi.utils.MedicalRecordCodeUtils.toSearchTerm(currentBah).equals(
                    com.zjcxph.imgapi.utils.MedicalRecordCodeUtils.toSearchTerm(normalize(candidates.getFirst().get("bah"))));

        boolean supportedRepair = ("mr_scan".equals(entityType) && "SCAN_ARCHIVE_LINK_MISSING_ESTIMATED".equals(checkCode))
                || ("mr_archive_box_record".equals(entityType) && "BOX_ARCHIVE_LINK_MISSING".equals(checkCode));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("issue", issue);
        result.put("currentEntity", currentEntity);
        result.put("candidateArchives", candidates);
        result.put("suggestedAction", suggestedAction);
        result.put("deterministic", deterministic);
        result.put("readOnly", true);
        result.put("canApply", false);
        result.put("reason", !current ? "历史异常与当前记录不一致或记录已失效，请重新检查" : !validCodes ? "当前编号不满足安全关联规则" : deterministic
                ? (supportedRepair ? "存在唯一候选主档；预览不授权写入，实际关联须通过受控修复工单" : "存在唯一候选主档，但该异常类型尚未开放修复工单，仅供人工核对")
                : "候选关系不唯一或异常类型需要人工判断，禁止自动修改");
        return result;
    }

    private boolean canExecute() {
        return (readiness == null || !readiness.isReadOnly()) &&
                (maintenance == null || !maintenance.isEnabled());
    }

    private int budgetSeconds() {
        return Math.max(1, Math.min(runBudgetSeconds, 600));
    }

    private long deadlineFromNow(int seconds) {
        return System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(seconds);
    }

    // The durable partial unique index serializes admission across independent JVMs/connections.
    // Historical unmanaged RUNNING rows are not treated as live jobs.
    private long admit(String status, String trigger, Long actor, long deadline) {
        try {
            var ids = jdbcTemplate.query("""
                    INSERT INTO app.mrr_data_quality_run
                    (status, triggered_by, task_managed, deadline_at, claimed_at, requested_by)
                    VALUES (?, ?, true, clock_timestamp() + make_interval(secs => ?),
                        CASE WHEN ? = 'RUNNING' THEN clock_timestamp() ELSE NULL END, ?)
                    RETURNING id
                    """, statement -> {
                statement.setQueryTimeout(remainingQuerySeconds(deadline));
                statement.setString(1, status);
                statement.setString(2, normalizeTrigger(trigger));
                statement.setDouble(3, remainingBudgetSeconds(deadline));
                statement.setString(4, status);
                statement.setObject(5, actor, java.sql.Types.BIGINT);
            }, (rs, n) -> rs.getLong(1));
            return ids.getFirst();
        } catch (DuplicateKeyException conflict) {
            throw new BusinessException(409, "已有活动数据质量检查");
        }
    }

    private double remainingBudgetSeconds(long deadline) {
        long nanos = deadline - System.nanoTime();
        if (nanos <= 0) throw new org.springframework.dao.QueryTimeoutException("数据质量检查预算已耗尽");
        return Math.min(600.0, nanos / 1_000_000_000.0);
    }

    private void recoverExpired(long deadline) {
        budgetedUpdate(Math.min(deadline, deadlineFromNow(3)),"""
                UPDATE app.mrr_data_quality_run
                SET status = CASE WHEN status='CANCEL_REQUESTED' THEN 'CANCELLED' ELSE 'FAILED' END,
                    completed_at=CURRENT_TIMESTAMP,
                    error_message=CASE WHEN status='CANCEL_REQUESTED' THEN NULL ELSE 'RUN_DEADLINE_EXPIRED' END
                WHERE task_managed AND status IN ('PENDING','RUNNING','CANCEL_REQUESTED')
                    AND deadline_at <= clock_timestamp()
                """);
    }

    @Override
    public Map<String, Object> submitRun(String triggeredBy, Long actorId) {
        if (!asyncEnabled || !canExecute()) throw new BusinessException(503, "数据质量检查已暂停");
        if (actorId != null && actorId <= 0) throw new BusinessException(400, "无效操作者");
        long deadline = deadlineFromNow(budgetSeconds());
        recoverExpired(deadline);
        long id = admit("PENDING", triggeredBy, actorId, deadline);
        return getRun(id);
    }

    @Override
    public Map<String, Object> cancelRun(long runId, long actorId) {
        if (runId <= 0 || actorId <= 0) throw new BusinessException(400, "无效检查批次或操作者");
        int changed = controlUpdate("""
                UPDATE app.mrr_data_quality_run
                SET status=CASE WHEN status='PENDING' THEN 'CANCELLED' ELSE 'CANCEL_REQUESTED' END,
                    completed_at=CASE WHEN status='PENDING' THEN CURRENT_TIMESTAMP ELSE NULL END,
                    cancelled_by=?
                WHERE id=? AND task_managed AND status IN ('PENDING','RUNNING')
                """, actorId, runId);
        var state = getRun(runId);
        var row = (Map<?, ?>) state.get("latestRun");
        if (changed == 0 && !Set.of("CANCEL_REQUESTED","CANCELLED").contains(String.valueOf(row.get("status")))) {
            throw new BusinessException(409, "检查已结束或不能取消");
        }
        return state;
    }

    /** One worker in the existing bounded scheduler; pending jobs survive process restarts. */
    @Scheduled(fixedDelay = 2000, initialDelay = 10000)
    public void processPending() {
        if (!asyncEnabled || !canExecute() || !running.compareAndSet(false, true)) return;
        long started = System.nanoTime();
        try {
            recoverExpired(deadlineFromNow(3));
            var claimed = jdbcTemplate.query("""
                    UPDATE app.mrr_data_quality_run
                    SET status='RUNNING', claimed_at=clock_timestamp()
                    WHERE id=(SELECT id FROM app.mrr_data_quality_run
                        WHERE task_managed AND status='PENDING' AND deadline_at>clock_timestamp()
                        ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED)
                        AND status='PENDING' AND deadline_at>clock_timestamp()
                    RETURNING id, GREATEST(0, EXTRACT(EPOCH FROM deadline_at-clock_timestamp())) AS remaining
                    """, statement -> statement.setQueryTimeout(3),
                    (rs, n) -> new long[]{rs.getLong("id"), (long)(rs.getDouble("remaining") * 1_000_000_000L)});
            if (claimed.isEmpty()) return;
            long[] lease = claimed.getFirst();
            runningGauge.set(1);
            executeRun(lease[0], started + lease[1]);
        } catch (Exception failure) {
            logger.warn("Data quality worker failed: {}", failure.getClass().getSimpleName());
        } finally {
            runningGauge.set(0);
            running.set(false);
        }
    }

    @Override
    public Map<String, Object> runChecks(String triggeredBy) {
        if (!canExecute()) throw new BusinessException(503, "数据质量检查已暂停");
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("数据质量检查正在运行，请稍后重试");
        }
        runningGauge.set(1);
        try {
            long deadline = deadlineFromNow(budgetSeconds());
            recoverExpired(deadline);
            var session = com.zjcxph.imgapi.utils.AuthContext.getCurrentUser();
            long id = admit("RUNNING", triggeredBy, session == null ? null : session.getId(), deadline);
            executeRun(id, deadline);
            return getRun(id);
        } finally {
            runningGauge.set(0);
            running.set(false);
        }
    }

    private void requireActive(long runId, long deadline) {
        if (!canExecute()) throw new IllegalStateException("数据质量检查因只读模式暂停");
        var rows = jdbcTemplate.query("""
                SELECT id FROM app.mrr_data_quality_run
                WHERE id=? AND status='RUNNING' AND task_managed AND deadline_at>clock_timestamp()
                """, statement -> {
            statement.setQueryTimeout(remainingQuerySeconds(deadline));
            statement.setLong(1, runId);
        }, (rs, n) -> rs.getLong(1));
        if (rows.isEmpty()) throw new IllegalStateException("检查运行状态已变化或已取消");
    }

    private void executeRun(long runId, long deadline) {
        boolean succeeded = false;
        try {
            long totalIssues = 0;
            long criticalCount = 0;
            long warningCount = 0;
            int safeSampleLimit = Math.max(1, Math.min(sampleLimit, 1000));

            for (DataQualityCheckDefinition check : checks) {
                requireActive(runId, deadline);
                Long countValue = jdbcTemplate.query(check.countSql(), statement -> statement.setQueryTimeout(remainingQuerySeconds(deadline)),
                        resultSet -> { if (!resultSet.next()) return null; long value = resultSet.getLong(1); return resultSet.wasNull() ? null : value; });
                if (countValue == null) throw new IllegalStateException("统计信息不可用: " + check.code());
                long issueCount = countValue;
                requireActive(runId, deadline);
                List<Map<String, Object>> samples = issueCount == 0
                        ? List.of()
                        : jdbcTemplate.query(check.sampleSql() + " LIMIT ?", statement -> {
                            statement.setQueryTimeout(remainingQuerySeconds(deadline));
                            statement.setInt(1, safeSampleLimit);
                        }, new ColumnMapRowMapper());
                if (!canExecute()) throw new IllegalStateException("数据质量检查因只读模式暂停");
                saveRuleResult(runId, check, issueCount, samples, deadline);
                totalIssues += issueCount;
                if ("CRITICAL".equals(check.severity())) criticalCount += issueCount;
                else warningCount += issueCount;
            }

            if (!canExecute()) throw new IllegalStateException("数据质量检查因只读模式暂停");
            int finalized = budgetedUpdate(deadline, """
                    UPDATE app.mrr_data_quality_run
                    SET status = 'SUCCESS', check_count = ?, total_issues = ?, critical_count = ?,
                        warning_count = ?, completed_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND status = 'RUNNING' AND task_managed AND deadline_at>clock_timestamp()
                    """, checks.size(), totalIssues, criticalCount, warningCount, runId);
            if (finalized != 1) throw new IllegalStateException("检查运行状态已变化");
            succeeded = true;
            try {
                cleanupExpiredRuns(deadline);
            } catch (Exception exception) {
                logger.warn("Data quality retention cleanup failed: {}", exception.getClass().getSimpleName());
            }
            try {
                refreshMetricsFromLatestRun(deadline);
            } catch (Exception exception) {
                logger.warn("Data quality metric refresh failed: {}", exception.getClass().getSimpleName());
            }
            logger.info("Data quality run {} completed: total={}, critical={}, warning={}", runId, totalIssues, criticalCount, warningCount);
        } catch (Exception exception) {
            if (!succeeded) {
                try {
                    recordFailedRun(runId, exception);
                } catch (Exception recordingFailure) {
                    exception.addSuppressed(recordingFailure);
                }
            }
            throw new IllegalStateException("数据质量检查失败，请检查追踪编号或后台安全堆栈", exception);
        }
    }

    private int remainingQuerySeconds(long deadline) {
        long nanos = deadline - System.nanoTime();
        if (nanos <= 0 || Thread.currentThread().isInterrupted()) {
            throw new org.springframework.dao.QueryTimeoutException("数据质量检查执行预算已耗尽或中断");
        }
        return (int) Math.min(10, (nanos + 999_999_999L) / 1_000_000_000L);
    }

    /**
     * All samples and the corresponding count for a rule are published atomically.
     * The SQL shape is generated only from the already bounded sample count; every value is bound.
     */
    private static final String ACTIVE_GUARD = """
            WITH active AS MATERIALIZED (
                SELECT id FROM app.mrr_data_quality_run
                WHERE id=? AND status='RUNNING' AND task_managed AND deadline_at>clock_timestamp()
                FOR UPDATE
            )
            """;

    private void saveRuleResult(long runId, DataQualityCheckDefinition check,
                                long issueCount, List<Map<String, Object>> samples, long deadline) {
        int changed;
        if (samples.isEmpty()) {
            changed = budgetedUpdate(deadline, ACTIVE_GUARD + """
                    INSERT INTO app.mrr_data_quality_check_result
                        (run_id, check_code, check_name, severity, issue_count, sampled_count)
                    SELECT id, ?, ?, ?, ?, 0 FROM active
                    """, runId, check.code(), check.name(), check.severity(), issueCount);
        } else {
            if (samples.size() > 1000) throw new IllegalStateException("异常样本超过持久化上限");
            String placeholders = String.join(",", java.util.Collections.nCopies(samples.size(),
                    "(?::text, ?::text, ?::text, ?::text, ?::text, ?::text, ?::text, ?::text)"));
            List<Object> parameters = new ArrayList<>(samples.size() * 8 + 5);
            parameters.add(runId);
            for (Map<String, Object> sample : samples) {
                java.util.Collections.addAll(parameters, check.code(), check.name(), check.severity(),
                        sample.get("entity_type"), sample.get("entity_id"), sample.get("bah"),
                        sample.get("sjh"), sample.get("detail"));
            }
            java.util.Collections.addAll(parameters, check.code(), check.name(), check.severity(), issueCount);
            changed = budgetedUpdate(deadline, ACTIVE_GUARD + """
                    , inserted AS (
                        INSERT INTO app.mrr_data_quality_issue
                            (run_id, check_code, check_name, severity, entity_type, entity_id, bah, sjh, detail)
                        SELECT active.id, v.* FROM (VALUES %s)
                        AS v(code,name,severity,entity_type,entity_id,bah,sjh,detail) CROSS JOIN active
                        RETURNING 1
                    )
                    INSERT INTO app.mrr_data_quality_check_result
                        (run_id, check_code, check_name, severity, issue_count, sampled_count)
                    SELECT active.id, ?, ?, ?, ?, (SELECT count(*)::integer FROM inserted) FROM active
                    """.formatted(placeholders), parameters.toArray());
        }
        if (changed != 1) throw new IllegalStateException("检查运行状态已变化或已取消");
    }

    private int budgetedUpdate(long deadline, String sql, Object... args) {
        return jdbcTemplate.update(sql, (PreparedStatementSetter) statement -> {
            statement.setQueryTimeout(remainingQuerySeconds(deadline));
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
        });
    }

    private int controlUpdate(String sql, Object... args) {
        return budgetedUpdate(deadlineFromNow(3), sql, args);
    }

    private List<Map<String, Object>> findArchiveCandidates(String bah, String sjh) {
        if (bah == null && sjh == null) {
            return List.of();
        }
        return jdbcTemplate.queryForList("""
                WITH input AS (
                    SELECT NULLIF(BTRIM(CAST(? AS text)), '') AS bah,
                           NULLIF(BTRIM(CAST(? AS text)), '') AS sjh
                )
                SELECT a.id, a.bah, a.sjh, a.patient_name AS "patientName",
                       a.inpatient_department AS "department", a.page_count AS "pageCount",
                       CASE
                           WHEN i.sjh IS NOT NULL AND BTRIM(COALESCE(a.sjh, '')) = i.sjh
                               THEN 'SJH_EXACT'
                           WHEN i.bah IS NOT NULL AND BTRIM(COALESCE(a.bah, '')) = i.bah
                               THEN 'BAH_EXACT'
                           ELSE 'FORMAT_ONLY'
                       END AS "matchType"
                FROM app.mr_archive a
                CROSS JOIN input i
                WHERE (i.sjh IS NOT NULL
                       AND app.numeric_code_key(a.sjh) = app.numeric_code_key(i.sjh))
                   OR (i.sjh IS NULL AND i.bah IS NOT NULL
                       AND app.numeric_code_key(a.bah) = app.numeric_code_key(i.bah))
                ORDER BY CASE
                           WHEN i.sjh IS NOT NULL AND BTRIM(COALESCE(a.sjh, '')) = i.sjh THEN 0
                           WHEN i.bah IS NOT NULL AND BTRIM(COALESCE(a.bah, '')) = i.bah THEN 1
                           ELSE 2
                         END,
                         a.id DESC
                LIMIT 5
                """, bah, sjh);
    }

    private Map<String, Object> loadCurrentEntity(String entityType, String entityId) {
        if (entityType == null || entityId == null) {
            return Map.of();
        }
        if (!LINKABLE_ENTITY_TYPES.contains(entityType)) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("entityType", entityType);
            result.put("entityId", entityId);
            result.put("archiveId", null);
            result.put("readOnly", true);
            return result;
        }

        Long id = parseEntityId(entityId);
        if (id == null) {
            return Map.of();
        }
        String sql = switch (entityType) {
            case "mr_scan" -> """
                    SELECT 'mr_scan' AS "entityType", id AS "entityId",
                           archive_id AS "archiveId", bah, sjh, filename, pages,
                           uploadflag AS "uploadFlag"
                    FROM app.mr_scan WHERE id = ?
                    """;
            case "mr_statistics" -> """
                    SELECT 'mr_statistics' AS "entityType", id AS "entityId",
                           archive_id AS "archiveId", bah, sjh,
                           patientname AS "patientName", pages
                    FROM app.mr_statistics WHERE id = ?
                    """;
            case "mr_archive_box_record" -> """
                    SELECT 'mr_archive_box_record' AS "entityType", id AS "entityId",
                           archive_id AS "archiveId", bah, sjh,
                           box_no AS "boxNo", status
                    FROM app.mr_archive_box_record WHERE id = ?
                    """;
            default -> throw new IllegalStateException("unsupported entity type");
        };
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, id);
        return rows.isEmpty() ? Map.of() : rows.getFirst();
    }

    private String suggestedAction(String checkCode) {
        String normalized = checkCode == null ? "" : checkCode.toUpperCase(Locale.ROOT);
        if (normalized.contains("LINK_MISSING")) {
            return "LINK_ARCHIVE_ID";
        }
        if (normalized.contains("LINK_MISMATCH")) {
            return "REVIEW_ARCHIVE_LINK";
        }
        if (normalized.contains("DUPLICATE")) {
            return "REVIEW_DUPLICATE";
        }
        if (normalized.contains("AMBIGUOUS")) {
            return "MANUAL_DISAMBIGUATION";
        }
        return "MANUAL_REVIEW";
    }

    private void cleanupExpiredRuns(long deadline) {
        int safeRetentionDays = Math.max(7, retentionDays);
        budgetedUpdate(Math.min(deadline,deadlineFromNow(3)), """
                DELETE FROM app.mrr_data_quality_run WHERE id IN (
                    SELECT id FROM app.mrr_data_quality_run
                    WHERE started_at < CURRENT_TIMESTAMP - make_interval(days => ?)
                        AND (NOT task_managed OR status NOT IN ('PENDING','RUNNING','CANCEL_REQUESTED'))
                    ORDER BY started_at LIMIT 100
                )
                """, safeRetentionDays);
    }

    private void refreshMetricsFromLatestRun(long deadline) {
        var runs = jdbcTemplate.query(
                "SELECT id, EXTRACT(EPOCH FROM completed_at)::bigint AS completed_epoch FROM app.mrr_data_quality_run WHERE status = 'SUCCESS' ORDER BY id DESC LIMIT 1",
                statement -> statement.setQueryTimeout(remainingQuerySeconds(Math.min(deadline, deadlineFromNow(3)))),
                (rs, n) -> new long[]{rs.getLong("id"), rs.getLong("completed_epoch")}
        );
        if (!runs.isEmpty()) {
            refreshMetrics(runs.getFirst()[0], runs.getFirst()[1], deadline);
        }
    }

    private void refreshMetrics(long runId, long completedEpoch, long deadline) {
        List<Map<String, Object>> rows = jdbcTemplate.query("""
                SELECT check_code, severity, issue_count
                FROM app.mrr_data_quality_check_result
                WHERE run_id = ?
                """, statement -> {
                    statement.setQueryTimeout(remainingQuerySeconds(Math.min(deadline,deadlineFromNow(3))));
                    statement.setLong(1,runId);
                },new ColumnMapRowMapper());
        List<MultiGauge.Row<?>> gaugeRows = new ArrayList<>();
        long total = 0;
        for (Map<String, Object> row : rows) {
            long count = ((Number) row.get("issue_count")).longValue();
            total += count;
            gaugeRows.add(MultiGauge.Row.of(
                    Tags.of(
                            "check_code", String.valueOf(row.get("check_code")),
                            "severity", String.valueOf(row.get("severity"))
                    ),
                    count
            ));
        }
        issuesGauge.register(gaugeRows, true);
        totalIssuesGauge.set(total);
        lastSuccessTimestampGauge.set(completedEpoch);
    }

    private void recordFailedRun(long runId, Exception exception) {
        controlUpdate("""
                UPDATE app.mrr_data_quality_run
                SET status = CASE WHEN status='CANCEL_REQUESTED' THEN 'CANCELLED' ELSE 'FAILED' END,
                    completed_at = CURRENT_TIMESTAMP,
                    error_message = CASE WHEN status='CANCEL_REQUESTED' THEN NULL ELSE ? END
                WHERE id = ? AND status IN ('RUNNING','CANCEL_REQUESTED') AND task_managed
                """, exception.getClass().getSimpleName(), runId);
    }

    private String normalizeTrigger(String triggeredBy) {
        if (triggeredBy == null || triggeredBy.isBlank()) {
            return "manual";
        }
        return triggeredBy.substring(0, Math.min(32, triggeredBy.length()));
    }

    private Long parseEntityId(String entityId) {
        try {
            return Long.parseLong(entityId);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String normalize(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) {
            return "unknown error";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
