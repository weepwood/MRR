package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.exception.BusinessException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 运行日志明细检索与问题聚合。
 *
 * <p>聚合结果只用于定位方向，不替代明细：{@code counts}/{@code trend} 直接读分区明细，
 * 走 {@code (level, occurred_at)} 索引，只覆盖 WARN/ERROR 两类；
 * {@code topErrors}/{@code topModules} 先按窗口内命中次数聚合错误编号，再用小表
 * {@code system_error_event} 补全摘要，避免对明细做多列分组。</p>
 */
@Service
public class RuntimeLogQueryService {

    private static final int QUERY_TIMEOUT_SECONDS = 3;
    private static final int MAX_TREND_POINTS = 50;
    private static final int TOP_ERROR_SCAN = 50;
    private static final int TOP_ERROR_LIMIT = 10;
    private static final int TOP_MODULE_LIMIT = 8;

    private final JdbcTemplate jdbc;

    public RuntimeLogQueryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String, Object> search(Instant from, Instant to, String level, String minLevel, String keyword,
            String requestId, String errorId, String logger, Instant cursorTime, UUID cursorId, int size) {
        RuntimeLogQueryPlan plan = RuntimeLogQueryPlan.search(from, to, level, minLevel, keyword, requestId,
                errorId, logger, cursorTime, cursorId, size);
        RowMapper<Map<String, Object>> mapper = (rs, n) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String key : List.of("occurred_at", "event_id", "level", "logger_name", "request_id",
                    "error_id", "message_summary", "stack_trace")) {
                row.put(key, key.equals("occurred_at") ? rs.getTimestamp(key).toInstant().toString() : rs.getString(key));
            }
            return row;
        };
        List<Map<String, Object>> fetched = query(plan.sql(), plan.args(), mapper);
        boolean more = fetched.size() > size;
        List<Map<String, Object>> items = more ? fetched.subList(0, size) : fetched;
        return Map.of("list", items, "hasMore", more);
    }

    /**
     * 选定时间窗内的问题概览。
     *
     * @return {@code range}/{@code counts}/{@code trend}/{@code topErrors}/{@code topModules}/{@code unresolvedGroups}
     */
    public Map<String, Object> insights(Instant from, Instant to) {
        Instant[] range = RuntimeLogQueryPlan.range(from, to);
        Instant start = range[0];
        Instant end = range[1];
        String bucket = RuntimeLogQueryPlan.bucket(Duration.between(start, end));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("range", Map.of("from", start.toString(), "to", end.toString(), "bucket", bucket));
        result.put("counts", levelCounts(start, end));
        result.put("trend", trend(start, end, bucket));
        List<Map<String, Object>> active = activeErrors(start, end);
        result.put("topErrors", active.stream().limit(TOP_ERROR_LIMIT).toList());
        result.put("topModules", topModules(active));
        result.put("unresolvedGroups", unresolvedGroups(start, end));
        return result;
    }

    private Map<String, Object> levelCounts(Instant start, Instant end) {
        String sql = "SELECT "
                + "(SELECT COUNT(*) FROM app.runtime_log_event WHERE level='ERROR' AND occurred_at>=? AND occurred_at<?) AS error_count,"
                + "(SELECT COUNT(*) FROM app.runtime_log_event WHERE level='WARN' AND occurred_at>=? AND occurred_at<?) AS warn_count";
        Map<String, Object> counts = query(sql, List.of(Timestamp.from(start), Timestamp.from(end),
                Timestamp.from(start), Timestamp.from(end)), (rs, n) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("ERROR", rs.getLong("error_count"));
                    row.put("WARN", rs.getLong("warn_count"));
                    return row;
                }).getFirst();
        return counts;
    }

    /**
     * 只统计 WARN/ERROR 的分时趋势；INFO 及以下数量级更大且对定位无帮助。
     *
     * <p>分桶跟随应用服务器时区而不是 UTC：按天聚合时若用 UTC，边界会落在本地 08:00，
     * 凌晨发生的错误会被算到前一天。时区名先做字符白名单校验再拼进 SQL，非法时退回 UTC。</p>
     */
    private List<Map<String, Object>> trend(Instant start, Instant end, String bucket) {
        String field = "day".equals(bucket) ? "day" : "hour";
        ZoneId zone = resolveZone();
        String sql = "SELECT date_trunc('" + field + "', occurred_at AT TIME ZONE '" + zone.getId() + "') AS bucket_start,"
                + " level, COUNT(*) AS hits FROM app.runtime_log_event"
                + " WHERE occurred_at>=? AND occurred_at<? AND level IN ('WARN','ERROR')"
                + " GROUP BY 1,2 ORDER BY 1";
        List<Map<String, Object>> rows = query(sql, List.of(Timestamp.from(start), Timestamp.from(end)),
                (rs, n) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("bucket", rs.getObject("bucket_start", LocalDateTime.class).atZone(zone).toInstant());
                    row.put("level", rs.getString("level"));
                    row.put("hits", rs.getLong("hits"));
                    return row;
                });

        Map<Instant, long[]> byBucket = new HashMap<>();
        for (Map<String, Object> row : rows) {
            long[] values = byBucket.computeIfAbsent((Instant) row.get("bucket"), key -> new long[2]);
            values["ERROR".equals(row.get("level")) ? 0 : 1] += (Long) row.get("hits");
        }

        ChronoUnit unit = "day".equals(bucket) ? ChronoUnit.DAYS : ChronoUnit.HOURS;
        Instant cursor = start.atZone(zone).truncatedTo(unit).toInstant();
        List<Map<String, Object>> series = new ArrayList<>();
        while (cursor.isBefore(end) && series.size() < MAX_TREND_POINTS) {
            long[] values = byBucket.getOrDefault(cursor, new long[2]);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("bucketStart", cursor.toString());
            point.put("error", values[0]);
            point.put("warn", values[1]);
            series.add(point);
            cursor = cursor.plus(1, unit);
        }
        return series;
    }

    /**
     * 窗口内仍在发生的错误组。
     *
     * <p>这里刻意不使用明细表按 {@code error_id} 分组：明细里的 {@code error_id} 是
     * <strong>每次事件</strong>各自的编号（用于用户报障检索），而错误组只保留首次编号并
     * 把后续编号放进 {@code error_ids}。按明细分组会把同一组拆成多条、且大部分编号在
     * 聚合表里查不到摘要。因此以 {@code system_error_event} 为准，取窗口内有发生的组。</p>
     *
     * <p>{@code occurrence_count} 是生命周期累计值，不能当作窗口内频次；页面按
     * 「累计次数 + 最近发生」展示，并明确标注口径。</p>
     */
    private List<Map<String, Object>> activeErrors(Instant start, Instant end) {
        return query("SELECT error_id, level, module, message_summary, occurrence_count, last_seen_at,"
                        + " request_id, status FROM app.system_error_event"
                        + " WHERE last_seen_at>=? AND last_seen_at<?"
                        + " ORDER BY occurrence_count DESC, last_seen_at DESC LIMIT ?",
                List.of(Timestamp.from(start), Timestamp.from(end), TOP_ERROR_SCAN),
                (rs, n) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("errorId", rs.getString("error_id"));
                    row.put("level", rs.getString("level"));
                    row.put("module", rs.getString("module"));
                    row.put("message", com.zjcxph.imgapi.config.PrivateJsonLogEncoder
                            .safeStoredMessage(rs.getString("message_summary")));
                    row.put("occurrenceCount", rs.getLong("occurrence_count"));
                    row.put("lastSeenAt", rs.getTimestamp("last_seen_at").toInstant().toString());
                    row.put("requestId", rs.getString("request_id"));
                    row.put("status", rs.getString("status"));
                    return row;
                });
    }

    private List<Map<String, Object>> topModules(List<Map<String, Object>> errors) {
        Map<String, long[]> grouped = new LinkedHashMap<>();
        for (Map<String, Object> error : errors) {
            String module = error.get("module") == null ? "unknown" : String.valueOf(error.get("module"));
            long occurrences = (Long) error.get("occurrenceCount");
            long[] values = grouped.computeIfAbsent(module, key -> new long[2]);
            values[0] += occurrences;
            values[1] += 1;
        }
        return grouped.entrySet().stream()
                .sorted((left, right) -> Long.compare(right.getValue()[0], left.getValue()[0]))
                .limit(TOP_MODULE_LIMIT)
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("module", entry.getKey());
                    row.put("occurrences", entry.getValue()[0]);
                    row.put("groups", entry.getValue()[1]);
                    return row;
                })
                .toList();
    }

    /** 时区名会拼进 SQL，只接受安全字符；异常时退回 UTC，避免拼接风险。 */
    private static ZoneId resolveZone() {
        ZoneId zone = ZoneId.systemDefault();
        return zone.getId().matches("[A-Za-z0-9_/+\\-]{1,64}") ? zone : ZoneOffset.UTC;
    }

    private long unresolvedGroups(Instant start, Instant end) {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM app.system_error_event"
                + " WHERE status='OPEN' AND last_seen_at>=? AND last_seen_at<?", Long.class,
                Timestamp.from(start), Timestamp.from(end));
        return value == null ? 0 : value;
    }

    private <T> List<T> query(String sql, List<Object> args, RowMapper<T> mapper) {
        try {
            return jdbc.query(sql, statement -> {
                statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                for (int i = 0; i < args.size(); i++) {
                    statement.setObject(i + 1, args.get(i));
                }
            }, mapper);
        } catch (DataAccessException e) {
            throw translate(e);
        }
    }

    /**
     * 把语句超时翻译成可操作的提示。
     *
     * <p>超时是「条件太宽」而不是「系统坏了」；原样抛出会变成 500，让排障的人误以为
     * 日志功能故障。其他数据库异常继续冒泡，避免掩盖真实故障。</p>
     */
    private static RuntimeException translate(DataAccessException e) {
        String cause = e.getMostSpecificCause().getMessage();
        if (cause != null && (cause.contains("timeout") || cause.contains("canceling statement"))) {
            return new BusinessException(400, "检索超时：请缩小时间范围，或用追踪编号、错误编号、模块缩小范围");
        }
        return e;
    }
}
