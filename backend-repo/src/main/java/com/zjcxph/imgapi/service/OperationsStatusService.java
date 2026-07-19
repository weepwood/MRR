package com.zjcxph.imgapi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
    private final Path runtimeRoot;
    private final Path backupStateFile;
    private final Path backupErrorFile;
    private final Path applicationLog;
    private final Path errorLog;
    private final Path imageRoot;

    public OperationsStatusService(
            ReliableAuditService reliableAuditService,
            ObjectMapper objectMapper,
            @Value("${app.runtime.root:.}") String runtimeRoot,
            @Value("${app.backup.state-file:./state/backup/last-backup.json}") String backupStateFile,
            @Value("${app.backup.error-file:./state/backup/last-backup-error.json}") String backupErrorFile,
            @Value("${logging.file.name:img-api.log}") String applicationLog,
            @Value("${logging.error-file.name:mrr-error.log}") String errorLog,
            @Value("${image.basePath:}") String imageRoot
    ) {
        this.reliableAuditService = reliableAuditService;
        this.objectMapper = objectMapper;
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
        result.put("audit", auditStatus());
        result.put("backup", backupStatus());
        result.put("storage", storageStatus());
        result.put("logs", logStatus());
        return result;
    }

    private Map<String, Object> auditStatus() {
        long queued = reliableAuditService.getQueuedEvents();
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("status", reliableAuditService.isHealthy() ? (queued > 0 ? "DEGRADED" : "UP") : "DOWN");
        audit.put("queuedEvents", queued);
        audit.put("lastFailure", reliableAuditService.getLastFailure());
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
        backup.put("secondaryCopyPath", latest.get("secondaryCopyPath"));
        backup.put("lastError", failure.get("error"));
        return backup;
    }

    private Map<String, Object> storageStatus() {
        Map<String, Object> storage = new LinkedHashMap<>();
        storage.put("server", fileStoreStatus(runtimeRoot));
        if (imageRoot != null) {
            storage.put("images", fileStoreStatus(imageRoot));
        }
        return storage;
    }

    private Map<String, Object> fileStoreStatus(Path path) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("path", path.toString());
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
            status.put("error", exception.getClass().getSimpleName());
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
