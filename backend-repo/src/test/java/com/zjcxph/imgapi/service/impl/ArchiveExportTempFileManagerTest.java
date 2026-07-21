package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.config.ArchiveExportProperties;
import com.zjcxph.imgapi.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArchiveExportTempFileManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void reservesQuotaAndBuildsFilesInsideTheControlledDirectory() throws Exception {
        ArchiveExportProperties properties = new ArchiveExportProperties();
        properties.setTempDirectory(tempDir.toString());
        properties.setMaxTotalBytes(10);
        properties.setMaxFileBytes(10);
        properties.setRetention(Duration.ofHours(1));
        ArchiveExportTempFileManager manager = new ArchiveExportTempFileManager(properties);
        manager.initialize();

        try (ArchiveExportTempFileManager.Reservation reservation = manager.reserve("job-0001", 6, "zip")) {
            assertThat(reservation.path().normalize()).startsWith(tempDir.normalize());
            assertThat(reservation.path().getFileName().toString()).isEqualTo("job-0001.zip");
            assertThat(reservation.reservedBytes()).isEqualTo(10);
            assertThatThrownBy(() -> manager.reserve("job-0002", 5, "pdf"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("临时文件配额不足");
        }
    }
}
