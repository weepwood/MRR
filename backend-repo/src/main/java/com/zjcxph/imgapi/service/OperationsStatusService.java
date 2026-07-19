package com.zjcxph.imgapi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Read-only status aggregation for the single-server operations page. */
@Service
public class OperationsStatusService {

    private final ReliableAuditService reliableAuditService;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final Path runtimeRoot;
    private final Path backupStateFile;
    private final Path backupErrorFile;
    private final Path applicationLog;
    private final Path errorLog;
    private final Path imageRoot;

    @Autowired
    public OperationsStatusService(
            ReliableAuditService reliableAuditService,
            ObjectMapper objectMapper,
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            @Value("${app.runtime.root:.}") String runtimeRoot,
            @Value("${app.backup.state-file:./state/backup/last-backup.json}") String backupStateFile,
            @Value("${app.backup.error-file:./state/backup/last-backup-error.json}") String backupErrorFile,
            @Value("${logging.file.name:img-api.log}") String applicationLog,
            @Value("${logging.error-file.name:mrr-error.log}") String errorLog,
            @Value("${image.basePath:}") String imageRoot
    ) {
        this(
                reliableAuditService,
                objectMapper,
                jdbcTemplateProvider.getIfAvailable(),
                runtimeRoot,
                backupStateFile,
                backupErrorFile,
                applicationLog,
                errorLog,
                imageRoot
        );
    }

    /** Test-compatible constructor that does not require a database. */
    public OperationsStatusService(
            ReliableAuditService reliableAuditService,
            ObjectMapper objectMapper,
            String runtimeRoot,
            String backupStateFile,
            String backupErrorFile,
            String applicationLog,
            String errorLog,
            String imageRoot
    ) {
        this(
                reliableAuditService,
                objectMapper,
                null,
                runtimeRoot,
                backupStateFile,
                backupErrorFile,
                applicationLog,
                errorLog,
                imageRoot
        );
    }

    private OperationsStatusService(
            ReliableAuditService reliableAuditService,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate,
            String runtimeRoot,
            String backupStateFile,
            String backupErrorFile,
            String applicationLog,
            String errorLog,
            String imageRoot
    ) {
        this.reliableAuditService = reliableAuditService;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.runtimeRoot = normalize(runtimeRoot);
        this.backupStateFile = normalize(backupStateFile);
        this.backupErrorFile = normalize(backupErrorFile);
        this.applicationLog = normalize(applicationLog);
        this.errorLog = normalize(errorLog);
        this.imageRoot = imageRoot == null || imageRoot.isBlank() ? null : normalize(imageRoot);
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", "SINGLE_SERVER");
        result.put("checkedAt", Instant.now().toString());
        result.put("application", applicationStatus());
        result.put("database", databaseStatus());
        result.put("audit", auditStatus());
        result.put("backup", backupStatus());
        result.put("storage", storageStatus());
        result.put("logs", logStatus());
        return result;
    }

    private Map<String, Object> applicationStatus() {
        Runtime runtime = Runtime.getRuntime();
        long max = runtime.maxMemory();
        long total = runtime.totalMemory();
        long free = runtime.freeMemory();
        long used = Math.max(0L, total - free);

        Map<String, Object> application = new LinkedHashMap<>();
        application.put("status", "UP");
        application.put("jvmUptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000L);
        application.put("availableProcessors", runtime.availableProcessors());
        application.put("heapUsedBytes", used);
        application.put("heapCommittedBytes", total);
        application.put("heapMaxBytes", max);
        application.put("heapUsedPercent", max <= 0 ? 0 : Math.round(used * 10000.0d / max) / 100.0d);
        return application;
    }

    private Map<String, Object> databaseStatus() {
        Map<String, Object> database = new LinkedHashMap<>();
        if (jdbcTemplate == null) {
            database.put("status", "UNKNOWN");
            return database;
        }

        long started = System.nanoTime();
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            database.put("status", "UP");
            database.put("responseTimeMs", Math.round((System.nanoTime() - started) / 10_000.0d) / 100.0d);
            database.put("lockWaiters", safeLongQuery(
                    "SELECT COUNT(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'"));
            database.put("unlinkedArchiveRecords", safeLongQuery(
                    "SELECT COALESCE(SUM(unlinked_count), 0) FROM app.v_archive_link_quality"));
            database.put("pool", connectionPoolStatus());
        } catch (Exception exception) {
            database.put("status", "DOWN");
            database.put("errorType", exception.getClass().getSimpleName());
        }
        return database;
    }

    private Map<String, Object> connectionPoolStatus() {
        Map<String, Object> pool = new LinkedHashMap<>();
        if (!(jdbcTemplate.getDataSource() instanceof HikariDataSource hikari)
                || hikari.getHikariPoolMXBean() == null) {
            pool.put("status", "UNKNOWN");
            return pool;
        }
        pool.put("status", "UP");
        pool.put("active", hikari.getHikariPoolMXBean().getActiveConnections());
        pool.put("idle", hikari.getHikariPoolMXBean().getIdleConnections());
        pool.put("total", hikari.getHikariPoolMXBean().getTotalConnections());
        pool.put("waiting", hikari.getHikariPoolMXBean().getThreadsAwaitingConnection());
        return pool;
    }

    private Long safeLongQuery(String sql) {
        try {
            return jdbcTemplate.queryForObject(sql, Long.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Object> auditStatus() {
        long queued = reliableAuditService.getQueuedEvents();
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("status", reliableAuditService.isHealthy()
                ? (reliableAuditService.isDegraded() ? "DEGRADED" : "UP")
                : "DOWN");
        audit.put("queuedEvents", queued);
        audit.put("deadLetterEvents", reliableAuditService.getDeadLetterEvents());
        audit.put("fallbackAvailable", reliableAuditService.isFallbackAvailable());
        audit.put("lostEventDetected", reliableAuditService.isLostEventDetected());
        audit.put("lastFailureCode", reliableAuditService.getLastFailure());
        audit.put("lastFailureAt", reliableAuditService.getLastFailureAt() == null
                ? null
                : reliableAuditService.getLastFailureAt().toString());
        return audit;
    }

    private Map<String, Object> backupStatus() {
        Map<String, Object> latest = readJson(backupStateFile);
        Map<String, Object> failure = readJson(backupErrorFile);
        Map<String, Object> backup = new LinkedHashMap<>();

        Instant completedAt = parseInstant(latest.get("completedAt"));
        Instant failedAt = parseInstant(failure.get("failedAt"));
        Double ageHours = null;
        if (completedAt != null) {
            long ageMinutes = Math.max(0L, Duration.between(completedAt, Instant.now()).toMinutes());
            ageHours = Math.round(ageMinutes / 60.0d * 100.0d) / 100.0d;
        }

        String status;
        if (failedAt != null && (completedAt == null || failedAt.isAfter(completedAt))) {
            status = "FAILED";
        } else if (completedAt == null) {
            status = "NOT_RUN";
        } else if (ageHours != null && ageHours > 30) {
            status = "STALE";
        } else {
            status = "UP";
        }

        backup.put("status", status);
        backup.put("completedAt", completedAt == null ? null : completedAt.toString());
        backup.put("ageHours", ageHours);
        backup.put("dumpSizeBytes", latest.get("dumpSizeBytes"));
        backup.put("secondaryCopyConfigured", Boolean.TRUE.equals(latest.get("secondaryCopyConfigured")));
        backup.put("secretsIncluded", Boolean.TRUE.equals(latest.get("secretsIncluded")));
        backup.put("lastFailureAt", failedAt == null ? null : failedAt.toString());
        backup.put("lastErrorCode", failure.get("errorCode"));
        backup.put("lastErrorType", failure.get("errorType"));
        return backup;
    }

    private Map<String, Object> storageStatus() {
        Map<String, Object> storage = new LinkedHashMap<>();
        storage.put("server", fileStoreStatus("SERVER", runtimeRoot));
        if (imageRoot != null) {
            storage.put("images", fileStoreStatus("IMAGES", imageRoot));
        }
        return storage;
    }

    private Map<String, Object> fileStoreStatus(String location, Path path) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("location", location);
        if (!Files.exists(path)) {
            status.put("status", "MISSING");
            return status;
        }
        try {
            FileStore store = Files.getFileStore(path);
            long total = store.getTotalSpace();
            long usable = store.getUsableSpace();
            status.put("status", total > 0 && usable * 100.0d / total < 8.0d ? "CRITICAL" : "UP");
            status.put("totalBytes", total);
            status.put("usableBytes", usable);
            status.put("usedPercent", total <= 0 ? 0 : Math.round((total - usable) * 10000.0d / total) / 100.0d);
        } catch (Exception exception) {
            status.put("status", "UNKNOWN");
            status.put("errorType", exception.getClass().getSimpleName());
        }
        return status;
    }

    private Map<String, Object> logStatus() {
        Map<String, Object> logs = new LinkedHashMap<>();
        logs.put("applicationBytes", fileSize(applicationLog));
        logs.put("errorBytes", fileSize(errorLog));
        logs.put("applicationLogExists", Files.isRegularFile(applicationLog));
        logs.put("errorLogExists", Files.isRegularFile(errorLog));
        return logs;
    }

    private Map<String, Object> readJson(Path path) {
        if (!Files.isRegularFile(path)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(path.toFile(), new TypeReference<>() { });
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private Instant parseInstant(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private long fileSize(Path path) {
        try {
            return Files.isRegularFile(path) ? Files.size(path) : 0L;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static Path normalize(String value) {
        return Path.of(value == null || value.isBlank() ? "." : value).toAbsolutePath().normalize();
    }
}
