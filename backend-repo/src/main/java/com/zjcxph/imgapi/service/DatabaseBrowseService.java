package com.zjcxph.imgapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.common.ResultCode;
import com.zjcxph.imgapi.dto.resp.browse.DbBrowseColumnView;
import com.zjcxph.imgapi.dto.resp.browse.DbBrowsePage;
import com.zjcxph.imgapi.dto.resp.browse.DbBrowseTableSchema;
import com.zjcxph.imgapi.dto.resp.browse.DbBrowseTableSummary;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.repository.DatabaseBrowseRepository;
import com.zjcxph.imgapi.setting.browse.ColumnType;
import com.zjcxph.imgapi.setting.browse.DbBrowseColumn;
import com.zjcxph.imgapi.setting.browse.DbBrowseRegistry;
import com.zjcxph.imgapi.setting.browse.DbBrowseTable;
import com.zjcxph.imgapi.setting.browse.FilterOp;
import com.zjcxph.imgapi.utils.AuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 受控只读数据库浏览（ADR-0007）。
 *
 * <p>安全边界集中在本类：可浏览对象只来自 {@link DbBrowseRegistry}；HTTP 参数只作为绑定值，
 * 绝不参与 SQL 结构拼接；查询在只读事务中执行并受语句超时与行数上限约束。</p>
 */
@Service
public class DatabaseBrowseService {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseBrowseService.class);

    private static final int MAX_FILTERS = 5;
    private static final int HARD_MAX_PAGE_SIZE = 200;
    private static final String DATETIME_SQL_PATTERN = "'YYYY-MM-DD\"T\"HH24:MI:SS'";
    private static final String DATE_SQL_PATTERN = "'YYYY-MM-DD'";

    private final DbBrowseRegistry registry;
    private final DatabaseBrowseRepository repository;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public DatabaseBrowseService(DbBrowseRegistry registry,
                                 DatabaseBrowseRepository repository,
                                 ObjectMapper objectMapper,
                                 @Value("${app.operations.db-browse-enabled:true}") boolean enabled) {
        this.registry = registry;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<DbBrowseTableSummary> listTables() {
        requireEnabled();
        return registry.all().stream()
                .filter(DbBrowseTable::enabled)
                .filter(this::canAccess)
                .map(this::toSummary)
                .toList();
    }

    public DbBrowseTableSchema getSchema(String tableKey) {
        requireEnabled();
        DbBrowseTable table = requireAccessible(tableKey);
        return new DbBrowseTableSchema(
                table.key(),
                table.label(),
                table.personalData(),
                table.requireFilter(),
                table.keyColumns(),
                table.maxPageSize(),
                table.returnedColumns().stream().map(this::toColumnView).toList(),
                table.omittedColumnCount());
    }

    @Transactional(readOnly = true)
    public DbBrowsePage readRows(String tableKey, String rawFilter, String orderBy, String order,
                                 int page, int size, String cursor) {
        long started = System.currentTimeMillis();
        // 审计必须在参数校验外层：越权探测与非法参数同样要留痕，否则「被拒绝的浏览」不可追溯。
        String outcome = "OK";
        String auditTable = sanitizeTableKey(tableKey);
        int rowCount = 0;
        String digest = "-";
        try {
            requireEnabled();
            if (page < 1) {
                throw fail(ResultCode.DB_BROWSE_PARAMETER_INVALID);
            }
            DbBrowseTable table = requireAccessible(tableKey);
            auditTable = table.key();
            int effectiveSize = resolveSize(table, size);
            List<FilterCondition> conditions = parseFilters(table, rawFilter);
            digest = filterDigest(conditions);
            if (table.requireFilter() && conditions.isEmpty()) {
                throw fail(ResultCode.DB_BROWSE_FILTER_REQUIRED);
            }
            List<String> sortColumns = resolveSortColumns(table, orderBy);
            boolean descending = resolveDescending(order);
            CursorPosition position = decodeCursor(cursor, table, sortColumns, descending);
            if (page > 1 && position == null) {
                throw fail(ResultCode.DB_BROWSE_CURSOR_INVALID);
            }

            SqlSpec spec = buildSql(table, conditions, sortColumns, descending, position, effectiveSize + 1);
            List<Map<String, Object>> rawRows = repository.query(spec.sql(), spec.args());
            boolean hasMore = rawRows.size() > effectiveSize;
            List<Map<String, Object>> pageRows = hasMore ? rawRows.subList(0, effectiveSize) : rawRows;
            rowCount = pageRows.size();

            Set<String> truncated = new LinkedHashSet<>();
            List<Map<String, Object>> maskedRows = new ArrayList<>(pageRows.size());
            for (Map<String, Object> raw : pageRows) {
                maskedRows.add(maskRow(table, raw, truncated));
            }

            String nextCursor = hasMore
                    ? encodeCursor(table, pageRows.get(pageRows.size() - 1), sortColumns, descending)
                    : null;

            Long estimatedRows = table.estimateRowCount() ? estimateRows(table) : null;
            long tookMs = System.currentTimeMillis() - started;

            return new DbBrowsePage(
                    table.key(),
                    table.returnedColumns().stream().map(this::toColumnView).toList(),
                    maskedRows,
                    page,
                    effectiveSize,
                    hasMore,
                    nextCursor,
                    estimatedRows,
                    estimatedRows != null,
                    table.omittedColumnCount(),
                    List.copyOf(truncated),
                    tookMs);
        } catch (BusinessException e) {
            outcome = mapOutcome(e.getCode());
            throw e;
        } catch (QueryTimeoutException e) {
            outcome = "TIMEOUT";
            throw fail(ResultCode.DB_BROWSE_QUERY_TIMEOUT);
        } catch (DataAccessException e) {
            outcome = "ERROR";
            // 底层异常细节不外泄给客户端，只在本机日志保留可定位信息。
            logger.error("数据库浏览查询失败: table={}", auditTable, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR.getCode(), "查询失败，请稍后重试");
        } finally {
            logger.info("DB_BROWSE table={} outcome={} rows={} filters={} durationMs={}",
                    auditTable, outcome, rowCount, digest, System.currentTimeMillis() - started);
        }
    }

    // ---------------------------------------------------------------- 访问控制

    private void requireEnabled() {
        if (!enabled) {
            throw fail(ResultCode.DB_BROWSE_DISABLED);
        }
    }

    private DbBrowseTable requireAccessible(String tableKey) {
        // 未登记、未启用、无表级权限统一返回 3001，避免通过状态码枚举系统存在哪些表。
        DbBrowseTable table = registry.find(tableKey)
                .filter(DbBrowseTable::enabled)
                .orElseThrow(() -> fail(ResultCode.DB_BROWSE_TABLE_NOT_FOUND));
        if (!canAccess(table)) {
            throw fail(ResultCode.DB_BROWSE_TABLE_NOT_FOUND);
        }
        return table;
    }

    private boolean canAccess(DbBrowseTable table) {
        AuthSession session = AuthContext.getCurrentUser();
        if (session == null) {
            return false;
        }
        // hasPermission 对 ADMIN 恒为 true，与 AuthorizationInterceptor 的既有语义保持一致。
        return table.requiredPermissions().stream().allMatch(session::hasPermission);
    }

    // ---------------------------------------------------------------- 参数与 SQL

    private int resolveSize(DbBrowseTable table, int size) {
        if (size <= 0) {
            throw fail(ResultCode.DB_BROWSE_PARAMETER_INVALID);
        }
        int max = Math.min(table.maxPageSize(), HARD_MAX_PAGE_SIZE);
        if (size > max) {
            throw fail(ResultCode.DB_BROWSE_BUDGET_EXCEEDED);
        }
        return size;
    }

    /**
     * 排序方向只接受 {@code asc}/{@code desc}。
     *
     * <p>早期实现把任何非 {@code asc} 的值当作 {@code desc} 处理，等于静默接受
     * {@code order=foo}；游标方向校验也依赖这个值，因此必须显式拒绝。</p>
     */
    private static boolean resolveDescending(String order) {
        if (order == null || order.isBlank()) {
            return true;
        }
        String normalized = order.trim();
        if ("desc".equalsIgnoreCase(normalized)) {
            return true;
        }
        if ("asc".equalsIgnoreCase(normalized)) {
            return false;
        }
        throw fail(ResultCode.DB_BROWSE_PARAMETER_INVALID);
    }

    private List<String> resolveSortColumns(DbBrowseTable table, String orderBy) {
        List<String> columns = new ArrayList<>();
        if (orderBy != null && !orderBy.isBlank()) {
            String requested = orderBy.trim();
            if (!table.orderableColumns().contains(requested)) {
                throw fail(ResultCode.DB_BROWSE_PARAMETER_INVALID);
            }
            columns.add(requested);
        }
        for (String keyColumn : table.keyColumns()) {
            if (!columns.contains(keyColumn)) {
                columns.add(keyColumn);
            }
        }
        return columns;
    }

    private List<FilterCondition> parseFilters(DbBrowseTable table, String rawFilter) {
        if (rawFilter == null || rawFilter.isBlank()) {
            return List.of();
        }
        String[] segments = rawFilter.split(",");
        if (segments.length > MAX_FILTERS) {
            throw fail(ResultCode.DB_BROWSE_BUDGET_EXCEEDED);
        }
        Set<String> filterable = table.filterableColumnNames();
        List<FilterCondition> conditions = new ArrayList<>(segments.length);
        for (String segment : segments) {
            if (segment.isBlank()) {
                continue;
            }
            // 值里可能含冒号（时间），只切前两段。
            String[] parts = segment.split(":", 3);
            if (parts.length < 2) {
                throw fail(ResultCode.DB_BROWSE_PARAMETER_INVALID);
            }
            String columnName = parts[0].trim();
            if (!filterable.contains(columnName)) {
                throw fail(ResultCode.DB_BROWSE_PARAMETER_INVALID);
            }
            DbBrowseColumn column = table.column(columnName)
                    .orElseThrow(() -> fail(ResultCode.DB_BROWSE_PARAMETER_INVALID));
            FilterOp op = FilterOp.parse(parts[1])
                    .filter(column.allowedOps()::contains)
                    .orElseThrow(() -> fail(ResultCode.DB_BROWSE_PARAMETER_INVALID));
            String value = parts.length == 3 ? decodeFilterValue(parts[2]) : null;
            if (op.requiresValue() && (value == null || value.isEmpty())) {
                throw fail(ResultCode.DB_BROWSE_PARAMETER_INVALID);
            }
            conditions.add(new FilterCondition(column, op, value));
        }
        return conditions;
    }

    private SqlSpec buildSql(DbBrowseTable table, List<FilterCondition> conditions,
                             List<String> sortColumns, boolean descending,
                             CursorPosition position, int limit) {
        StringBuilder sql = new StringBuilder("SELECT ");
        List<String> selects = new ArrayList<>();
        for (DbBrowseColumn column : table.returnedColumns()) {
            selects.add(selectExpression(column));
        }
        sql.append(String.join(", ", selects));
        sql.append(" FROM ").append(table.schemaName()).append('.').append(table.tableName());

        List<String> where = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        for (FilterCondition condition : conditions) {
            appendFilter(where, args, condition);
        }
        if (position != null) {
            appendKeyset(where, args, position, descending);
        }
        if (!where.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", where));
        }

        List<String> orderClauses = new ArrayList<>();
        for (String column : sortColumns) {
            orderClauses.add(column + (descending ? " DESC" : " ASC"));
        }
        sql.append(" ORDER BY ").append(String.join(", ", orderClauses));
        sql.append(" LIMIT ?");
        args.add(limit);
        return new SqlSpec(sql.toString(), args);
    }

    private String selectExpression(DbBrowseColumn column) {
        // 时间列在 SQL 内格式化，避免 Java 侧对 Timestamp/OffsetDateTime 做不一致处理。
        return switch (column.type()) {
            case DATETIME -> "to_char(" + column.name() + ", " + DATETIME_SQL_PATTERN + ") AS " + column.name();
            case DATE -> "to_char(" + column.name() + ", " + DATE_SQL_PATTERN + ") AS " + column.name();
            default -> column.name();
        };
    }

    private void appendFilter(List<String> where, List<Object> args, FilterCondition condition) {
        String column = condition.column().name();
        switch (condition.op()) {
            case IS_NULL -> where.add(column + " IS NULL");
            case IS_NOT_NULL -> where.add(column + " IS NOT NULL");
            case EQ -> { where.add(column + " = ?"); args.add(typedValue(condition)); }
            case NE -> { where.add(column + " <> ?"); args.add(typedValue(condition)); }
            case GT -> { where.add(column + " > ?"); args.add(typedValue(condition)); }
            case GTE -> { where.add(column + " >= ?"); args.add(typedValue(condition)); }
            case LT -> { where.add(column + " < ?"); args.add(typedValue(condition)); }
            case LTE -> { where.add(column + " <= ?"); args.add(typedValue(condition)); }
            case PREFIX -> { where.add(column + " LIKE ? ESCAPE '\\'"); args.add(escapeLike(condition.value()) + "%"); }
        }
    }

    private void appendKeyset(List<String> where, List<Object> args, CursorPosition position, boolean descending) {
        String operator = descending ? "<" : ">";
        if (position.values().size() == 1) {
            where.add(position.columns().get(0) + " " + operator + " ?");
            args.add(position.values().get(0));
            return;
        }
        String left = "(" + String.join(", ", position.columns()) + ")";
        String right = "(" + String.join(", ", position.columns().stream().map(c -> "?").toList()) + ")";
        where.add(left + " " + operator + " " + right);
        args.addAll(position.values());
    }

    private Object typedValue(FilterCondition condition) {
        String value = condition.value();
        if (condition.column().type() == ColumnType.BOOLEAN) {
            if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                throw fail(ResultCode.DB_BROWSE_PARAMETER_INVALID);
            }
            return Boolean.valueOf(value);
        }
        return coerce(value, condition.column().type());
    }

    /**
     * 把 HTTP/游标里的字符串转成 JDBC 能直接比较的类型。
     *
     * <p>时间必须转成 {@link Timestamp} / {@link Date}：PostgreSQL 不接受
     * {@code timestamp >= varchar}，直接把字符串交给 JDBC 会得到「操作符不存在」，
     * 而这条路径在 mock 掉数据访问层的单测里看不见。</p>
     */
    private Object coerce(Object raw, ColumnType type) {
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw);
        try {
            return switch (type) {
                case NUMBER -> text.contains(".") ? new BigDecimal(text) : Long.valueOf(text);
                case DATETIME -> Timestamp.valueOf(LocalDateTime.parse(text));
                case DATE -> java.sql.Date.valueOf(LocalDate.parse(text));
                case BOOLEAN -> Boolean.valueOf(text);
                default -> text;
            };
        } catch (RuntimeException e) {
            throw fail(ResultCode.DB_BROWSE_PARAMETER_INVALID);
        }
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /**
     * 只还原逗号编码，不做完整 URL 解码。
     *
     * <p>完整 decode 会把值里本来就有的 {@code +} 变成空格、把 {@code %} 当转义起点，
     * 反而搜不到原值。前端只需在值含逗号时写成 {@code %2C}。</p>
     */
    private static String decodeFilterValue(String value) {
        return value.replace("%2C", ",").replace("%2c", ",");
    }

    // ---------------------------------------------------------------- 脱敏

    private Map<String, Object> maskRow(DbBrowseTable table, Map<String, Object> raw, Set<String> truncated) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (DbBrowseColumn column : table.returnedColumns()) {
            Object value = raw.get(column.name());
            if (value == null) {
                row.put(column.name(), null);
                continue;
            }
            if (column.type() == ColumnType.TEXT || column.type() == ColumnType.JSON) {
                String text = String.valueOf(value);
                if (text.length() > column.maxLength()) {
                    text = text.substring(0, column.maxLength());
                    truncated.add(column.name());
                }
                row.put(column.name(), column.mask().apply(text));
                continue;
            }
            row.put(column.name(), value);
        }
        return row;
    }

    // ---------------------------------------------------------------- 游标

    private String encodeCursor(DbBrowseTable table, Map<String, Object> lastRow, List<String> columns,
                                boolean descending) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("k", table.key());
        payload.put("c", columns);
        // 方向随游标一起固化：同一游标在反向查询里必须被拒绝，而不是静默返回错的下一页。
        payload.put("d", descending ? "desc" : "asc");
        List<Object> values = new ArrayList<>(columns.size());
        for (String column : columns) {
            values.add(lastRow.get(column));
        }
        payload.put("v", values);
        try {
            byte[] json = objectMapper.writeValueAsBytes(payload);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (Exception e) {
            // 排序键无法序列化属于注册表配置问题，不应把底层信息暴露给客户端。
            logger.error("数据库浏览游标编码失败: table={}", table.key(), e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR.getCode(), "分页失败，请重新查询");
        }
    }

    private CursorPosition decodeCursor(String cursor, DbBrowseTable table, List<String> sortColumns,
                                        boolean descending) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            byte[] json = Base64.getUrlDecoder().decode(cursor.trim());
            JsonNode node = objectMapper.readTree(new String(json, StandardCharsets.UTF_8));
            if (!table.key().equals(node.path("k").asText())) {
                throw fail(ResultCode.DB_BROWSE_CURSOR_INVALID);
            }
            List<String> columns = new ArrayList<>();
            node.path("c").forEach(item -> columns.add(item.asText()));
            if (!columns.equals(sortColumns)) {
                throw fail(ResultCode.DB_BROWSE_CURSOR_INVALID);
            }
            String direction = node.path("d").asText();
            if (!(descending ? "desc" : "asc").equals(direction)) {
                throw fail(ResultCode.DB_BROWSE_CURSOR_INVALID);
            }
            JsonNode values = node.path("v");
            if (!values.isArray() || values.size() != columns.size()) {
                throw fail(ResultCode.DB_BROWSE_CURSOR_INVALID);
            }
            List<Object> typed = new ArrayList<>(columns.size());
            for (int i = 0; i < columns.size(); i++) {
                ColumnType type = table.column(columns.get(i))
                        .map(DbBrowseColumn::type)
                        .orElse(ColumnType.TEXT);
                typed.add(cursorValue(values.get(i), type));
            }
            return new CursorPosition(columns, typed);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw fail(ResultCode.DB_BROWSE_CURSOR_INVALID);
        }
    }

    /** 游标里的值必须能被转成排序列的真实类型，否则一律按游标无效处理。 */
    private Object cursorValue(JsonNode node, ColumnType type) {
        if (node == null || !node.isValueNode() || node.isNull()) {
            throw fail(ResultCode.DB_BROWSE_CURSOR_INVALID);
        }
        try {
            return coerce(node.asText(), type);
        } catch (BusinessException e) {
            throw fail(ResultCode.DB_BROWSE_CURSOR_INVALID);
        }
    }

    // ---------------------------------------------------------------- 其它

    private Long estimateRows(DbBrowseTable table) {
        return repository.estimateRows(table.schemaName(), table.tableName());
    }

    /** 拒绝路径要记录调用方传入的表标识，但它是不可信输入：清洗后再进日志，避免日志注入。 */
    private static String sanitizeTableKey(String tableKey) {
        if (tableKey == null || tableKey.isBlank()) {
            return "-";
        }
        String cleaned = tableKey.replaceAll("[^A-Za-z0-9_.-]", "_");
        return cleaned.length() > 40 ? cleaned.substring(0, 40) : cleaned;
    }

    private static String mapOutcome(Integer code) {        if (code == null) {
            return "ERROR";
        }
        return switch (code) {
            case 3001, 3007 -> "DENIED";
            case 3002, 3003, 3005, 3006 -> "BAD_REQUEST";
            case 3004 -> "TIMEOUT";
            default -> "ERROR";
        };
    }

    private static String filterDigest(List<FilterCondition> conditions) {
        if (conditions.isEmpty()) {
            return "-";
        }
        List<String> parts = new ArrayList<>(conditions.size());
        for (FilterCondition condition : conditions) {
            parts.add(condition.column().name() + ":" + condition.op().name());
        }
        return String.join(",", parts);
    }

    private DbBrowseTableSummary toSummary(DbBrowseTable table) {
        Long estimatedRows = table.estimateRowCount() ? estimateRows(table) : null;
        return new DbBrowseTableSummary(
                table.key(),
                table.label(),
                table.description(),
                table.personalData(),
                table.requireFilter(),
                estimatedRows,
                estimatedRows != null,
                table.returnedColumns().size());
    }

    private DbBrowseColumnView toColumnView(DbBrowseColumn column) {
        List<String> ops = new ArrayList<>();
        for (FilterOp op : FilterOp.values()) {
            if (column.allowedOps().contains(op)) {
                ops.add(op.name());
            }
        }
        return new DbBrowseColumnView(
                column.name(),
                column.label(),
                column.type().name(),
                column.filterable(),
                column.orderable(),
                column.mask().name(),
                ops);
    }

    private static BusinessException fail(ResultCode code) {
        return new BusinessException(code.getCode(), code.getMessage());
    }

    private record FilterCondition(DbBrowseColumn column, FilterOp op, String value) {
    }

    private record CursorPosition(List<String> columns, List<Object> values) {
    }

    private record SqlSpec(String sql, List<Object> args) {
    }
}
