package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.service.DataQualityService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class DataQualityServiceImpl implements DataQualityService {

    private static final Logger logger = LoggerFactory.getLogger(DataQualityServiceImpl.class);

    private final JdbcTemplate jdbcTemplate;
    private final MultiGauge issuesGauge;
    private final AtomicLong totalIssuesGauge = new AtomicLong();
    private final AtomicLong lastSuccessTimestampGauge = new AtomicLong();
    private final AtomicLong runningGauge = new AtomicLong();
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${app.data-quality.enabled:true}")
    private boolean enabled;

    @Value("${app.data-quality.sample-limit:200}")
    private int sampleLimit;

    @Value("${app.data-quality.retention-days:90}")
    private int retentionDays;

    private final List<CheckDefinition> checks = List.of(
            new CheckDefinition(
                    "SCAN_BAH_MISSING", "扫描记录病案号为空", "CRITICAL",
                    "SELECT COUNT(*) FROM mr_scan WHERE NULLIF(TRIM(COALESCE(bah, '')), '') IS NULL",
                    """
                    SELECT 'mr_scan' AS entity_type, id::text AS entity_id, bah, sjh,
                           '扫描记录缺少病案号' AS detail
                    FROM mr_scan
                    WHERE NULLIF(TRIM(COALESCE(bah, '')), '') IS NULL
                    ORDER BY id DESC
                    """
            ),
            new CheckDefinition(
                    "SCAN_SJH_MISSING", "扫描记录上架号为空", "WARNING",
                    "SELECT COUNT(*) FROM mr_scan WHERE NULLIF(TRIM(COALESCE(sjh, '')), '') IS NULL",
                    """
                    SELECT 'mr_scan' AS entity_type, id::text AS entity_id, bah, sjh,
                           '扫描记录缺少上架号' AS detail
                    FROM mr_scan
                    WHERE NULLIF(TRIM(COALESCE(sjh, '')), '') IS NULL
                    ORDER BY id DESC
                    """
            ),
            new CheckDefinition(
                    "SCAN_CODE_FORMAT_INVALID", "病案号或上架号不是八位数字", "WARNING",
                    """
                    SELECT COUNT(*) FROM mr_scan
                    WHERE (NULLIF(TRIM(COALESCE(bah, '')), '') IS NOT NULL AND TRIM(bah) !~ '^[0-9]{8}$')
                       OR (NULLIF(TRIM(COALESCE(sjh, '')), '') IS NOT NULL AND TRIM(sjh) !~ '^[0-9]{8}$')
                    """,
                    """
                    SELECT 'mr_scan' AS entity_type, id::text AS entity_id, bah, sjh,
                           '病案号或上架号不符合八位数字规范' AS detail
                    FROM mr_scan
                    WHERE (NULLIF(TRIM(COALESCE(bah, '')), '') IS NOT NULL AND TRIM(bah) !~ '^[0-9]{8}$')
                       OR (NULLIF(TRIM(COALESCE(sjh, '')), '') IS NOT NULL AND TRIM(sjh) !~ '^[0-9]{8}$')
                    ORDER BY id DESC
                    """
            ),
            new CheckDefinition(
                    "SCAN_PAGES_INVALID", "扫描页数为空或小于等于零", "CRITICAL",
                    "SELECT COUNT(*) FROM mr_scan WHERE pages IS NULL OR pages <= 0",
                    """
                    SELECT 'mr_scan' AS entity_type, id::text AS entity_id, bah, sjh,
                           CONCAT('无效页数: ', COALESCE(pages::text, 'NULL')) AS detail
                    FROM mr_scan
                    WHERE pages IS NULL OR pages <= 0
                    ORDER BY id DESC
                    """
            ),
            new CheckDefinition(
                    "SCAN_FILE_METADATA_MISSING", "扫描文件元数据缺失", "CRITICAL",
                    """
                    SELECT COUNT(*) FROM mr_scan
                    WHERE NULLIF(TRIM(COALESCE(filename, '')), '') IS NULL
                       OR NULLIF(TRIM(COALESCE(folder, '')), '') IS NULL
                    """,
                    """
                    SELECT 'mr_scan' AS entity_type, id::text AS entity_id, bah, sjh,
                           CONCAT('文件名=', COALESCE(filename, 'NULL'), ', 目录=', COALESCE(folder, 'NULL')) AS detail
                    FROM mr_scan
                    WHERE NULLIF(TRIM(COALESCE(filename, '')), '') IS NULL
                       OR NULLIF(TRIM(COALESCE(folder, '')), '') IS NULL
                    ORDER BY id DESC
                    """
            ),
            new CheckDefinition(
                    "MIGRATED_WITHOUT_OSS_URL", "已迁移记录缺少 OSS 地址", "CRITICAL",
                    """
                    SELECT COUNT(*) FROM mr_scan
                    WHERE LOWER(COALESCE(migration_status, '')) IN ('migrated', 'success', 'completed')
                      AND NULLIF(TRIM(COALESCE(oss_url, '')), '') IS NULL
                    """,
                    """
                    SELECT 'mr_scan' AS entity_type, id::text AS entity_id, bah, sjh,
                           CONCAT('迁移状态=', COALESCE(migration_status, 'NULL'), '，但 OSS 地址为空') AS detail
                    FROM mr_scan
                    WHERE LOWER(COALESCE(migration_status, '')) IN ('migrated', 'success', 'completed')
                      AND NULLIF(TRIM(COALESCE(oss_url, '')), '') IS NULL
                    ORDER BY id DESC
                    """
            ),
            new CheckDefinition(
                    "MIGRATION_SUCCESS_UNVERIFIED", "迁移成功但未完成校验", "WARNING",
                    """
                    SELECT COUNT(*) FROM image_migration_log
                    WHERE LOWER(COALESCE(migration_status, '')) IN ('migrated', 'success', 'completed')
                      AND verified_at IS NULL
                    """,
                    """
                    SELECT 'image_migration_log' AS entity_type, id::text AS entity_id,
                           NULL::text AS bah, NULL::text AS sjh,
                           CONCAT('scan_id=', scan_id, '，迁移成功但 verified_at 为空') AS detail
                    FROM image_migration_log
                    WHERE LOWER(COALESCE(migration_status, '')) IN ('migrated', 'success', 'completed')
                      AND verified_at IS NULL
                    ORDER BY id DESC
                    """
            ),
            new CheckDefinition(
                    "DUPLICATE_SCAN_PAGE", "扫描页疑似重复", "WARNING",
                    """
                    SELECT COALESCE(SUM(duplicate_count - 1), 0)
                    FROM (
                        SELECT COUNT(*) AS duplicate_count
                        FROM mr_scan
                        WHERE NULLIF(TRIM(COALESCE(bah, '')), '') IS NOT NULL
                        GROUP BY bah, sjh, filename, pages
                        HAVING COUNT(*) > 1
                    ) duplicate_rows
                    """,
                    """
                    SELECT 'mr_scan' AS entity_type, MIN(id)::text AS entity_id, bah, sjh,
                           CONCAT('相同病案号、上架号、文件名和页码重复 ', COUNT(*), ' 条') AS detail
                    FROM mr_scan
                    WHERE NULLIF(TRIM(COALESCE(bah, '')), '') IS NOT NULL
                    GROUP BY bah, sjh, filename, pages
                    HAVING COUNT(*) > 1
                    ORDER BY COUNT(*) DESC, MIN(id) DESC
                    """
            ),
            new CheckDefinition(
                    "STATISTICS_SCAN_NOT_FOUND", "统计记录找不到对应扫描记录", "WARNING",
                    """
                    SELECT COUNT(*)
                    FROM mr_statistics s
                    WHERE NOT EXISTS (
                        SELECT 1 FROM mr_scan sc
                        WHERE NULLIF(TRIM(COALESCE(sc.bah, '')), '') = NULLIF(TRIM(COALESCE(s.bah, '')), '')
                          AND (
                              NULLIF(TRIM(COALESCE(s.sjh, '')), '') IS NULL
                              OR NULLIF(TRIM(COALESCE(sc.sjh, '')), '') = NULLIF(TRIM(COALESCE(s.sjh, '')), '')
                          )
                    )
                    """,
                    """
                    SELECT 'mr_statistics' AS entity_type, s.id::text AS entity_id, s.bah, s.sjh,
                           '统计记录找不到对应扫描记录' AS detail
                    FROM mr_statistics s
                    WHERE NOT EXISTS (
                        SELECT 1 FROM mr_scan sc
                        WHERE NULLIF(TRIM(COALESCE(sc.bah, '')), '') = NULLIF(TRIM(COALESCE(s.bah, '')), '')
                          AND (
                              NULLIF(TRIM(COALESCE(s.sjh, '')), '') IS NULL
                              OR NULLIF(TRIM(COALESCE(sc.sjh, '')), '') = NULLIF(TRIM(COALESCE(s.sjh, '')), '')
                          )
                    )
                    ORDER BY s.id DESC
                    """
            ),
            new CheckDefinition(
                    "ARCHIVE_BOX_EXCEPTION", "档案装箱状态异常", "WARNING",
                    "SELECT COUNT(*) FROM mr_archive_box_record WHERE status <> 'NORMAL'",
                    """
                    SELECT 'mr_archive_box_record' AS entity_type, id::text AS entity_id, bah, sjh,
                           CONCAT('装箱状态=', status, '，实际箱号=', COALESCE(box_no, 'NULL'),
                                  '，预期箱号=', COALESCE(expected_box_no, 'NULL')) AS detail
                    FROM mr_archive_box_record
                    WHERE status <> 'NORMAL'
                    ORDER BY updated_at DESC, id DESC
                    """
            )
    );

    public DataQualityServiceImpl(JdbcTemplate jdbcTemplate, MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
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
            refreshMetricsFromLatestRun();
        } catch (Exception e) {
            logger.warn("Unable to initialize data quality metrics: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "${app.data-quality.cron:0 15 2 * * ?}")
    public void scheduledRun() {
        if (!enabled) {
            return;
        }
        try {
            runChecks("scheduled");
        } catch (Exception e) {
            logger.error("Scheduled data quality run failed", e);
        }
    }

    @Override
    public Map<String, Object> getSummary() {
        List<Map<String, Object>> runs = jdbcTemplate.queryForList("""
                SELECT id, status, triggered_by, check_count, total_issues, critical_count,
                       warning_count, started_at, completed_at, error_message
                FROM mrr_data_quality_run
                ORDER BY id DESC
                LIMIT 1
                """);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("running", running.get());
        result.put("enabled", enabled);
        if (runs.isEmpty()) {
            result.put("latestRun", null);
            result.put("checks", List.of());
            return result;
        }
        Map<String, Object> latestRun = runs.getFirst();
        result.put("latestRun", latestRun);
        result.put("checks", jdbcTemplate.queryForList("""
                SELECT check_code, check_name, severity, issue_count, sampled_count, checked_at
                FROM mrr_data_quality_check_result
                WHERE run_id = ?
                ORDER BY CASE severity WHEN 'CRITICAL' THEN 1 ELSE 2 END, issue_count DESC, check_code
                """, latestRun.get("id")));
        return result;
    }

    @Override
    public List<Map<String, Object>> getIssues(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        List<Long> runIds = jdbcTemplate.query(
                "SELECT id FROM mrr_data_quality_run ORDER BY id DESC LIMIT 1",
                (rs, rowNum) -> rs.getLong(1)
        );
        if (runIds.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.queryForList("""
                SELECT id, check_code, check_name, severity, entity_type, entity_id,
                       bah, sjh, detail, detected_at
                FROM mrr_data_quality_issue
                WHERE run_id = ?
                ORDER BY CASE severity WHEN 'CRITICAL' THEN 1 ELSE 2 END, id DESC
                LIMIT ?
                """, runIds.getFirst(), safeLimit);
    }

    @Override
    public Map<String, Object> runChecks(String triggeredBy) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("数据质量检查正在运行，请稍后重试");
        }
        runningGauge.set(1);
        Long runId = null;
        try {
            String safeTriggeredBy = triggeredBy == null || triggeredBy.isBlank() ? "manual" : triggeredBy.substring(0, Math.min(32, triggeredBy.length()));
            runId = jdbcTemplate.queryForObject("""
                    INSERT INTO mrr_data_quality_run(status, triggered_by, started_at)
                    VALUES ('RUNNING', ?, CURRENT_TIMESTAMP)
                    RETURNING id
                    """, Long.class, safeTriggeredBy);

            long totalIssues = 0;
            long criticalCount = 0;
            long warningCount = 0;
            int safeSampleLimit = Math.max(1, Math.min(sampleLimit, 1000));

            for (CheckDefinition check : checks) {
                Long countValue = jdbcTemplate.queryForObject(check.countSql(), Long.class);
                long issueCount = countValue == null ? 0 : countValue;
                List<Map<String, Object>> samples = issueCount == 0
                        ? List.of()
                        : jdbcTemplate.queryForList(check.sampleSql() + " LIMIT ?", safeSampleLimit);

                jdbcTemplate.update("""
                        INSERT INTO mrr_data_quality_check_result
                            (run_id, check_code, check_name, severity, issue_count, sampled_count)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """, runId, check.code(), check.name(), check.severity(), issueCount, samples.size());

                for (Map<String, Object> sample : samples) {
                    jdbcTemplate.update("""
                            INSERT INTO mrr_data_quality_issue
                                (run_id, check_code, check_name, severity, entity_type, entity_id, bah, sjh, detail)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                            runId, check.code(), check.name(), check.severity(),
                            sample.get("entity_type"), sample.get("entity_id"), sample.get("bah"),
                            sample.get("sjh"), sample.get("detail"));
                }

                totalIssues += issueCount;
                if ("CRITICAL".equals(check.severity())) {
                    criticalCount += issueCount;
                } else {
                    warningCount += issueCount;
                }
            }

            jdbcTemplate.update("""
                    UPDATE mrr_data_quality_run
                    SET status = 'SUCCESS', check_count = ?, total_issues = ?, critical_count = ?,
                        warning_count = ?, completed_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, checks.size(), totalIssues, criticalCount, warningCount, runId);

            cleanupExpiredRuns();
            refreshMetrics(runId);
            logger.info("Data quality run {} completed: total={}, critical={}, warning={}",
                    runId, totalIssues, criticalCount, warningCount);
            return getSummary();
        } catch (Exception e) {
            if (runId != null) {
                jdbcTemplate.update("""
                        UPDATE mrr_data_quality_run
                        SET status = 'FAILED', completed_at = CURRENT_TIMESTAMP, error_message = ?
                        WHERE id = ?
                        """, abbreviate(e.getMessage(), 2000), runId);
            }
            throw new IllegalStateException("数据质量检查失败: " + e.getMessage(), e);
        } finally {
            running.set(false);
            runningGauge.set(0);
        }
    }

    private void cleanupExpiredRuns() {
        int safeRetentionDays = Math.max(7, retentionDays);
        jdbcTemplate.update("""
                DELETE FROM mrr_data_quality_run
                WHERE started_at < CURRENT_TIMESTAMP - make_interval(days => ?)
                """, safeRetentionDays);
    }

    private void refreshMetricsFromLatestRun() {
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM mrr_data_quality_run WHERE status = 'SUCCESS' ORDER BY id DESC LIMIT 1",
                (rs, rowNum) -> rs.getLong(1)
        );
        if (!ids.isEmpty()) {
            refreshMetrics(ids.getFirst());
        }
    }

    private void refreshMetrics(long runId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT check_code, severity, issue_count
                FROM mrr_data_quality_check_result
                WHERE run_id = ?
                """, runId);
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
        lastSuccessTimestampGauge.set(Instant.now().getEpochSecond());
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) {
            return "unknown error";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private record CheckDefinition(
            String code,
            String name,
            String severity,
            String countSql,
            String sampleSql
    ) {
    }
}
