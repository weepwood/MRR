package com.zjcxph.imgapi.utils;

import java.math.BigInteger;

/**
 * 病案号（BAH）和上架号（SJH）的统一格式化工具。
 *
 * <p>纯数字编码不足 8 位时在左侧补零；已经是 8 位、超过 8 位或包含非数字字符的值
 * 仅做首尾空白清理，避免静默截断或改写异常历史数据。</p>
 */
public final class MedicalRecordCodeUtils {

    public static final int CODE_LENGTH = 8;
    public static final BigInteger ARCHIVE_BAH_UNIQUE_LIMIT = BigInteger.valueOf(10_000_000L);

    private MedicalRecordCodeUtils() {
    }

    /**
     * 将纯数字编码规范化为 8 位。
     *
     * @param code 原始编码
     * @return 规范化编码；输入为 {@code null} 时返回 {@code null}
     */
    public static String normalize(String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.trim();
        if (!trimmed.isEmpty() && trimmed.length() < CODE_LENGTH && trimmed.matches("\\d+")) {
            return "0".repeat(CODE_LENGTH - trimmed.length()) + trimmed;
        }
        return trimmed;
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
