package com.zjcxph.imgapi.utils;

import java.math.BigInteger;

/**
 * 病案号和上架号的原值清理与独立比较键。不得改写用于存储定位的编号。
 */
public final class MedicalRecordCodeUtils {

    public static final int CODE_LENGTH = 8;
    public static final BigInteger ARCHIVE_BAH_UNIQUE_LIMIT = BigInteger.valueOf(10_000_000L);

    private MedicalRecordCodeUtils() {
    }

    /**
     * 仅清理首尾空白，空值返回 null；保留原始长度和前导零。
     *
     * @param code 原始编码
     * @return 规范化编码；输入为 {@code null} 时返回 {@code null}
     */
    public static String normalize(String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 与 {@link #normalize(String)} 相同，但将 {@code null} 转为空字符串，便于接口参数处理。
     */
    public static String normalizeOrEmpty(String code) {
        String normalized = normalize(code);
        return normalized == null ? "" : normalized;
    }

    /**
     * 生成数据库搜索词。纯数字编码会去掉前导零，使“123”和“00000123”得到相同搜索词。
     */
    public static String toSearchTerm(String code) {
        if (code == null) {
            return "";
        }
        String trimmed = code.trim();
        if (trimmed.isEmpty() || !trimmed.matches("\\d+")) {
            return trimmed;
        }
        String withoutLeadingZeros = trimmed.replaceFirst("^0+", "");
        return withoutLeadingZeros.isEmpty() ? "0" : withoutLeadingZeros;
    }

    /**
     * 病案号从 10000000 开始不再保证唯一，查询时必须同时提供唯一上架号。
     */
    public static boolean requiresSjhForBah(String bah) {
        String searchTerm = toSearchTerm(bah);
        if (searchTerm.isBlank() || !searchTerm.matches("\\d+")) {
            return false;
        }
        return new BigInteger(searchTerm).compareTo(ARCHIVE_BAH_UNIQUE_LIMIT) >= 0;
    }

    /**
     * 判断值是否是可规范化的 1-8 位纯数字编码。
     */
    public static boolean isSupportedNumericCode(String code) {
        if (code == null) {
            return false;
        }
        String trimmed = code.trim();
        return trimmed.matches("\\d{1," + CODE_LENGTH + "}");
    }
}
