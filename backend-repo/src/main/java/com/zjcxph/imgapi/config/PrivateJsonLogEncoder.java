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
            row.put("message", safeMessage(event.getLoggerName(), event.getMessage(),
                    event.getArgumentArray() != null && event.getArgumentArray().length > 0));
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

    /** 历史数据与降级路径使用的占位符；再次脱敏时必须原样保留。 */
    public static final String OMITTED = "[MESSAGE OMITTED]";

    /**
     * 正文可信的框架 logger 前缀。
     *
     * <p>框架日志由 Spring、Tomcat、连接池、驱动等组件产生，不含病案标识，且绝大多数是
     * 无参数的固定描述（例如 {@code Tomcat initialized with port 18045 (http)}）。
     * 这些正是「启动为什么失败」最直接的线索，必须可读。</p>
     */
    private static final java.util.List<String> FRAMEWORK_LOGGER_PREFIXES = java.util.List.of(
            "org.springframework", "org.apache", "org.hibernate", "org.mybatis", "org.flywaydb",
            "com.zaxxer", "com.fasterxml", "ch.qos.logback", "io.lettuce", "io.micrometer",
            "io.netty", "reactor.", "jakarta.", "javax.", "java."
    );

    /**
     * 日志正文准入与清洗。
     *
     * <p>此前实现只放行 6 条硬编码模板，其余一律替换成 {@link #OMITTED}。实测日志文件
     * 372/372 条全部被抹除（含 Spring Boot 启动日志与全部业务模板），运行日志页只能看到占位符，
     * 失去排障价值。ADR-0002 的真实约束是「message 使用模板，参数不直接序列化」——
     * 参数只计入 {@code argument_count} 而不落正文，因此模板本身应当保留。</p>
     *
     * <p>放行条件取两者之一，保持「未知自由文本默认隐藏」这一既有安全契约：</p>
     * <ol>
     *   <li>{@code templated}：调用点传了参数，说明 message 是 SLF4J 模板，业务值在参数里；</li>
     *   <li>框架 logger：正文由框架产生，不含业务字段。</li>
     * </ol>
     * <p>业务包内不带参数的自由文本仍整体隐藏，避免拼接式调用把敏感值带进正文。</p>
     */
    public static String safeMessage(String loggerName, String message, boolean templated) {
        if (message == null || message.isEmpty()) {
            return "";
        }
        // 已脱敏占位符必须原样返回，否则历史补录会把它当成新的自由文本反复处理。
        if (OMITTED.equals(message)) {
            return OMITTED;
        }
        return templated || isFrameworkLogger(loggerName) ? RuntimeErrorSanitizer.sanitizeLogMessage(message) : OMITTED;
    }

    /**
     * 已落盘正文的二次处理：只做幂等脱敏，不再重新判定准入。
     *
     * <p>文件内 {@code privacy_version=1} 的记录在写入时已通过 {@link #safeMessage} 准入，
     * 采集器无需（也无法）重新获知参数与 logger 之外的上下文；重复判定只会把已放行的
     * 业务模板再次误判为自由文本。</p>
     */
    public static String safeStoredMessage(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }
        return OMITTED.equals(message) ? OMITTED : RuntimeErrorSanitizer.sanitizeLogMessage(message);
    }

    private static boolean isFrameworkLogger(String loggerName) {
        if (loggerName == null || loggerName.isEmpty()) {
            return false;
        }
        for (String prefix : FRAMEWORK_LOGGER_PREFIXES) {
            if (loggerName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
