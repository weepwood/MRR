package com.zjcxph.imgapi.utils;

import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.UUID;
import java.util.WeakHashMap;

/** 同一事件在同步错误采集和异步文件编码中共享身份；弱引用不延长日志对象生命周期。 */
public final class RuntimeLogIdentity {
    private static final WeakHashMap<ILoggingEvent, Identity> EVENTS = new WeakHashMap<>();
    private RuntimeLogIdentity() {}
    public record Identity(UUID eventId, String errorId) {}
    public static synchronized Identity of(ILoggingEvent event) {
        return EVENTS.computeIfAbsent(event, key -> {
            String supplied = key.getMDCPropertyMap().get("errorId");
            String errorId = supplied != null && supplied.matches("[A-Za-z0-9_-]{1,32}")
                    ? supplied : (key.getLevel().isGreaterOrEqual(ch.qos.logback.classic.Level.WARN)
                    ? RuntimeErrorSanitizer.newErrorId() : null);
            return new Identity(UUID.randomUUID(), errorId);
        });
    }
}
