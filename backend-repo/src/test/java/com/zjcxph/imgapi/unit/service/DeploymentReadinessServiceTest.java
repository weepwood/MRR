package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.config.ArchiveExportProperties;
import com.zjcxph.imgapi.config.ImageProperties;
import com.zjcxph.imgapi.service.DeploymentReadinessService;
import com.zjcxph.imgapi.service.ImageUrlService;
import com.zjcxph.imgapi.service.OssService;
import com.zjcxph.imgapi.service.SystemSettingService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class DeploymentReadinessServiceTest {

    @Test
    void databaseExceptionTextIsNotExposedInReadiness() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        org.mockito.Mockito.when(jdbc.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("SYNTHETIC private patient and jdbc location"));
        var service = new DeploymentReadinessService(jdbc, mock(Flyway.class), new ImageProperties(),
                new ArchiveExportProperties(), mock(ImageUrlService.class), mock(OssService.class),
                mock(SystemSettingService.class), 0L, 48L, 1L, 1000000L);
        try {
            var result = (Map<?, ?>) org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "checkDatabase");
            org.assertj.core.api.Assertions.assertThat(result.toString()).doesNotContain("SYNTHETIC private");
            org.assertj.core.api.Assertions.assertThat(result.get("passed")).isEqualTo(false);
        } finally { service.shutdown(); }
    }

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
                0L,
                48L,
                1L,
                1_000_000L
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
}
