package com.zjcxph.imgapi.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.ResultSetMetaData;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.atomic.AtomicBoolean;

/**
 * 大规模病案完整性统计快照。
 *
 * <p>约三千万行的扫描表统计只在后台任务中执行，HTTP 接口始终读取最近一次
 * 完成的快照，避免管理员刷新页面直接触发生产库全表扫描。</p>
 */
@Service
public class IntegrityDiagnosticsService {

    private static final Logger logger = LoggerFactory.getLogger(IntegrityDiagnosticsService.class);

    private final JdbcTemplate jdbcTemplate;
    private final int queryTimeoutSeconds;
    private final AtomicBoolean refreshing = new AtomicBoolean(false);
    private final ExecutorService refreshExecutor;

    private volatile Map<String, Object> cachedSnapshot = pendingSnapshot();

    public IntegrityDiagnosticsService(
            JdbcTemplate jdbcTemplate,
            @Value("${app.operations.integrity-query-timeout-seconds:180}") int queryTimeoutSeconds
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.queryTimeoutSeconds = Math.max(10, queryTimeoutSeconds);
        this.refreshExecutor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "mrr-integrity-refresh");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 仅读取内存快照，不执行统计 SQL。
     */
    public Map<String, Object> getSnapshot() {
        Map<String, Object> result = new LinkedHashMap<>(cachedSnapshot);
        result.put("refreshing", refreshing.get());
        return result;
    }

    @Scheduled(
            fixedDelayString = "${app.operations.integrity-refresh-ms:3600000}",
            initialDelayString = "${app.operations.integrity-initial-delay-ms:10000}"
    )
    public void refreshScheduled() {
        requestRefresh();
    }

    /**
     * 向专用单线程执行器提交刷新任务，调度线程立即返回。
     */
    public boolean requestRefresh() {
        if (!refreshing.compareAndSet(false, true)) {
            return false;
        }
        refreshExecutor.execute(this::refreshSnapshot);
        return true;
    }

    @PreDestroy
    public void shutdown() {
        refreshExecutor.shutdownNow();
    }

    private void refreshSnapshot() {
        String attemptAt = Instant.now().toString();
        try {
            cachedSnapshot = buildSnapshot();
        } catch (Exception exception) {
            logger.error("刷新病案完整性快照失败", exception);
            Map<String, Object> failed = new LinkedHashMap<>(cachedSnapshot);
            failed.put("status", "ERROR");
            failed.put("lastAttemptAt", attemptAt);
            failed.put("lastError", safeMessage(exception));
            cachedSnapshot = Map.copyOf(failed);
        } finally {
            refreshing.set(false);
        }
    }

    private Map<String, Object> buildSnapshot() {
        Map<String, Object> scans = queryForMap("""
                SELECT
                    COUNT(*) AS total,
                    COUNT(*) FILTER (WHERE archive_id IS NOT NULL) AS archive_linked,
                    COUNT(*) FILTER (WHERE NULLIF(BTRIM(oss_url), '') IS NOT NULL) AS oss_linked,
                    COUNT(*) FILTER (
                        WHERE NULLIF(BTRIM(sjh), '') IS NULL
                          AND CASE
                              WHEN normalized_bah ~ '^[0-9]+$'
                                  THEN normalized_bah::NUMERIC >= 10000000
                              ELSE FALSE
                          END
                    ) AS missing_sjh
                FROM (
                    SELECT
                        archive_id,
                        oss_url,
                        sjh,
                        app.normalize_medical_record_code(bah) AS normalized_bah
                    FROM app.mr_scan
                    WHERE uploadflag <> 0
                ) active_scans
                """);

        long totalScans = number(scans.get("total"));
        long scanArchiveLinked = number(scans.get("archive_linked"));
        long ossLinked = number(scans.get("oss_linked"));

        long brokenLinks = queryForLong("""
                SELECT COUNT(*)
                FROM app.mr_scan s
                LEFT JOIN app.mr_archive a ON a.id = s.archive_id
                WHERE s.uploadflag <> 0
                  AND s.archive_id IS NOT NULL
                  AND a.id IS NULL
                """);

        Map<String, Object> duplicateGroups = queryForMap("""
                WITH normalized_archive AS MATERIALIZED (
                    SELECT
                        app.normalize_medical_record_code(bah) AS normalized_bah,
                        app.normalize_medical_record_code(sjh) AS normalized_sjh
                    FROM app.mr_archive
                )
                SELECT
                    (
                        SELECT COUNT(*) FROM (
                            SELECT normalized_bah
                            FROM normalized_archive
                            WHERE CASE
                                WHEN normalized_bah ~ '^[0-9]+$'
                                    THEN normalized_bah::NUMERIC < 10000000
                                ELSE FALSE
                            END
                            GROUP BY normalized_bah
                            HAVING COUNT(*) > 1
                        ) old_duplicates
                    ) AS old_bah_duplicates,
                    (
                        SELECT COUNT(*) FROM (
                            SELECT normalized_sjh
                            FROM normalized_archive
                            WHERE CASE
                                WHEN normalized_bah ~ '^[0-9]+$'
                                    THEN normalized_bah::NUMERIC >= 10000000
                                ELSE FALSE
                            END
                              AND normalized_sjh IS NOT NULL
                            GROUP BY normalized_sjh
                            HAVING COUNT(*) > 1
                        ) new_duplicates
                    ) AS new_sjh_duplicates
                """);
        long oldBahDuplicates = number(duplicateGroups.get("old_bah_duplicates"));
        long newSjhDuplicates = number(duplicateGroups.get("new_sjh_duplicates"));

        List<Map<String, Object>> tables = new ArrayList<>();
        tables.add(coverage("mr_scan", totalScans, scanArchiveLinked));
        tables.add(loadCoverage("mr_statistics", "TRUE"));
        tables.add(loadCoverage("mr_archive_box_record", "TRUE"));

        String generatedAt = Instant.now().toString();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "READY");
        result.put("generatedAt", generatedAt);
        result.put("lastAttemptAt", generatedAt);
        result.put("lastError", "");
        result.put("archiveCoverage", ratio(scanArchiveLinked, totalScans));
        result.put("ossCoverage", ratio(ossLinked, totalScans));
        result.put("missingSjh", number(scans.get("missing_sjh")));
        result.put("brokenLinks", brokenLinks);
        result.put("duplicateArchiveGroups", oldBahDuplicates + newSjhDuplicates);
        result.put("duplicateArchiveDetails", Map.of(
                "legacyBahGroups", oldBahDuplicates,
                "modernSjhGroups", newSjhDuplicates
        ));
        result.put("totalActiveScans", totalScans);
        result.put("tables", List.copyOf(tables));
        return Map.copyOf(result);
    }

    private Map<String, Object> loadCoverage(String table, String filter) {
        Map<String, Object> row = queryForMap(
                "SELECT COUNT(*) AS total, COUNT(*) FILTER (WHERE archive_id IS NOT NULL) AS linked "
                        + "FROM app." + table + " WHERE " + filter
        );
        return coverage(table, number(row.get("total")), number(row.get("linked")));
    }

    private Map<String, Object> coverage(String table, long total, long linked) {
        return Map.of(
                "table", table,
                "total", total,
                "linked", linked,
                "unlinked", Math.max(0L, total - linked),
                "coverage", ratio(linked, total)
        );
    }

    private Map<String, Object> queryForMap(String sql) {
        return jdbcTemplate.query(
                sql,
                statement -> statement.setQueryTimeout(queryTimeoutSeconds),
                resultSet -> {
                    if (!resultSet.next()) {
                        return Map.of();
                    }
                    ResultSetMetaData metadata = resultSet.getMetaData();
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int index = 1; index <= metadata.getColumnCount(); index++) {
                        row.put(metadata.getColumnLabel(index), resultSet.getObject(index));
                    }
                    return row;
                }
        );
    }

    private long queryForLong(String sql) {
        Long value = jdbcTemplate.query(
                sql,
                statement -> statement.setQueryTimeout(queryTimeoutSeconds),
                resultSet -> resultSet.next() ? resultSet.getLong(1) : 0L
        );
        return value == null ? 0L : value;
    }

    private double ratio(long value, long total) {
        if (total <= 0) {
            return 1.0d;
        }
        return Math.round((value * 10000.0d / total)) / 10000.0d;
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() <= 300 ? message : message.substring(0, 300);
    }

    private static Map<String, Object> pendingSnapshot() {
        return Map.ofEntries(
                Map.entry("status", "PENDING"),
                Map.entry("generatedAt", ""),
                Map.entry("lastAttemptAt", ""),
                Map.entry("lastError", ""),
                Map.entry("archiveCoverage", 0.0d),
                Map.entry("ossCoverage", 0.0d),
                Map.entry("missingSjh", 0L),
                Map.entry("brokenLinks", 0L),
                Map.entry("duplicateArchiveGroups", 0L),
                Map.entry("duplicateArchiveDetails", Map.of(
                        "legacyBahGroups", 0L,
                        "modernSjhGroups", 0L
                )),
                Map.entry("totalActiveScans", 0L),
                Map.entry("tables", List.of())
        );
    }
}
