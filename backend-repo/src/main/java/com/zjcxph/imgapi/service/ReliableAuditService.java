package com.zjcxph.imgapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.entity.Log;
import com.zjcxph.imgapi.mapper.LogMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Persists security-sensitive audit events synchronously. If PostgreSQL is temporarily unavailable,
 * the event is forced to an append-only local spool and replayed after recovery.
 */
@Service
public class ReliableAuditService {

    private static final Logger logger = LoggerFactory.getLogger(ReliableAuditService.class);
    private static final long MAX_SPOOL_BYTES = 512L * 1024L * 1024L;

    private final LogMapper logMapper;
    private final ObjectMapper objectMapper;
    private final Path spoolFile;
    private final Object spoolLock = new Object();
    private final AtomicLong queuedEvents = new AtomicLong();
    private final Counter databaseWrites;
    private final Counter spoolWrites;
    private final Counter permanentFailures;
    private volatile String lastFailure;

    public ReliableAuditService(LogMapper logMapper,
                                ObjectMapper objectMapper,
                                MeterRegistry meterRegistry,
                                @Value("${app.audit.spool-file:./audit-spool/audit-events.jsonl}") String spoolFile) {
        this.logMapper = logMapper;
        this.objectMapper = objectMapper;
        this.spoolFile = Path.of(spoolFile).toAbsolutePath().normalize();
        this.databaseWrites = Counter.builder("mrr.audit.persist.total")
                .tag("result", "database")
                .description("Critical audit events persisted directly to PostgreSQL")
                .register(meterRegistry);
        this.spoolWrites = Counter.builder("mrr.audit.persist.total")
                .tag("result", "spool")
                .description("Critical audit events persisted to the local durable spool")
                .register(meterRegistry);
        this.permanentFailures = Counter.builder("mrr.audit.persist.total")
                .tag("result", "failed")
                .description("Critical audit events that could not be persisted")
                .register(meterRegistry);
        Gauge.builder("mrr.audit.spool.events", queuedEvents, AtomicLong::get)
                .description("Approximate number of audit events waiting in the durable spool")
                .register(meterRegistry);
        refreshQueuedEvents();
    }

    public void persist(Log auditLog) {
        auditLog.setPersistedVia("DATABASE");
        try {
            logMapper.insert(auditLog);
            databaseWrites.increment();
            lastFailure = null;
        } catch (Exception databaseException) {
            logger.error("Critical audit database write failed; writing event {} to durable spool",
                    auditLog.getEventId(), databaseException);
            persistToSpool(auditLog, databaseException);
        }
    }

    private void persistToSpool(Log auditLog, Exception databaseException) {
        auditLog.setPersistedVia("SPOOL");
        synchronized (spoolLock) {
            try {
                ensureParentDirectory();
                if (Files.exists(spoolFile) && Files.size(spoolFile) >= MAX_SPOOL_BYTES) {
                    throw new IllegalStateException("audit spool has reached the 512 MB safety limit");
                }
                String jsonLine = objectMapper.writeValueAsString(auditLog) + System.lineSeparator();
                forceWrite(spoolFile, jsonLine, true);
                queuedEvents.incrementAndGet();
                spoolWrites.increment();
                lastFailure = databaseException.getClass().getSimpleName();
            } catch (Exception spoolException) {
                lastFailure = spoolException.getClass().getSimpleName();
                permanentFailures.increment();
                logger.error("CRITICAL: audit event {} could not be persisted to database or spool",
                        auditLog.getEventId(), spoolException);
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.audit.replay-interval-ms:30000}")
    public void replaySpool() {
        synchronized (spoolLock) {
            if (!Files.exists(spoolFile)) {
                queuedEvents.set(0);
                return;
            }

            Path replayFile = spoolFile.resolveSibling(spoolFile.getFileName() + ".replay");
            List<String> remaining = new ArrayList<>();
            long persisted = 0;
            try (BufferedReader reader = Files.newBufferedReader(spoolFile, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    try {
                        Log auditLog = objectMapper.readValue(line, Log.class);
                        auditLog.setPersistedVia("SPOOL");
                        logMapper.insert(auditLog);
                        persisted++;
                    } catch (Exception replayException) {
                        remaining.add(line);
                    }
                }

                StringBuilder rewritten = new StringBuilder();
                for (String line : remaining) {
                    rewritten.append(line).append(System.lineSeparator());
                }
                forceWrite(replayFile, rewritten.toString(), false);
                replaceSpool(replayFile);
                queuedEvents.set(remaining.size());
                if (persisted > 0) {
                    databaseWrites.increment(persisted);
                    logger.info("Replayed {} audit events from durable spool; {} remain", persisted, remaining.size());
                }
                if (remaining.isEmpty()) {
                    lastFailure = null;
                }
            } catch (Exception exception) {
                lastFailure = exception.getClass().getSimpleName();
                logger.warn("Unable to replay durable audit spool: {}", exception.getMessage());
                try {
                    Files.deleteIfExists(replayFile);
                } catch (Exception ignored) {
                    // Keep the original spool untouched.
                }
            }
        }
    }

    public boolean isHealthy() {
        return lastFailure == null || queuedEvents.get() > 0;
    }

    public long getQueuedEvents() {
        return queuedEvents.get();
    }

    public String getLastFailure() {
        return lastFailure;
    }

    public Path getSpoolFile() {
        return spoolFile;
    }

    private void refreshQueuedEvents() {
        if (!Files.exists(spoolFile)) {
            queuedEvents.set(0);
            return;
        }
        try (var lines = Files.lines(spoolFile, StandardCharsets.UTF_8)) {
            queuedEvents.set(lines.filter(line -> !line.isBlank()).count());
        } catch (Exception exception) {
            lastFailure = exception.getClass().getSimpleName();
            logger.warn("Unable to inspect audit spool: {}", exception.getMessage());
        }
    }

    private void ensureParentDirectory() throws Exception {
        Path parent = spoolFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private void forceWrite(Path path, String content, boolean append) throws Exception {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        StandardOpenOption[] options = append
                ? new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND}
                : new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING};
        try (FileChannel channel = FileChannel.open(path, options)) {
            ByteBuffer buffer = StandardCharsets.UTF_8.encode(content);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    private void replaceSpool(Path replayFile) throws Exception {
        try {
            Files.move(replayFile, spoolFile,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(replayFile, spoolFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
