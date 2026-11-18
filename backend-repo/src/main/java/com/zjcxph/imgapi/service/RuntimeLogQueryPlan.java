package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.exception.BusinessException;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 运行日志检索计划：校验条件并生成参数化 SQL。
 *
 * <p>把校验与拼装从 JDBC 执行里拆出来，一是不让筛选口径散落在控制器与服务之间，
 * 二是这类边界（时间窗、通配符转义、级别展开）必须能在无数据库环境下固定住。
 * 所有值都以参数形式传入，SQL 中不含任何用户输入。</p>
 *
 * <p>正文关键词检索刻意限制在 24 小时窗口内：{@code message_summary} 上的模糊匹配
 * 走的是分区内顺序扫描，窗口越长代价越高。长时间跨度应改用追踪编号、错误编号或模块
 * 定位，这几项都有索引支撑。</p>
 */
public record RuntimeLogQueryPlan(String sql, List<Object> args) {

    private static final Set<String> LEVELS = Set.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR");
    private static final List<String> LEVEL_ORDER = List.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR");
    private static final Duration MAX_RANGE = Duration.ofDays(31);
    private static final Duration KEYWORD_RANGE = Duration.ofHours(24);
    private static final int MAX_KEYWORD_LENGTH = 64;
    private static final int MAX_FILTER_LENGTH = 255;
    private static final int MAX_PAGE_SIZE = 200;

    /** 与 {@code event_id} 主键列一致的采样列，顺序决定前端字段名。 */
    public static final String SELECT_COLUMNS =
            "occurred_at,event_id,level,logger_name,request_id,error_id,message_summary,stack_trace";

    /** 时间范围归一化：缺省最近 24 小时，最长 31 天。 */
    public static Instant[] range(Instant from, Instant to) {
        Instant end = to == null ? Instant.now() : to;
        Instant start = from == null ? end.minusSeconds(86400) : from;
        if (!start.isBefore(end) || Duration.between(start, end).compareTo(MAX_RANGE) > 0) {
            throw new BusinessException(400, "请选择31天内的时间范围");
        }
        return new Instant[]{start, end};
    }

    /** 趋势分桶；超过 48 小时改用天，避免点数膨胀。 */
    public static String bucket(Duration span) {
        return span.toHours() <= 48 ? "hour" : "day";
    }

    public static RuntimeLogQueryPlan search(Instant from, Instant to, String level, String minLevel,
            String keyword, String requestId, String errorId, String logger,
            Instant cursorTime, UUID cursorId, int size) {
        Instant[] range = range(from, to);
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(400, "请选择31天内的时间范围和有效分页条件");
        }
        if ((cursorTime == null) != (cursorId == null)) {
            throw new BusinessException(400, "请选择31天内的时间范围和有效分页条件");
        }

        String normalizedLevel = normalizeLevel(level, "日志级别无效");
        String normalizedMinLevel = normalizeLevel(minLevel, "最低日志级别无效");
        if (normalizedLevel != null && normalizedMinLevel != null) {
            throw new BusinessException(400, "日志级别与最低日志级别只能选一个");
        }
        String normalizedKeyword = normalizeText(keyword, MAX_KEYWORD_LENGTH, "正文关键词过长");
        if (normalizedKeyword != null && Duration.between(range[0], range[1]).compareTo(KEYWORD_RANGE) > 0) {
            throw new BusinessException(400, "正文检索请把时间范围缩小到24小时内，或改用追踪编号、错误编号、模块定位");
        }

        StringBuilder sql = new StringBuilder("SELECT ").append(SELECT_COLUMNS)
                .append(" FROM app.runtime_log_event WHERE occurred_at>=? AND occurred_at<?");
        List<Object> args = new ArrayList<>();
        args.add(Timestamp.from(range[0]));
        args.add(Timestamp.from(range[1]));

        if (normalizedLevel != null) {
            sql.append(" AND level=?");
            args.add(normalizedLevel);
        }
        if (normalizedMinLevel != null) {
            List<String> accepted = LEVEL_ORDER.subList(LEVEL_ORDER.indexOf(normalizedMinLevel), LEVEL_ORDER.size());
            sql.append(" AND level IN (").append("?,".repeat(accepted.size() - 1)).append("?)");
            args.addAll(accepted);
        }
        if (normalizedKeyword != null) {
            sql.append(" AND message_summary ILIKE ? ESCAPE '\\'");
            args.add("%" + escapeLike(normalizedKeyword) + "%");
        }
        appendEquals(sql, args, "request_id", normalizeText(requestId, MAX_FILTER_LENGTH, "筛选条件过长"));
        appendEquals(sql, args, "error_id", normalizeText(errorId, 32, "错误编号过长"));
        String normalizedLogger = normalizeText(logger, MAX_FILTER_LENGTH, "模块名称过长");
        if (normalizedLogger != null) {
            sql.append(" AND logger_name ILIKE ? ESCAPE '\\'");
            args.add("%" + escapeLike(normalizedLogger) + "%");
        }
        if (cursorTime != null) {
            sql.append(" AND (occurred_at,event_id)<(?,?)");
            args.add(Timestamp.from(cursorTime));
            args.add(cursorId);
        }
        sql.append(" ORDER BY occurred_at DESC,event_id DESC LIMIT ?");
        args.add(size + 1);
        return new RuntimeLogQueryPlan(sql.toString(), List.copyOf(args));
    }

    private static void appendEquals(StringBuilder sql, List<Object> args, String column, String value) {
        if (value == null) {
            return;
        }
        sql.append(" AND ").append(column).append("=?");
        args.add(value);
    }

    private static String normalizeLevel(String value, String message) {
        String normalized = normalizeText(value, 16, message);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(java.util.Locale.ROOT);
        if (!LEVELS.contains(normalized)) {
            throw new BusinessException(400, message);
        }
        return normalized;
    }

    private static String normalizeText(String value, int maxLength, String message) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > maxLength) {
            throw new BusinessException(400, message);
        }
        return trimmed;
    }

    /**
     * 转义 LIKE 元字符。
     *
     * <p>不转义时用户输入的 {@code %} 会把检索变成全表匹配，{@code _} 会放宽单字符匹配，
     * 结果既慢又容易误导排查方向。</p>
     */
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
