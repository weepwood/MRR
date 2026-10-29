package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.config.ArchiveExportProperties;
import com.zjcxph.imgapi.dto.req.ArchiveExportJobRequest;
import com.zjcxph.imgapi.dto.resp.ArchiveExportSummary;
import com.zjcxph.imgapi.entity.ArchiveExportJob;
import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.repository.ArchiveExportJobRepository;
import com.zjcxph.imgapi.service.impl.ArchiveExportTempFileManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.nio.file.Path;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ArchiveExportJobPreflightTest {
    @TempDir Path directory;
    final ArchiveExportService exporter = mock(ArchiveExportService.class);
    final ArchiveExportJobRepository repository = mock(ArchiveExportJobRepository.class);
    final ArchiveExportTempFileManager files = mock(ArchiveExportTempFileManager.class);
    final List<Runnable> queue = new ArrayList<>();
    final AtomicReference<ArchiveExportJob> inserted = new AtomicReference<>();
    final ArchiveExportJobService service = new ArchiveExportJobService(
            exporter, repository, files, new ArchiveExportProperties(), queue::add);

    @BeforeEach
    void setup() {
        doAnswer(invocation -> { inserted.set(invocation.getArgument(0)); return null; }).when(repository).insert(any());
        when(repository.findById(anyString())).thenAnswer(invocation -> Optional.ofNullable(inserted.get()));
    }

    @Test
    void wholeArchiveCreationUsesOnlySummaryAndDoesNotQueueAFullImageList() {
        when(exporter.summarizeArchive("789508", "")).thenReturn(new ArchiveExportSummary(1000, 5000));
        when(exporter.prepareArchive("789508", "")).thenReturn(export());

        var result = service.create(session(), request());

        assertThat(result.plannedCount()).isEqualTo(1000);
        assertThat(result.estimatedBytes()).isEqualTo(5000);
        assertThat(result.sourceSummary()).isEmpty();
        assertThat(queue).hasSize(1);
        verify(exporter).summarizeArchive("789508", "");
        verify(exporter, never()).prepareArchive(anyString(), anyString());
        verifyNoInteractions(files);
    }

    @Test
    void emptySummaryKeepsSynchronousNotFoundAndNeverQueuesAJob() {
        when(exporter.summarizeArchive("789508", "")).thenReturn(new ArchiveExportSummary(0, 0));
        when(exporter.prepareArchive("789508", "")).thenReturn(export());

        assertThatThrownBy(() -> service.create(session(), request()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("未找到可导出的影像");
        assertThat(queue).isEmpty();
        verify(repository, never()).insert(any());
    }

    @Test
    void selectedArchiveStillUsesTheExistingFullSelectionValidation() {
        var request = request();
        request.setIds(List.of("1"));
        when(exporter.prepareSelectedArchive(List.of("1"))).thenReturn(export());

        var result = service.create(session(), request);

        assertThat(result.plannedCount()).isEqualTo(1);
        verify(exporter).prepareSelectedArchive(List.of("1"));
        verify(exporter, never()).summarizeArchive(anyString(), anyString());
    }

    @Test
    void anExistingIdempotentJobDoesNotRepeatPreflightOrQueueWork() {
        var request = request();
        request.setIdempotencyKey("synthetic-request");
        ArchiveExportJob job = new ArchiveExportJob();
        job.setId("synthetic-existing"); job.setStatus("PENDING"); job.setFormat("ZIP");
        job.setScope("WHOLE_ARCHIVE"); job.setBah("789508"); job.setSjh("");
        when(repository.findByIdempotency(1L, "synthetic-user", "synthetic-request")).thenReturn(Optional.of(job));

        assertThat(service.create(session(), request).id()).isEqualTo(job.getId());
        verifyNoInteractions(exporter, files);
        assertThat(queue).isEmpty();
    }

    @Test
    void workerPreparesOnceAndReplacesTheEarlierCountBeforePublishingSuccess() throws Exception {
        var properties = new ArchiveExportProperties();
        properties.setTempDirectory(directory.toString());
        var managedFiles = new ArchiveExportTempFileManager(properties);
        managedFiles.initialize();
        var workerService = new ArchiveExportJobService(exporter, repository, managedFiles, properties, queue::add);
        when(exporter.summarizeArchive("789508", "")).thenReturn(new ArchiveExportSummary(1000, 5000));
        when(exporter.prepareArchive("789508", "")).thenReturn(export());
        when(repository.markProcessing(anyString(), any(), any(), any())).thenReturn(1);
        when(repository.updatePlan(anyString(), anyInt(), anyLong(), any())).thenAnswer(invocation -> {
            inserted.get().setPlannedCount(invocation.getArgument(1));
            inserted.get().setEstimatedBytes(invocation.getArgument(2));
            inserted.get().setSourceSummary(invocation.getArgument(3));
            return 1;
        });
        when(repository.setOutputFile(anyString(), anyString())).thenReturn(1);
        when(repository.markSuccess(anyString(), anyLong(), anyString(), any())).thenReturn(1);
        doAnswer(invocation -> { invocation.<OutputStream>getArgument(1).write(1); return null; })
                .when(exporter).writeBatchZip(any(), any(), any());

        String id = workerService.create(session(), request()).id();
        verify(exporter, never()).prepareArchive(anyString(), anyString());
        queue.getFirst().run();

        verify(exporter, times(1)).prepareArchive("789508", "");
        verify(repository).updatePlan(id, 1, 10L, "NGINX");
        assertThat(inserted.get().getPlannedCount()).isEqualTo(1);
        assertThat(inserted.get().getSourceSummary()).isEqualTo("NGINX");
        verify(repository).markSuccess(eq(id), eq(1L), anyString(), any());
    }

    @Test
    void cancellationDuringPreparationStopsBeforeFileReservation() throws Exception {
        when(exporter.summarizeArchive("789508", "")).thenReturn(new ArchiveExportSummary(1, 10));
        when(exporter.prepareArchive("789508", "")).thenReturn(export());
        when(repository.markProcessing(anyString(), any(), any(), any())).thenReturn(1);
        when(repository.updatePlan(anyString(), anyInt(), anyLong(), any())).thenReturn(0);
        String id = service.create(session(), request()).id();

        queue.getFirst().run();

        verify(repository).markCancelled(eq(id), any());
        verify(exporter, never()).writeBatchZip(any(), any(), any());
        verifyNoInteractions(files);
    }

    static ArchiveExportService.BatchZipExport export() {
        PathDO item = new PathDO("synthetic", "page.jpg", "synthetic", "789508");
        item.setFileSize(10L);
        return new ArchiveExportService.BatchZipExport(List.of(item));
    }

    static ArchiveExportJobRequest request() {
        ArchiveExportJobRequest request = new ArchiveExportJobRequest();
        request.setFormat("ZIP"); request.setBah("789508");
        return request;
    }

    static AuthSession session() {
        AuthSession session = new AuthSession();
        session.setId(1L); session.setUsername("synthetic-user");
        session.setPermissions(List.of("record:download"));
        return session;
    }
}
