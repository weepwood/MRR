package com.zjcxph.imgapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.common.AppErrorCode;
import com.zjcxph.imgapi.entity.Log;
import com.zjcxph.imgapi.exception.BusinessException;
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
import java.io.BufferedWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Persists security-sensitive audit events synchronously. PostgreSQL is preferred; when it is
 * temporarily unavailable, events are forced to an append-only local spool and replayed later.
 * Sensitive requests call {@link #assertFallbackAvailable()} before executing so a broken fallback
 * path fails closed before business side effects are produced.
 */
@Service
public class ReliableAuditService {

    private static final Logger logger = LoggerFactory.getLogger(ReliableAuditService.class);
    private static final long MAX_SPOOL_BYTES = 512L * 1024L * 1024L;
    private static final long FALLBACK_PROBE_CACHE_MILLIS = 5_000L;

    private final LogMapper logMapper;
    private final ObjectMapper objectMapper;
    private final Path spoolFile;
    private final Path deadLetterFile;
    private final Path probeFile;
    private final Object spoolLock = new Object();
    private final AtomicLong queuedEvents = new AtomicLong();
    private final AtomicLong deadLetterEvents = new AtomicLong();
    private final Counter databaseWrites;
    private final Counter spoolWrites;
    private final Counter permanentFailures;

    private volatile String lastFailure;
    private volatile Instant lastFailureAt;
    private volatile boolean fallbackAvailable = true;
    private volatile boolean lostEventDetected;
    private volatile long lastFallbackProbeAt;

    public ReliableAuditService(LogMapper logMapper,
                                ObjectMapper objectMapper,
                                MeterRegistry meterRegistry,
                                @Value("${app.audit.spool-file:./audit-spool/audit-events.jsonl}") String spoolFile) {
        this.logMapper = logMapper;
        this.objectMapper = objectMapper;
        this.spoolFile = Path.of(spoolFile).toAbsolutePath().normalize();
        this.deadLetterFile = this.spoolFile.resolveSibling(this.spoolFile.getFileName() + ".deadletter");
        this.probeFile = this.spoolFile.resolveSibling(this.spoolFile.getFileName() + ".probe");
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
        Gauge.builder("mrr.audit.deadletter.events", deadLetterEvents, AtomicLong::get)
                .description("Audit events quarantined because the spool content was invalid")
                .register(meterRegistry);
        refreshState();
    }

    /**
     * Verifies that the durable fallback can accept an event before a sensitive request executes.
     * The probe is cached briefly to avoid forcing a filesystem write for every image request.
     */
    public void assertFallbackAvailable() {
        if (lostEventDetected || deadLetterEvents.get() > 0) {
            throw auditUnavailable();
        }
        long now = System.currentTimeMillis();
        if (fallbackAvailable && now - lastFallbackProbeAt < FALLBACK_PROBE_CACHE_MILLIS) {
            return;
        }
        synchronized (spoolLock) {
            now = System.currentTimeMillis();
            if (lostEventDetected || deadLetterEvents.get() > 0) {
                throw auditUnavailable();
            }
            if (fallbackAvailable && now - lastFallbackProbeAt < FALLBACK_PROBE_CACHE_MILLIS) {
                return;
            }
            try {
                ensureParentDirectory();
                ensureSpoolCapacity(64);
                forceWrite(probeFile, "audit-fallback-probe=" + now + System.lineSeparator(), false);
                Files.deleteIfExists(probeFile);
                fallbackAvailable = true;
                lastFallbackProbeAt = now;
                if (queuedEvents.get() == 0) {
                    clearFailure();
                }
            } catch (Exception exception) {
                fallbackAvailable = false;
                markFailure(exception.getClass().getSimpleName());
                logger.error("Audit fallback preflight failed", exception);
                throw auditUnavailable();
            }
        }
    }

    public void persist(Log auditLog) {
        auditLog.setPersistedVia("DATABASE");
        try {
            logMapper.insert(auditLog);
            databaseWrites.increment();
            if (queuedEvents.get() == 0 && deadLetterEvents.get() == 0 && !lostEventDetected) {
                clearFailure();
            }
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
                String jsonLine = objectMapper.writeValueAsString(auditLog) + System.lineSeparator();
                ensureSpoolCapacity(jsonLine.getBytes(StandardCharsets.UTF_8).length);
                forceWrite(spoolFile, jsonLine, true);
                queuedEvents.incrementAndGet();
                spoolWrites.increment();
                fallbackAvailable = true;
                lastFallbackProbeAt = System.currentTimeMillis();
                markFailure(databaseException.getClass().getSimpleName());
            } catch (Exception spoolException) {
                fallbackAvailable = false;
                lostEventDetected = true;
                markFailure(spoolException.getClass().getSimpleName());
                permanentFailures.increment();
                logger.error("CRITICAL: audit event {} could not be persisted to database or spool",
                        auditLog.getEventId(), spoolException);
                throw auditUnavailable();
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
            long persisted = 0;
            long remaining = 0;
            long quarantined = 0;
            String replayFailure = null;

            try (BufferedReader reader = Files.newBufferedReader(spoolFile, StandardCharsets.UTF_8);
                 BufferedWriter remainingWriter = Files.newBufferedWriter(
                         replayFile,
                         StandardCharsets.UTF_8,
                         StandardOpenOption.CREATE,
                         StandardOpenOption.WRITE,
                         StandardOpenOption.TRUNCATE_EXISTING);
                 BufferedWriter deadLetterWriter = Files.newBufferedWriter(
                         deadLetterFile,
                         StandardCharsets.UTF_8,
                         StandardOpenOption.CREATE,
                         StandardOpenOption.WRITE,
                         StandardOpenOption.APPEND)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }

                    Log auditLog;
                    try {
                        auditLog = objectMapper.readValue(line, Log.class);
                    } catch (Exception malformedEvent) {
                        deadLetterWriter.write(line);
                        deadLetterWriter.newLine();
                        quarantined++;
                        replayFailure = "SPOOL_CORRUPT";
                        continue;
                    }

                    try {
                        auditLog.setPersistedVia("SPOOL");
                        logMapper.insert(auditLog);
                        persisted++;
                    } catch (Exception databaseException) {
                        remainingWriter.write(line);
                        remainingWriter.newLine();
                        remaining++;
                        replayFailure = databaseException.getClass().getSimpleName();
                    }
                }
            } catch (Exception exception) {
                fallbackAvailable = false;
                markFailure(exception.getClass().getSimpleName());
                logger.warn("Unable to replay durable audit spool: {}", exception.getMessage());
                deleteQuietly(replayFile);
                return;
            }

            try {
                forceFile(replayFile);
                if (quarantined > 0) {
                    forceFile(deadLetterFile);
                }
                replaceSpool(replayFile);
                queuedEvents.set(remaining);
                if (quarantined > 0) {
                    deadLetterEvents.addAndGet(quarantined);
                }
                fallbackAvailable = true;

                if (persisted > 0) {
                    databaseWrites.increment(persisted);
                    logger.info("Replayed {} audit events from durable spool; {} remain; {} quarantined",
                            persisted, remaining, quarantined);
                }

                if (quarantined > 0) {
                    markFailure("SPOOL_CORRUPT");
                } else if (remaining > 0) {
                    markFailure(replayFailure == null ? "DATABASE_UNAVAILABLE" : replayFailure);
                } else if (!lostEventDetected && deadLetterEvents.get() == 0) {
                    clearFailure();
                }
            } catch (Exception exception) {
                fallbackAvailable = false;
                markFailure(exception.getClass().getSimpleName());
                logger.warn("Unable to replace durable audit spool: {}", exception.getMessage());
                deleteQuietly(replayFile);
            }
        }
    }

    public boolean isHealthy() {
        return fallbackAvailable && !lostEventDetected && deadLetterEvents.get() == 0;
    }

    public boolean isDegraded() {
        return isHealthy() && queuedEvents.get() > 0;
    }

    public boolean isFallbackAvailable() {
        return fallbackAvailable;
    }

    public boolean isLostEventDetected() {
        return lostEventDetected;
    }

    public long getQueuedEvents() {
        return queuedEvents.get();
    }

    public long getDeadLetterEvents() {
        return deadLetterEvents.get();
    }

    public String getLastFailure() {
        return lastFailure;
    }

    public Instant getLastFailureAt() {
        return lastFailureAt;
    }

    public Path getSpoolFile() {
        return spoolFile;
    }

    private void refreshState() {
        try {
            queuedEvents.set(countNonBlankLines(spoolFile));
            deadLetterEvents.set(countNonBlankLines(deadLetterFile));
            if (deadLetterEvents.get() > 0) {
                markFailure("SPOOL_CORRUPT");
            }
        } catch (Exception exception) {
            fallbackAvailable = false;
            markFailure(exception.getClass().getSimpleName());
            logger.warn("Unable to inspect audit spool: {}", exception.getMessage());
        }
    }

    private long countNonBlankLines(Path path) throws Exception {
        if (!Files.exists(path)) {
            return 0;
        }
        try (var lines = Files.lines(path, StandardCharsets.UTF_8)) {
            return lines.filter(line -> !line.isBlank()).count();
        }
    }

    private void ensureParentDirectory() throws Exception {
        Path parent = spoolFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private void ensureSpoolCapacity(long additionalBytes) throws Exception {
        long currentSize = Files.exists(spoolFile) ? Files.size(spoolFile) : 0L;
        if (currentSize + Math.max(0L, additionalBytes) > MAX_SPOOL_BYTES) {
            throw new IllegalStateException("audit spool has reached the 512 MB safety limit");
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

    private void forceFile(Path path) throws Exception {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
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

    private void markFailure(String failure) {
        this.lastFailure = failure == null || failure.isBlank() ? "UNKNOWN" : failure;
        this.lastFailureAt = Instant.now();
    }

    private void clearFailure() {
        this.lastFailure = null;
        this.lastFailureAt = null;
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            // Preserve the original failure as the health signal.
        }
    }

    private BusinessException auditUnavailable() {
        return new BusinessException(AppErrorCode.AUDIT_UNAVAILABLE);
    }
}
