package com.zjcxph.imgapi.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import com.zjcxph.imgapi.dto.resp.SystemErrorOverviewDTO;
import com.zjcxph.imgapi.entity.SystemErrorEvent;
import com.zjcxph.imgapi.mapper.SystemErrorEventMapper;
import com.zjcxph.imgapi.utils.RuntimeErrorSanitizer;
import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class SystemErrorEventService {

    private static final int MAX_QUEUE_SIZE = 5_000;
    private static final int MAX_FLUSH_SIZE = 250;
    private static final Set<String> ALLOWED_LEVELS = Set.of("WARN", "ERROR");
    private static final Set<String> ALLOWED_STATUSES = Set.of("OPEN", "ACKNOWLEDGED", "RESOLVED");
    private static final String SELF_LOGGER_PREFIX = SystemErrorEventService.class.getName();

    private final SystemErrorEventMapper mapper;
    private final BlockingQueue<SystemErrorEvent> pending = new ArrayBlockingQueue<>(MAX_QUEUE_SIZE);
    private final AtomicLong droppedEvents = new AtomicLong();
    private final ThreadLocal<Boolean> persistenceInProgress = new ThreadLocal<>();
    private volatile boolean accepting = true;

    public SystemErrorEventService(SystemErrorEventMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 日志线程只做脱敏、指纹计算和入队，不在业务线程同步访问数据库。
     */
    public void capture(ILoggingEvent loggingEvent) {
        if (!accepting
                || Boolean.TRUE.equals(persistenceInProgress.get())
                || loggingEvent == null
                || loggingEvent.getLevel().toInt() < Level.WARN_INT) {
            return;
        }
        String loggerName = loggingEvent.getLoggerName();
        if (loggerName != null && loggerName.startsWith(SELF_LOGGER_PREFIX)) {
            return;
        }
        String formattedMessage = com.zjcxph.imgapi.config.PrivateJsonLogEncoder.safeMessage(loggingEvent.getMessage());
        if (loggerName != null
                && loggerName.endsWith("GlobalExceptionHandler")
                && formattedMessage != null
                && (formattedMessage.startsWith("业务异常") || formattedMessage.startsWith("客户端输入异常"))) {
            return;
        }

        IThrowableProxy throwableProxy = loggingEvent.getThrowableProxy();
        String exceptionType = throwableProxy == null ? null : throwableProxy.getClassName();
        String rawStackTrace = com.zjcxph.imgapi.config.PrivateJsonLogEncoder.safeStack(throwableProxy);
        String summary = RuntimeErrorSanitizer.sanitizeSummary(
                formattedMessage == null || formattedMessage.isBlank()
                        ? (exceptionType == null ? "未提供错误消息" : exceptionType)
                        : formattedMessage
        );

        SystemErrorEvent event = new SystemErrorEvent();
        var identity = com.zjcxph.imgapi.utils.RuntimeLogIdentity.of(loggingEvent);
        event.setProjectionEventId(identity.eventId().toString());
        event.setErrorId(identity.errorId());
        event.setLevel(loggingEvent.getLevel().toString());
        event.setModule(resolveModule(loggerName));
        event.setLoggerName(loggerName == null ? "unknown" : loggerName);
        event.setExceptionType(exceptionType);
        event.setMessageSummary(summary);
        event.setStackTrace(RuntimeErrorSanitizer.sanitizeStackTrace(rawStackTrace));
        event.setRequestId(loggingEvent.getMDCPropertyMap().get("requestId"));
        event.setThreadName(loggingEvent.getThreadName());
        LocalDateTime occurredAt = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(loggingEvent.getTimeStamp()), java.time.ZoneId.systemDefault());
        event.setFirstSeenAt(occurredAt);
        event.setLastSeenAt(occurredAt);
        event.setFingerprint(RuntimeErrorSanitizer.fingerprint(
                event.getLevel(), event.getLoggerName(), event.getExceptionType(), event.getMessageSummary()
        ));
        if (!pending.offer(event)) {
            droppedEvents.incrementAndGet();
        }
    }

    @Scheduled(fixedDelayString = "${app.runtime-errors.flush-interval-ms:2000}")
    public void flushPending() {
        if (Boolean.TRUE.equals(persistenceInProgress.get())) {
            return;
        }
        persistenceInProgress.set(true);
        try {
            long dropped = droppedEvents.getAndSet(0);
            if (dropped > 0) {
                System.err.println("运行错误事件队列已满，丢弃事件数量: " + dropped);
            }

            List<SystemErrorEvent> batch = new ArrayList<>(MAX_FLUSH_SIZE);
            pending.drainTo(batch, MAX_FLUSH_SIZE);
            for (int index = 0; index < batch.size(); index++) {
                try {
                    mapper.upsert(batch.get(index));
                } catch (RuntimeException exception) {
                    // 失败重试共享收据；数据库故障不应静默消耗内存队列。
                    for (int remaining = index; remaining < batch.size(); remaining++) {
                        if (!pending.offer(batch.get(remaining))) droppedEvents.incrementAndGet();
                    }
                    // 不输出异常消息，避免数据库连接串或配置细节进入控制台。
                    System.err.println("运行错误事件持久化失败: " + exception.getClass().getSimpleName());
                    break; // 本轮停止，避免数据库中断时逐条等待连接超时。
                }
            }
        } finally {
            persistenceInProgress.remove();
        }
    }

    public List<SystemErrorEvent> search(
            String keyword,
            String level,
            String status,
            String module,
            int page,
            int size
    ) {
        List<SystemErrorEvent> events = mapper.search(
                normalize(keyword),
                normalizeAllowed(level, ALLOWED_LEVELS, "日志级别"),
                normalizeAllowed(status, ALLOWED_STATUSES, "处理状态"),
                normalize(module),
                size,
                (page - 1) * size
        );
        events.forEach(this::privateRead);
        return events;
    }

    public long count(String keyword, String level, String status, String module) {
        return mapper.count(
                normalize(keyword),
                normalizeAllowed(level, ALLOWED_LEVELS, "日志级别"),
                normalizeAllowed(status, ALLOWED_STATUSES, "处理状态"),
                normalize(module)
        );
    }

    public SystemErrorEvent findById(long id) {
        return privateRead(mapper.findById(id));
    }

    private SystemErrorEvent privateRead(SystemErrorEvent event) {
        if (event == null) return null;
        event.setMessageSummary(com.zjcxph.imgapi.config.PrivateJsonLogEncoder.safeMessage(event.getMessageSummary()));
        event.setStackTrace(com.zjcxph.imgapi.config.PrivateJsonLogEncoder.safeStoredStack(event.getStackTrace()));
        event.setThreadName("[HIDDEN]");
        return event;
    }

    public SystemErrorOverviewDTO overview() {
        return mapper.overview();
    }

    public boolean updateStatus(long id, String status, String username) {
        String normalizedStatus = normalizeAllowed(status, ALLOWED_STATUSES, "处理状态");
        return mapper.updateStatus(id, normalizedStatus, normalize(username)) > 0;
    }

    @PreDestroy
    public void destroy() {
        accepting = false;
        // 关闭线程不发起数据库I/O；已产生的脱敏文件是恢复来源。
        if (!pending.isEmpty()) System.err.println("运行错误事件未确认数量: " + pending.size() + "；可从脱敏日志文件恢复");
    }

    private String resolveModule(String loggerName) {
        if (loggerName == null || loggerName.isBlank()) {
            return "unknown";
        }
        String prefix = "com.zjcxph.imgapi.";
        if (!loggerName.startsWith(prefix)) {
            int separator = loggerName.indexOf('.');
            return separator > 0 ? loggerName.substring(0, separator) : loggerName;
        }
        String remainder = loggerName.substring(prefix.length());
        int separator = remainder.indexOf('.');
        return separator > 0 ? remainder.substring(0, separator) : remainder;
    }

    private String normalizeAllowed(String value, Set<String> allowed, String fieldName) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException(fieldName + "不受支持");
        }
        return normalized;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
