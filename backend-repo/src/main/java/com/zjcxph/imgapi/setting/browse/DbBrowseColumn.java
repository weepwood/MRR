package com.zjcxph.imgapi.setting.browse;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 单个可浏览列的定义。
 *
 * <p>物理列名只允许小写字母、数字与下划线，且必须由注册表声明；不接受来自 HTTP 的列名。
 * {@link ColumnType#BINARY} 列一律不可见、不可过滤，避免把影像二进制内容拖进响应。</p>
 */
public record DbBrowseColumn(
        String name,
        String label,
        ColumnType type,
        MaskStrategy mask,
        boolean visible,
        boolean filterable,
        boolean orderable,
        int maxLength,
        Set<FilterOp> allowedOps
) {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{0,60}$");
    private static final int DEFAULT_MAX_LENGTH = 512;
    private static final int MAX_MAX_LENGTH = 4096;

    public DbBrowseColumn {
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("非法列名: " + name);
        }
        Objects.requireNonNull(label, "列标签不能为空: " + name);
        Objects.requireNonNull(type, "列类型不能为空: " + name);
        mask = mask == null ? MaskStrategy.NONE : mask;
        if (type == ColumnType.BINARY) {
            visible = false;
            filterable = false;
            orderable = false;
        }
        if (maxLength <= 0) {
            maxLength = DEFAULT_MAX_LENGTH;
        }
        if (maxLength > MAX_MAX_LENGTH) {
            throw new IllegalArgumentException("截断长度超出上限: " + name);
        }
        if (allowedOps == null || allowedOps.isEmpty()) {
            allowedOps = FilterOp.defaultsFor(type);
        }
        // 未声明可见的列没有过滤/排序意义，避免注册表写出「隐藏但可查」的矛盾配置。
        if (!visible) {
            filterable = false;
            orderable = false;
        }
        allowedOps = allowedOps.stream()
                .filter(op -> op.supports(type))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        // 掩码只在返回前生效，过滤条件仍按库内原值匹配：允许前缀/包含匹配等于开放
        // 「逐字符逼近原值」的旁路，掩码就失去意义。掩码列只保留等值类匹配。
        if (mask != MaskStrategy.NONE) {
            for (FilterOp op : allowedOps) {
                if (op == FilterOp.PREFIX) {
                    throw new IllegalArgumentException("掩码列不允许前缀/包含过滤: " + name + "（" + mask + "）");
                }
            }
        }
    }

    /** 原样返回的普通文本列。 */
    public static DbBrowseColumn text(String name, String label, boolean filterable, boolean orderable) {
        return new DbBrowseColumn(name, label, ColumnType.TEXT, MaskStrategy.NONE,
                true, filterable, orderable, DEFAULT_MAX_LENGTH, null);
    }

    /** 原样返回的数值列。 */
    public static DbBrowseColumn number(String name, String label, boolean filterable, boolean orderable) {
        return new DbBrowseColumn(name, label, ColumnType.NUMBER, MaskStrategy.NONE,
                true, filterable, orderable, DEFAULT_MAX_LENGTH, null);
    }

    /** 带掩码的文本列。 */
    public static DbBrowseColumn masked(String name, String label, MaskStrategy mask,
                                        boolean filterable, boolean orderable) {
        return new DbBrowseColumn(name, label, ColumnType.TEXT, mask,
                true, filterable, orderable, DEFAULT_MAX_LENGTH, null);
    }

    /** 带掩码且显式声明操作符的文本列（例如病案号允许前缀匹配）。 */
    public static DbBrowseColumn masked(String name, String label, MaskStrategy mask,
                                        boolean filterable, boolean orderable, Set<FilterOp> allowedOps) {
        return new DbBrowseColumn(name, label, ColumnType.TEXT, mask,
                true, filterable, orderable, DEFAULT_MAX_LENGTH, allowedOps);
    }

    /** 时间列，默认开放等值与范围过滤（排查时按时间段找记录是主要用法）。 */
    public static DbBrowseColumn dateTime(String name, String label, boolean filterable) {
        return new DbBrowseColumn(name, label, ColumnType.DATETIME, MaskStrategy.NONE,
                true, filterable, false, DEFAULT_MAX_LENGTH,
                Set.of(FilterOp.EQ, FilterOp.NE, FilterOp.GT, FilterOp.GTE, FilterOp.LT, FilterOp.LTE));
    }

    /**
     * 纯日期列（无时分秒）。
     *
     * <p>与 {@link #dateTime} 的区别只在 SQL 侧格式化模板：日期列用
     * {@code YYYY-MM-DD}，否则 {@code to_char} 会把 {@code date} 补成
     * {@code T00:00:00}，让「出院日期」这类字段看起来带了错误的时间。</p>
     */
    public static DbBrowseColumn date(String name, String label, boolean filterable) {
        return new DbBrowseColumn(name, label, ColumnType.DATE, MaskStrategy.NONE,
                true, filterable, false, DEFAULT_MAX_LENGTH,
                Set.of(FilterOp.EQ, FilterOp.NE, FilterOp.GT, FilterOp.GTE, FilterOp.LT, FilterOp.LTE));
    }

    /** 布尔列。 */
    public static DbBrowseColumn bool(String name, String label) {
        return new DbBrowseColumn(name, label, ColumnType.BOOLEAN, MaskStrategy.NONE,
                true, true, false, DEFAULT_MAX_LENGTH, null);
    }

    /** 长文本列，超出长度在服务端截断。 */
    public static DbBrowseColumn text(String name, String label, boolean filterable,
                                      boolean orderable, int maxLength) {
        return new DbBrowseColumn(name, label, ColumnType.TEXT, MaskStrategy.NONE,
                true, filterable, orderable, maxLength, null);
    }

    /** 整列不返回：列名也不会出现在响应里。 */
    public static DbBrowseColumn hidden(String name, String label, ColumnType type) {
        return new DbBrowseColumn(name, label, type, MaskStrategy.FULL,
                false, false, false, DEFAULT_MAX_LENGTH, null);
    }

    /** 是否真正出现在响应里：声明可见且未被整列掩码。 */
    public boolean returned() {
        return visible && !mask.hidesColumn();
    }
}
