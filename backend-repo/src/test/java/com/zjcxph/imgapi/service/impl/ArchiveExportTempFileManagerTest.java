package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.config.ArchiveExportProperties;
import com.zjcxph.imgapi.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArchiveExportTempFileManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void reservesQuotaAndBuildsFilesInsideTheControlledDirectory() throws Exception {
        ArchiveExportProperties properties = properties(10, 10);
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

    @Test
    void doesNotCountTheActiveTaskFileAgainAfterItsQuotaWasReserved() throws Exception {
        ArchiveExportProperties properties = properties(20, 10);
        ArchiveExportTempFileManager manager = new ArchiveExportTempFileManager(properties);
        manager.initialize();

        try (ArchiveExportTempFileManager.Reservation first = manager.reserve("job-0001", 6, "zip")) {
            try (OutputStream output = manager.openOutput(first)) {
                output.write(new byte[6]);
            }

            try (ArchiveExportTempFileManager.Reservation second = manager.reserve("job-0002", 5, "pdf")) {
                assertThat(second.reservedBytes()).isEqualTo(10);
            }
        }
    }

    private ArchiveExportProperties properties(long maxTotalBytes, long maxFileBytes) {
        ArchiveExportProperties properties = new ArchiveExportProperties();
        properties.setTempDirectory(tempDir.toString());
        properties.setMaxTotalBytes(maxTotalBytes);
        properties.setMaxFileBytes(maxFileBytes);
        properties.setRetention(Duration.ofHours(1));
        return properties;
    }
}
