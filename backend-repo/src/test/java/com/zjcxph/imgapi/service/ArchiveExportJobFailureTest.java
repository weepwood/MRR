package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.config.ArchiveExportProperties;
import com.zjcxph.imgapi.entity.ArchiveExportJob;
import com.zjcxph.imgapi.repository.ArchiveExportJobRepository;
import com.zjcxph.imgapi.service.impl.ArchiveExportTempFileManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ArchiveExportJobFailureTest {
    @TempDir Path directory;

    @Test
    void failedDeletionKeepsTaskForRetryAndNextCleanupCanExpireIt() throws Exception {
        ArchiveExportProperties properties = new ArchiveExportProperties();
        properties.setTempDirectory(directory.toString());
        ArchiveExportTempFileManager files = new ArchiveExportTempFileManager(properties);
        files.initialize();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        files.registerCleanupMetrics(registry);
        // A nonempty directory deterministically rejects delete on Windows and Unix.
        Path locked = Files.createDirectory(directory.resolve("synthetic-task.zip"));
        Path child = Files.writeString(locked.resolve("synthetic-child"), "synthetic");
        ArchiveExportJob job = new ArchiveExportJob();
        job.setId("synthetic-task");
        job.setFilePath(locked.toString());
        ArchiveExportJobRepository repository = mock(ArchiveExportJobRepository.class);
        when(repository.findExpired(any(LocalDateTime.class))).thenReturn(List.of(job));
        ArchiveExportJobService service = new ArchiveExportJobService(
                mock(ArchiveExportService.class), repository, files, properties, Runnable::run);

        service.cleanupExpiredFiles();
        verify(repository, never()).markExpired(job.getId());
        verify(repository).deferCleanup(job.getId());
        assertThat(registry.get("mrr.archive.export.cleanup.failures").gauge().value()).isEqualTo(1);
        Files.delete(child);
        Files.delete(locked);
        service.cleanupExpiredFiles();
        verify(repository).markExpired(job.getId());
        registry.close();
    }
}
