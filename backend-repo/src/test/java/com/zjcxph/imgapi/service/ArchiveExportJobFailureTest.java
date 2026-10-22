package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.config.ArchiveExportProperties;
import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.entity.ArchiveExportJob;
import com.zjcxph.imgapi.repository.ArchiveExportJobRepository;
import com.zjcxph.imgapi.service.impl.ArchiveExportTempFileManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.test.util.ReflectionTestUtils;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ArchiveExportJobFailureTest {
    @TempDir Path directory;

    @Test
    void cancellationBetweenReadAndClaimDoesNotRetainAWorkerFlag() {
        ArchiveExportJobRepository repository = mock(ArchiveExportJobRepository.class);
        ArchiveExportService exporter = mock(ArchiveExportService.class);
        ArchiveExportTempFileManager files = mock(ArchiveExportTempFileManager.class);
        ArchiveExportJobService service = new ArchiveExportJobService(
                exporter, repository, files, new ArchiveExportProperties(), Runnable::run);
        ArchiveExportJob job = cancellableJob("PENDING");
        when(repository.findById(job.getId())).thenReturn(Optional.of(job));
        when(repository.requestCancel(job.getId())).thenReturn(1);
        when(repository.isCancelRequested(job.getId())).thenReturn(true);
        when(repository.markProcessing(eq(job.getId()), any(), any(), any())).thenAnswer(invocation -> {
            service.cancel(session(), job.getId());
            return 0;
        });

        ReflectionTestUtils.invokeMethod(service, "runJob", job.getId());

        Map<?, ?> flags = (Map<?, ?>) ReflectionTestUtils.getField(service, "cancellationFlags");
        assertThat(flags).isEmpty();
        verifyNoInteractions(exporter, files);
    }

    @Test
    @SuppressWarnings("unchecked")
    void cancellationStillSignalsTheFlagOwnedByAnActiveWorker() {
        ArchiveExportJobRepository repository = mock(ArchiveExportJobRepository.class);
        ArchiveExportJobService service = new ArchiveExportJobService(
                mock(ArchiveExportService.class), repository, mock(ArchiveExportTempFileManager.class),
                new ArchiveExportProperties(), Runnable::run);
        ArchiveExportJob job = cancellableJob("PROCESSING");
        when(repository.findById(job.getId())).thenReturn(Optional.of(job));
        when(repository.requestCancel(job.getId())).thenReturn(1);
        Map<String, AtomicBoolean> flags = (Map<String, AtomicBoolean>)
                ReflectionTestUtils.getField(service, "cancellationFlags");
        AtomicBoolean ownedFlag = new AtomicBoolean();
        flags.put(job.getId(), ownedFlag);

        service.cancel(session(), job.getId());

        assertThat(ownedFlag.get()).isTrue();
        assertThat(flags.get(job.getId())).isSameAs(ownedFlag);
    }

    private ArchiveExportJob cancellableJob(String status) {
        ArchiveExportJob job = new ArchiveExportJob();
        job.setId("f15ec3bb-6423-413d-a3c3-c01211b777a8");
        job.setOwnerUserId(1L);
        job.setOwnerUsername("synthetic-export-user");
        job.setFormat("ZIP");
        job.setStatus(status);
        return job;
    }

    private AuthSession session() {
        AuthSession session = new AuthSession();
        session.setId(1L);
        session.setUsername("synthetic-export-user");
        session.setRoleCode("USER");
        session.setPermissions(List.of("record:download"));
        return session;
    }

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
