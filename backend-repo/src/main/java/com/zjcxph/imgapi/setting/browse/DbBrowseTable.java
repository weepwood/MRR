package com.zjcxph.imgapi.setting.browse;

import com.zjcxph.imgapi.common.Permissions;
import lombok.Builder;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 一张可浏览表的定义。这是可见性的唯一来源：未登记的表、未声明的列一律不可见。
 *
 * <p>{@code key} 是对外标识而非物理表名，物理表名只在服务端出现。
 * 构造即校验，非法定义直接抛 {@link IllegalArgumentException}，
 * 由 {@link DbBrowseRegistry} 捕获后禁用该表并告警，不阻断应用启动。</p>
 */
@Builder
public record DbBrowseTable(
        String key,
        String schemaName,
        String tableName,
        String label,
        String description,
        boolean enabled,
        List<String> requiredPermissions,
        List<String> keyColumns,
        Set<String> orderableColumns,
        int maxPageSize,
        boolean requireFilter,
        boolean estimateRowCount,
        boolean personalData,
        List<DbBrowseColumn> columns
) {

    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{2,40}$");
    private static final Pattern TABLE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{2,60}$");
    private static final String ALLOWED_SCHEMA = "app";
    private static final int HARD_MAX_PAGE_SIZE = 200;

    public DbBrowseTable {
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException("非法表标识: " + key);
        }
        if (!ALLOWED_SCHEMA.equals(schemaName)) {
            throw new IllegalArgumentException("只允许浏览 " + ALLOWED_SCHEMA + " 模式: " + schemaName);
        }
        if (tableName == null || !TABLE_PATTERN.matcher(tableName).matches()) {
            throw new IllegalArgumentException("非法物理表名: " + tableName);
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("表标签不能为空: " + key);
        }
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("未声明任何列: " + key);
        }
        if (maxPageSize <= 0 || maxPageSize > HARD_MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("页大小上限必须在 1.." + HARD_MAX_PAGE_SIZE + ": " + key);
        }

        requiredPermissions = requiredPermissions == null ? List.of() : List.copyOf(requiredPermissions);
        for (String permission : requiredPermissions) {
            if (!Permissions.ALL_PERMISSIONS.contains(permission)) {
                throw new IllegalArgumentException("未登记的权限代码: " + permission + "（表 " + key + "）");
            }
        }

        Map<String, DbBrowseColumn> byName = new LinkedHashMap<>();
        for (DbBrowseColumn column : columns) {
            if (byName.putIfAbsent(column.name(), column) != null) {
                throw new IllegalArgumentException("重复列声明: " + column.name() + "（表 " + key + "）");
            }
        }
        columns = List.copyOf(columns);

        if (keyColumns == null || keyColumns.isEmpty()) {
            throw new IllegalArgumentException("必须声明 keyColumns: " + key);
        }
        keyColumns = List.copyOf(keyColumns);
        Set<String> columnNames = new HashSet<>(byName.keySet());
        for (String keyColumn : keyColumns) {
            if (!columnNames.contains(keyColumn)) {
                throw new IllegalArgumentException("keyColumn 未在列声明中登记: " + keyColumn + "（表 " + key + "）");
            }
        }

        orderableColumns = orderableColumns == null ? Set.of() : Set.copyOf(orderableColumns);
        for (String orderable : orderableColumns) {
            if (!columnNames.contains(orderable)) {
                throw new IllegalArgumentException("排序列未登记: " + orderable + "（表 " + key + "）");
            }
        }
        // 阶段一限制：排序键必须是稳定的 keyColumns，保证游标翻页不丢行不重复。
        for (String keyColumn : keyColumns) {
            if (!orderableColumns.contains(keyColumn)) {
                throw new IllegalArgumentException("keyColumn 必须同时可排序: " + keyColumn + "（表 " + key + "）");
            }
        }
        // 游标是 Base64 的明文位置标记，排序键若整列隐藏，等于把该列原值带进响应；
        // ORDER BY 也不应作用在调用方永远看不到的列上。
        for (String sortColumn : orderableColumns) {
            if (!byName.get(sortColumn).returned()) {
                throw new IllegalArgumentException("排序列必须可见: " + sortColumn + "（表 " + key + "）");
            }
        }

        boolean hasReturnedColumn = columns.stream().anyMatch(DbBrowseColumn::returned);
        if (!hasReturnedColumn) {
            throw new IllegalArgumentException("至少需要一个可见列: " + key);
        }
    }

    public Optional<DbBrowseColumn> column(String name) {
        return columns.stream().filter(column -> column.name().equals(name)).findFirst();
    }

    public List<DbBrowseColumn> returnedColumns() {
        return columns.stream().filter(DbBrowseColumn::returned).toList();
    }

    /** 因 {@link MaskStrategy#FULL} 或声明不可见而不出现在响应里的列数。 */
    public int omittedColumnCount() {
        return (int) columns.stream().filter(column -> !column.returned()).count();
    }

    public Set<String> filterableColumnNames() {
        return columns.stream()
                .filter(column -> column.filterable() && column.returned())
                .map(DbBrowseColumn::name)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
