package com.zjcxph.imgapi.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public final class RuntimeErrorSanitizer {

    private static final int MAX_SUMMARY_LENGTH = 2_000;
    private static final int MAX_STACK_LENGTH = 16_000;
    /** 运行日志正文字符数上限；{@code app.runtime_log_event.message_summary} 为 VARCHAR(2100)。 */
    private static final int MAX_LOG_MESSAGE_LENGTH = 1_000;
    private static final Pattern BEARER_OR_JWT = Pattern.compile(
            "(?i)\\bBearer\\s+[A-Za-z0-9._~+/-]+=*|\\beyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}"
    );
    private static final Pattern SENSITIVE_ASSIGNMENT = Pattern.compile(
            "(?i)([\"']?(?:bah|sjh|brxh|name|patientName|idCard|phone|mobile|password|passwd|token|authorization|secret|signature|ticket|access[-_]?key|private[-_]?key)[\"']?"
                    + "\\s*[:=]\\s*)(\"[^\"]*\"|'[^']*'|[^,;\\s\\]}]+)"
    );
    private static final Pattern LONG_ID_NUMBER = Pattern.compile("(?<!\\d)\\d{15,18}[0-9Xx](?!\\d)");
    /**
     * 已知医疗敏感相对请求目标：完整隐藏动态路径及其查询串，覆盖编码后的参数。
     * 不匹配普通固定运维路由；绝对 URL 仍由既有协议规则处理。
     */
    private static final Pattern SENSITIVE_REQUEST_TARGET = Pattern.compile(
            "(?i)(?<![A-Za-z0-9_/])/(?:idcard/|api/v1/(?:search/getBAHByID/|patients/idcard/|img/content/)"
                    + "|(?:archive|api/v1/(?:search/(?:getBAHByEncryptID(?:Legacy)?|archive-cases|getBAHByID)"
                    + "|patients(?:/idcard)?))\\?)[^\\s\"<>]+"
    );
    private static final Pattern ERROR_ID = Pattern.compile("(?i)ERR-\\d{8}-[A-F0-9]{8}");
    private static final Pattern UUID_VALUE = Pattern.compile(
            "(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\b"
    );
    private static final Pattern VARIABLE_NUMBER = Pattern.compile("\\b\\d{2,}\\b");
    private static final Pattern VARIABLE_HEX = Pattern.compile("\\b[0-9a-fA-F]{16,}\\b");

    /**
     * 运行日志正文专用的赋值脱敏规则。
     *
     * <p>与 {@link #SENSITIVE_ASSIGNMENT} 的差别只有一处：值前面的负向断言让本方法已经写出的
     * 占位符（{@code [REDACTED]}、{@code [MASKED_NUMBER]} 等）跳过匹配。采集器会对同一行重复
     * 调用脱敏，没有这条断言时 {@code [REDACTED]} 会被再次截成 {@code [REDACTED]]} 并逐轮膨胀；
     * 而否定范围只限占位符本身，{@code name=[真实值]} 仍会被正常抹除。</p>
     */
    private static final Pattern LOG_SENSITIVE_ASSIGNMENT = Pattern.compile(
            "(?i)([\"']?(?:bah|sjh|brxh|name|patientName|idCard|phone|mobile|password|passwd|token"
                    + "|authorization|secret|signature|ticket|access[-_]?key|private[-_]?key)[\"']?\\s*[:=]\\s*)"
                    + "(?!\\[(?:REDACTED|MASKED_)[A-Z_]*\\])(\\[[^\\]]*\\]|\"[^\"]*\"|'[^']*'|[^,;\\s\\]}]+)"
    );
    /**
     * 15 位与 18 位身份证。
     *
     * <p>既有 {@link #LONG_ID_NUMBER} 要求 16 个字符以上，15 位旧证实际不会被命中；
     * 该方法只用于日志正文，独立修正以免改变错误中心的既有指纹与摘要。</p>
     */
    private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)(?:\\d{15}|\\d{17}[0-9Xx])(?!\\d)");
    private static final Pattern MOBILE_NUMBER = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    /** 任意协议的连接串（含 {@code jdbc:postgresql://}），用于覆盖凭据与内网地址。 */
    private static final Pattern ANY_SCHEME_URL = Pattern.compile("(?i)\\b[a-z][a-z0-9+.-]*://[^\\s\"<>]+");
    /**
     * Windows 绝对路径。
     *
     * <p>前缀限制为「前面不是字母数字」，避免把 {@code psql:/} 这类协议写法截成
     * {@code psq[REDACTED_PATH]}，同时不影响 {@code D:\\temp\\x}、{@code C:/data}。</p>
     */
    private static final Pattern ABSOLUTE_PATH = Pattern.compile("(?i)(?<![A-Za-z0-9])[a-z]:[\\\\/][^\\s\"<>]+");
    private static final Pattern UNC_PATH = Pattern.compile("(?i)\\\\\\\\[^\\s\"<>]+");
    /**
     * 独立出现的长数字（6 位以上）按编号掩码。
     *
     * <p>病历号、上架号、条码等标识没有可识别的固定格式，只能按「长度 + 独立出现」兜底；
     * 端口、耗时、行号等常见短数值不受影响，因此运维信息基本保留。</p>
     */
    private static final Pattern LONG_DIGIT_RUN = Pattern.compile("(?<!\\d)\\d{6,}(?!\\d)");
    private static final DateTimeFormatter ERROR_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private RuntimeErrorSanitizer() {
    }

    public static String sanitizeSummary(String value) {
        return truncate(sanitize(value), MAX_SUMMARY_LENGTH);
    }

    public static String sanitizeStackTrace(String value) {
        return truncate(sanitize(value), MAX_STACK_LENGTH);
    }

    /**
     * 运行日志正文脱敏：保留模板可读性，只就地抹掉可识别的敏感值。
     *
     * <p>ADR-0002 的约束是「message 使用模板，参数不直接序列化」，因此模板文本本身必须落库，
     * 否则运维拿到 {@code [MESSAGE OMITTED]} 无法判断任何故障。这里对自由文本逐类脱敏：
     * 令牌、敏感键值、身份证、手机号、连接串、绝对路径与长编号。</p>
     *
     * <p>本方法必须幂等：{@code PrivateJsonLogEncoder} 在写文件时调用一次，错误中心读取
     * 历史存量时可能再次调用。</p>
     */
    public static String sanitizeLogMessage(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String sanitized = SENSITIVE_REQUEST_TARGET.matcher(value).replaceAll("[REDACTED_PATH]");
        sanitized = BEARER_OR_JWT.matcher(sanitized).replaceAll("[REDACTED_TOKEN]");
        sanitized = LOG_SENSITIVE_ASSIGNMENT.matcher(sanitized).replaceAll("$1[REDACTED]");
        sanitized = ID_CARD.matcher(sanitized).replaceAll("[REDACTED_ID]");
        sanitized = MOBILE_NUMBER.matcher(sanitized).replaceAll("[REDACTED_PHONE]");
        sanitized = ANY_SCHEME_URL.matcher(sanitized).replaceAll("[REDACTED_URL]");
        sanitized = ABSOLUTE_PATH.matcher(sanitized).replaceAll("[REDACTED_PATH]");
        sanitized = UNC_PATH.matcher(sanitized).replaceAll("[REDACTED_PATH]");
        sanitized = LONG_DIGIT_RUN.matcher(sanitized).replaceAll("[MASKED_NUMBER]");
        return truncate(sanitized, MAX_LOG_MESSAGE_LENGTH);
    }

    public static String fingerprint(String level, String loggerName, String exceptionType, String message) {
        String normalizedMessage = ERROR_ID.matcher(nullToEmpty(message)).replaceAll("ERR-*");
        normalizedMessage = UUID_VALUE.matcher(normalizedMessage).replaceAll("UUID");
        normalizedMessage = VARIABLE_NUMBER.matcher(normalizedMessage).replaceAll("#");
        normalizedMessage = VARIABLE_HEX.matcher(normalizedMessage).replaceAll("#");
        String source = String.join("|",
                nullToEmpty(level).toUpperCase(Locale.ROOT),
                nullToEmpty(loggerName),
                nullToEmpty(exceptionType),
                normalizedMessage
        );
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public static String newErrorId() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        return "ERR-" + LocalDate.now().format(ERROR_DATE) + "-" + suffix;
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String sanitized = SENSITIVE_REQUEST_TARGET.matcher(value).replaceAll("[REDACTED_PATH]");
        sanitized = BEARER_OR_JWT.matcher(sanitized).replaceAll("[REDACTED_TOKEN]");
        sanitized = SENSITIVE_ASSIGNMENT.matcher(sanitized).replaceAll("$1[REDACTED]");
        sanitized = LONG_ID_NUMBER.matcher(sanitized).replaceAll("[REDACTED_ID]");
        sanitized = sanitized.replaceAll("(?i)https?://[^\\s\"<>]+", "[REDACTED_URL]");
        sanitized = sanitized.replaceAll("(?i)[a-z]:[\\\\/][^\\r\\n\"<>]+", "[REDACTED_PATH]");
        return sanitized;
    }

    private static String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...[TRUNCATED]";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
