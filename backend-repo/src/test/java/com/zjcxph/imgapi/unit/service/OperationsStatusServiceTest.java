package com.zjcxph.imgapi.unit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.service.OperationsStatusService;
import com.zjcxph.imgapi.service.ReliableAuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationsStatusServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void reportsBackupAuditDiskAndLogStatusWithoutExternalMonitoring() throws Exception {
        Path stateDir = Files.createDirectories(tempDir.resolve("state/backup"));
        Path logsDir = Files.createDirectories(tempDir.resolve("logs/backend"));
        Path backupState = stateDir.resolve("last-backup.json");
        Path backupError = stateDir.resolve("last-backup-error.json");
        Path appLog = logsDir.resolve("img-api.log");
        Path errorLog = logsDir.resolve("mrr-error.log");

        Files.writeString(appLog, "application log");
        Files.writeString(errorLog, "error log");
        new ObjectMapper().writeValue(backupState.toFile(), Map.of(
                "result", "SUCCESS",
                "completedAt", Instant.now().minusSeconds(3600).toString(),
                "dumpSizeBytes", 1024L
        ));

        ReliableAuditService auditService = mock(ReliableAuditService.class);
        when(auditService.isHealthy()).thenReturn(true);
        when(auditService.getQueuedEvents()).thenReturn(0L);

        OperationsStatusService service = new OperationsStatusService(
                auditService,
                new ObjectMapper(),
                tempDir.toString(),
                backupState.toString(),
                backupError.toString(),
                appLog.toString(),
                errorLog.toString(),
                tempDir.toString()
        );

        Map<String, Object> status = service.getStatus();
        assertThat(status.get("mode")).isEqualTo("SINGLE_SERVER");

        @SuppressWarnings("unchecked")
        Map<String, Object> backup = (Map<String, Object>) status.get("backup");
        assertThat(backup.get("status")).isEqualTo("UP");
        assertThat(backup.get("dumpSizeBytes")).isEqualTo(1024);

        @SuppressWarnings("unchecked")
        Map<String, Object> audit = (Map<String, Object>) status.get("audit");
        assertThat(audit.get("status")).isEqualTo("UP");
        assertThat(audit.get("queuedEvents")).isEqualTo(0L);

        @SuppressWarnings("unchecked")
        Map<String, Object> logs = (Map<String, Object>) status.get("logs");
        assertThat((Long) logs.get("applicationBytes")).isPositive();
        assertThat((Long) logs.get("errorBytes")).isPositive();
    }
}
