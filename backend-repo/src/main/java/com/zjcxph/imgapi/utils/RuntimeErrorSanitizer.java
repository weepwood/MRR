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
    private static final Pattern BEARER_OR_JWT = Pattern.compile(
            "(?i)\\bBearer\\s+[A-Za-z0-9._~+/-]+=*|\\beyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}"
    );
    private static final Pattern SENSITIVE_ASSIGNMENT = Pattern.compile(
            "(?i)([\"']?(?:password|passwd|token|authorization|secret|signature|ticket|access[-_]?key|private[-_]?key)[\"']?"
                    + "\\s*[:=]\\s*)(\"[^\"]*\"|'[^']*'|[^,;\\s\\]}]+)"
    );
    private static final Pattern LONG_ID_NUMBER = Pattern.compile("(?<!\\d)\\d{15,18}[0-9Xx](?!\\d)");
    private static final Pattern ERROR_ID = Pattern.compile("(?i)ERR-\\d{8}-[A-F0-9]{8}");
    private static final Pattern UUID_VALUE = Pattern.compile(
            "(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\b"
    );
    private static final Pattern VARIABLE_NUMBER = Pattern.compile("\\b\\d{2,}\\b");
    private static final Pattern VARIABLE_HEX = Pattern.compile("\\b[0-9a-fA-F]{16,}\\b");
    private static final DateTimeFormatter ERROR_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private RuntimeErrorSanitizer() {
    }

    public static String sanitizeSummary(String value) {
        return truncate(sanitize(value), MAX_SUMMARY_LENGTH);
    }

    public static String sanitizeStackTrace(String value) {
        return truncate(sanitize(value), MAX_STACK_LENGTH);
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
        String sanitized = BEARER_OR_JWT.matcher(value).replaceAll("[REDACTED_TOKEN]");
        sanitized = SENSITIVE_ASSIGNMENT.matcher(sanitized).replaceAll("$1[REDACTED]");
        sanitized = LONG_ID_NUMBER.matcher(sanitized).replaceAll("[REDACTED_ID]");
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
