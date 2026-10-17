package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.config.ArchiveExportProperties;
import com.zjcxph.imgapi.config.ImageProperties;
import com.zjcxph.imgapi.service.DeploymentReadinessService;
import com.zjcxph.imgapi.service.ImageUrlService;
import com.zjcxph.imgapi.service.OssService;
import com.zjcxph.imgapi.service.SystemSettingService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeploymentReadinessServiceTest {

    @Test
    void readingSnapshotDoesNotPerformDatabaseOrNetworkChecks() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        Flyway flyway = mock(Flyway.class);
        ImageProperties imageProperties = mock(ImageProperties.class);
        ArchiveExportProperties exportProperties = mock(ArchiveExportProperties.class);
        ImageUrlService imageUrlService = mock(ImageUrlService.class);
        OssService ossService = mock(OssService.class);
        SystemSettingService systemSettingService = mock(SystemSettingService.class);

        DeploymentReadinessService service = new DeploymentReadinessService(
                jdbcTemplate,
                flyway,
                imageProperties,
                exportProperties,
                imageUrlService,
                ossService,
                systemSettingService,
                new SimpleMeterRegistry(),
                0L,
                48L,
                1L
        );

        try {
            Map<String, Object> snapshot = service.getSnapshot();

            assertTrue(service.isReadOnly());
            assertEquals("READ_ONLY_DEGRADED", snapshot.get("mode"));
            assertEquals(Instant.EPOCH.toString(), snapshot.get("checkedAt"));
            verifyNoInteractions(
                    jdbcTemplate,
                    flyway,
                    imageProperties,
                    exportProperties,
                    imageUrlService,
                    ossService,
                    systemSettingService
            );
        } finally {
            service.shutdown();
        }
    }

    @Test
    void refreshSnapshotExposesBackupFreshnessMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SystemSettingService systemSettingService = mock(SystemSettingService.class);
        when(systemSettingService.getSetting("lastSuccessfulBackupAt"))
                .thenReturn("2026-10-17T00:00:00Z");
        DeploymentReadinessService service = newService(systemSettingService, registry);

        try {
            service.refreshSnapshot();

            assertEquals(
                    Instant.parse("2026-10-17T00:00:00Z").getEpochSecond(),
                    registry.get("mrr.backup.last.success.timestamp").gauge().value()
            );
            assertTrue(registry.get("mrr.backup.age.hours").gauge().value() >= 0);
        } finally {
            service.shutdown();
        }
    }

    @Test
    void backupMetricsStayAtNeverRecordedWhenNoBackupWasEverWritten() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SystemSettingService systemSettingService = mock(SystemSettingService.class);
        when(systemSettingService.getSetting("lastSuccessfulBackupAt")).thenReturn(null);
        DeploymentReadinessService service = newService(systemSettingService, registry);

        try {
            service.refreshSnapshot();

            assertEquals(0.0, registry.get("mrr.backup.last.success.timestamp").gauge().value());
            assertEquals(-1.0, registry.get("mrr.backup.age.hours").gauge().value());
        } finally {
            service.shutdown();
        }
    }

    private DeploymentReadinessService newService(
            SystemSettingService systemSettingService,
            SimpleMeterRegistry registry
    ) {
        return new DeploymentReadinessService(
                mock(JdbcTemplate.class),
                mock(Flyway.class),
                mock(ImageProperties.class),
                mock(ArchiveExportProperties.class),
                mock(ImageUrlService.class),
                mock(OssService.class),
                systemSettingService,
                registry,
                0L,
                48L,
                1L
        );
    }
}
