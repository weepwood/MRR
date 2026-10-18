package com.zjcxph.imgapi.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.encoder.EncoderBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.utils.RuntimeErrorSanitizer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;

/** 不序列化日志参数或 Throwable 消息，防止医疗字段通过日志副本传播。 */
public class PrivateJsonLogEncoder extends EncoderBase<ILoggingEvent> {
    private static final ObjectMapper JSON = new ObjectMapper();
    public byte[] headerBytes() { return null; }
    public byte[] footerBytes() { return null; }
    public byte[] encode(ILoggingEvent event) {
        if (com.zjcxph.imgapi.utils.RuntimeLogContext.ingesting()) {
            com.zjcxph.imgapi.utils.RuntimeLogContext.SUPPRESSED.incrementAndGet();
            return new byte[0];
        }
        try {
            var row = new LinkedHashMap<String, Object>();
            var identity = com.zjcxph.imgapi.utils.RuntimeLogIdentity.of(event);
            row.put("event_id", identity.eventId().toString());
            if (identity.errorId() != null) row.put("errorId", identity.errorId());
            row.put("privacy_version", 1);
            row.put("@timestamp", Instant.ofEpochMilli(event.getTimeStamp()).toString());
            row.put("level", event.getLevel().toString());
            row.put("logger_name", event.getLoggerName());
            row.put("thread_name", event.getThreadName());
            row.put("message", safeMessage(event.getMessage()));
            // 模板占位符不展开；所有任意字符串参数均留在原业务边界内。
            row.put("argument_count", event.getArgumentArray() == null ? 0 : event.getArgumentArray().length);
            for (String key : new String[]{"requestId"}) {
                String value = event.getMDCPropertyMap().get(key);
                if (value != null && value.matches("[A-Za-z0-9_-]{1,80}")) row.put(key, value);
            }
            row.put("stack_trace", safeStack(event.getThrowableProxy()));
            return (JSON.writeValueAsString(row) + "\n").getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "{\"level\":\"ERROR\",\"message\":\"LOG_ENCODING_FAILED\"}\n".getBytes(StandardCharsets.UTF_8);
        }
    }

    public static String safeStack(IThrowableProxy proxy) {
        StringBuilder result = new StringBuilder();
        int depth = 0;
        while (proxy != null && depth++ < 8) {
            result.append(proxy.getClassName()).append('\n');
            var frames = proxy.getStackTraceElementProxyArray();
            if (frames != null) for (int i = 0; i < Math.min(frames.length, 80); i++) result.append(frames[i]).append('\n');
            proxy = proxy.getCause();
        }
        return RuntimeErrorSanitizer.sanitizeStackTrace(result.toString());
    }

    public static String safeStoredStack(String stack) {
        if (stack == null) return "";
        return stack.lines().filter(line -> line.matches("[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)+")
                || line.matches("\\s*at [A-Za-z0-9_.$/@<>-]+\\((?:[A-Za-z0-9_$.-]+\\.java(?::[0-9]+)?|Unknown Source|Native Method)\\)"))
                .limit(640).collect(java.util.stream.Collectors.joining("\n"));
    }

    public static String safeMessage(String message) {
        // getMessage 也可能是拼接字符串；仅允许已审核的固定模板，其他正文默认隐藏。
        return java.util.Set.of("Token revocation store unavailable", "Token revoked: jti={}",
                "Authentication user store unavailable: userId={}", "Unable to revoke current token",
                "Query failed for {}", "LOG_ENCODING_FAILED").contains(message == null ? "" : message)
                ? message : "[MESSAGE OMITTED]";
    }
}
