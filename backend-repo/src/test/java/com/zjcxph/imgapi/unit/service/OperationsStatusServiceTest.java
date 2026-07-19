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
                "dumpSizeBytes", 1024L,
                "secondaryCopyConfigured", false,
                "secretsIncluded", false
        ));

        ReliableAuditService auditService = mock(ReliableAuditService.class);
        when(auditService.isHealthy()).thenReturn(true);
        when(auditService.isDegraded()).thenReturn(false);
        when(auditService.isFallbackAvailable()).thenReturn(true);
        when(auditService.getQueuedEvents()).thenReturn(0L);
        when(auditService.getDeadLetterEvents()).thenReturn(0L);

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
        Map<String, Object> application = (Map<String, Object>) status.get("application");
        assertThat(application.get("status")).isEqualTo("UP");
        assertThat((Long) application.get("jvmUptimeSeconds")).isNotNegative();

        @SuppressWarnings("unchecked")
        Map<String, Object> backup = (Map<String, Object>) status.get("backup");
        assertThat(backup.get("status")).isEqualTo("UP");
        assertThat(backup.get("dumpSizeBytes")).isEqualTo(1024);
        assertThat(backup.get("secondaryCopyConfigured")).isEqualTo(false);
        assertThat(backup.get("secondaryCopyPath")).isNull();
        assertThat(backup.get("secretsIncluded")).isEqualTo(false);

        @SuppressWarnings("unchecked")
        Map<String, Object> audit = (Map<String, Object>) status.get("audit");
        assertThat(audit.get("status")).isEqualTo("UP");
        assertThat(audit.get("queuedEvents")).isEqualTo(0L);
        assertThat(audit.get("deadLetterEvents")).isEqualTo(0L);
        assertThat(audit.get("fallbackAvailable")).isEqualTo(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> storage = (Map<String, Object>) status.get("storage");
        @SuppressWarnings("unchecked")
        Map<String, Object> server = (Map<String, Object>) storage.get("server");
        assertThat(server.get("location")).isEqualTo("SERVER");
        assertThat(server.get("path")).isEqualTo("SERVER");
        assertThat(server.toString()).doesNotContain(tempDir.toString());

        @SuppressWarnings("unchecked")
        Map<String, Object> logs = (Map<String, Object>) status.get("logs");
        assertThat((Long) logs.get("applicationBytes")).isPositive();
        assertThat((Long) logs.get("errorBytes")).isPositive();
    }

    @Test
    void redactsSecondaryBackupPathAndRawFailureMessage() throws Exception {
        Path stateDir = Files.createDirectories(tempDir.resolve("redacted/state/backup"));
        Path backupState = stateDir.resolve("last-backup.json");
        Path backupError = stateDir.resolve("last-backup-error.json");
        String secretPath = "\\\\nas-secret\\mrr-backup";
        String rawError = "password=top-secret; failed to copy " + secretPath;

        new ObjectMapper().writeValue(backupState.toFile(), Map.of(
                "result", "SUCCESS",
                "completedAt", Instant.now().minusSeconds(7200).toString(),
                "dumpSizeBytes", 2048L,
                "secondaryCopyConfigured", true,
                "secretsIncluded", false,
                "secondaryCopyPath", secretPath
        ));
        new ObjectMapper().writeValue(backupError.toFile(), Map.of(
                "result", "FAILED",
                "failedAt", Instant.now().toString(),
                "errorCode", "BACKUP_FAILED",
                "errorType", "IOException",
                "error", rawError
        ));

        ReliableAuditService auditService = mock(ReliableAuditService.class);
        when(auditService.isHealthy()).thenReturn(true);
        when(auditService.isFallbackAvailable()).thenReturn(true);

        OperationsStatusService service = new OperationsStatusService(
                auditService,
                new ObjectMapper(),
                tempDir.toString(),
                backupState.toString(),
                backupError.toString(),
                tempDir.resolve("app.log").toString(),
                tempDir.resolve("error.log").toString(),
                ""
        );

        Map<String, Object> status = service.getStatus();
        @SuppressWarnings("unchecked")
        Map<String, Object> backup = (Map<String, Object>) status.get("backup");

        assertThat(backup.get("status")).isEqualTo("FAILED");
        assertThat(backup.get("secondaryCopyPath")).isEqualTo("已配置（路径已隐藏）");
        assertThat(backup.get("lastError")).isEqualTo("BACKUP_FAILED");
        assertThat(backup.get("lastErrorType")).isEqualTo("IOException");
        assertThat(status.toString())
                .doesNotContain(secretPath)
                .doesNotContain("top-secret")
                .doesNotContain("password=");
    }
}
