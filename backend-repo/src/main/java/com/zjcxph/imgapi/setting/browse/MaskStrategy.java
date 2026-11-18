package com.zjcxph.imgapi.setting.browse;

/**
 * 列级脱敏策略。
 *
 * <p>所有掩码必须在服务端完成，前端只负责展示；前端拿不到原值。
 * {@link #FULL} 的列连列名都不会出现在响应中，只计入 {@code omittedColumnCount}，
 * 否则等于向调用方泄露「系统存在 password_hash 这一列」。</p>
 */
public enum MaskStrategy {

    /** 原值返回。 */
    NONE,

    /** 整列不返回。 */
    FULL,

    /** 保留首字符，其余替换为 {@code *}。用于姓名。 */
    NAME,

    /** 保留前 6 后 4。长度不足 18 时全掩。用于身份证号。 */
    ID_CARD,

    /** 保留后 4 位。长度不足 5 时全掩。用于病案号、上架号。 */
    CODE,

    /** 保留前 3 后 4。长度不足 7 时全掩。用于联系电话。 */
    PHONE,

    /** 只保留文件名，去掉目录部分。 */
    PATH;

    private static final String MASK = "*";

    /** 该策略是否要求整列不出现在响应结构里。 */
    public boolean hidesColumn() {
        return this == FULL;
    }

    /**
     * 对单个值应用掩码。
     *
     * @param raw 原始值，允许 {@code null}
     * @return 掩码后的值；{@link #FULL} 恒返回 {@code null}
     */
    public String apply(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (this) {
            case NONE -> raw;
            case FULL -> null;
            case NAME -> keepHead(raw, 1);
            case ID_CARD -> raw.length() >= 18 ? keepHeadAndTail(raw, 6, 4) : allMask(raw.length());
            case CODE -> raw.length() <= 4
                    ? allMask(raw.length())
                    : allMask(raw.length() - 4) + raw.substring(raw.length() - 4);
            case PHONE -> raw.length() >= 7 ? keepHeadAndTail(raw, 3, 4) : allMask(raw.length());
            case PATH -> fileName(raw);
        };
    }

    private static String keepHead(String raw, int head) {
        if (raw.length() <= head) {
            return allMask(raw.length());
        }
        return raw.substring(0, head) + MASK.repeat(raw.length() - head);
    }

    private static String keepHeadAndTail(String raw, int head, int tail) {
        if (raw.length() <= head + tail) {
            return allMask(raw.length());
        }
        return raw.substring(0, head)
                + MASK.repeat(raw.length() - head - tail)
                + raw.substring(raw.length() - tail);
    }

    private static String allMask(int length) {
        return MASK.repeat(Math.max(length, 0));
    }

    private static String fileName(String raw) {
        int slash = Math.max(raw.lastIndexOf('/'), raw.lastIndexOf('\\'));
        return slash >= 0 ? raw.substring(slash + 1) : raw;
    }
}
