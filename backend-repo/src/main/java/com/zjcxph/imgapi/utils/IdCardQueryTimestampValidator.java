package com.zjcxph.imgapi.utils;

/**
 * 加密身份证查询的时间戳新鲜度校验。
 *
 * <p>历史缺陷：{@code GET /api/v1/search/getBAHByEncryptID} 收到的 {@code timestamp}
 * 只作为密钥派生材料参与 KDF，全链路没有任何新鲜度校验。因此同一段密文可以被无限期重放，
 * 与 PRD「防篡改：加密 ID 附带时间戳校验」的描述不符。</p>
 *
 * <p>本类只做窗口校验，不参与密钥派生，因此不会改变 {@code AESUtil} 的输入，
 * 对外部调用方的加密实现零影响——调用方只要保证时间戳是「发起请求时的时间」即可。</p>
 */
public final class IdCardQueryTimestampValidator {

    /** 默认容差（秒）。与外部集成票据链路的 {@code timestamp-tolerance-seconds} 保持一致。 */
    public static final int DEFAULT_TOLERANCE_SECONDS = 300;

    /** 大于该阈值视为毫秒时间戳，与外部票据链路的判定方式一致。 */
    private static final long MILLIS_THRESHOLD = 10_000_000_000L;

    private IdCardQueryTimestampValidator() {
    }

    /**
     * 校验时间戳是否落在允许窗口内。
     *
     * @param timestamp       调用方传入的原始时间戳（秒或毫秒）
     * @param toleranceSeconds 允许的偏差秒数；小于等于 0 表示关闭校验（运维逃生阀）
     * @param nowEpochSecond  当前时间（秒），由调用方传入以便测试
     * @throws IllegalArgumentException 时间戳格式无效或已超出窗口
     */
    public static void validate(String timestamp, int toleranceSeconds, long nowEpochSecond) {
        if (toleranceSeconds <= 0) {
            return;
        }
        long requestEpochSecond = parseEpochSeconds(timestamp);
        long difference = Math.abs(nowEpochSecond - requestEpochSecond);
        if (difference > toleranceSeconds) {
            throw new IllegalArgumentException("加密身份证查询时间戳已过期");
        }
    }

    /**
     * 将时间戳解析为秒级 Unix 时间。
     *
     * @param timestamp 原始时间戳
     * @return 秒级 Unix 时间
     * @throws IllegalArgumentException 为空或非数字时
     */
    public static long parseEpochSeconds(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            throw new IllegalArgumentException("加密身份证查询缺少时间戳");
        }
        long value;
        try {
            value = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("加密身份证查询时间戳格式无效");
        }
        return value > MILLIS_THRESHOLD ? value / 1000L : value;
    }
}
