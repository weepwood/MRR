package com.zjcxph.imgapi.setting.browse;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 允许的过滤操作符。
 *
 * <p>这是白名单的最终形态：不在本枚举内的操作符（{@code REGEXP}、{@code ILIKE}、
 * {@code SIMILAR TO}、{@code IN}）无法通过任何入口表达。对比类操作符只对
 * 数值与时间列开放，前缀匹配由服务端转义通配符。</p>
 *
 * <p>有意不提供「包含」（前导通配）匹配：它无法走索引，且对掩码列等价于
 * 全文探测；需要按片段查找时由业务检索接口承担。</p>
 */
public enum FilterOp {

    EQ(true),
    NE(true),
    GT(true),
    GTE(true),
    LT(true),
    LTE(true),
    PREFIX(true),
    IS_NULL(false),
    IS_NOT_NULL(false);

    private static final Set<FilterOp> EQUALITY_OPS = Set.of(EQ, NE);
    private static final Set<FilterOp> RANGE_OPS = Set.of(GT, GTE, LT, LTE);

    private final boolean requiresValue;

    FilterOp(boolean requiresValue) {
        this.requiresValue = requiresValue;
    }

    public boolean requiresValue() {
        return requiresValue;
    }

    /** 比较类操作符只对数值与时间列开放。 */
    public boolean supports(ColumnType type) {
        return switch (this) {
            case GT, GTE, LT, LTE -> type == ColumnType.NUMBER
                    || type == ColumnType.DATETIME
                    || type == ColumnType.DATE;
            case PREFIX -> type == ColumnType.TEXT;
            case IS_NULL, IS_NOT_NULL -> type != ColumnType.BINARY;
            case EQ, NE -> type != ColumnType.BINARY && type != ColumnType.JSON;
        };
    }

    public static Optional<FilterOp> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * 某类型列的默认可过滤操作符。
     *
     * <p>数值与时间列默认带比较操作：排查问题几乎总是「某时间之后」「某编号大于」，
     * 只给等值会让这些列形同不可过滤。</p>
     */
    public static Set<FilterOp> defaultsFor(ColumnType type) {
        Set<FilterOp> defaults = new java.util.LinkedHashSet<>(EQUALITY_OPS);
        if (type == ColumnType.NUMBER || type == ColumnType.DATETIME || type == ColumnType.DATE) {
            defaults.addAll(RANGE_OPS);
        }
        return defaults.stream()
                .filter(op -> op.supports(type))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
