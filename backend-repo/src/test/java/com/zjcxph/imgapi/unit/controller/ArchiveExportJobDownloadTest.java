package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.controller.ArchiveExportJobController;
import com.zjcxph.imgapi.entity.ArchiveExportJob;
import com.zjcxph.imgapi.interceptors.AuthorizationInterceptor;
import com.zjcxph.imgapi.repository.ArchiveExportJobRepository;
import com.zjcxph.imgapi.service.ArchiveExportJobService;
import com.zjcxph.imgapi.service.DeploymentReadinessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayOutputStream;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ArchiveExportJobDownloadTest {
    @TempDir Path directory;

    @Test
    void pendingDownloadsPinFileAndLimitConcurrentRequests() throws Exception {
        var props = new com.zjcxph.imgapi.config.ArchiveExportProperties();
        props.setTempDirectory(directory.toString());
        var files = new com.zjcxph.imgapi.service.impl.ArchiveExportTempFileManager(props);
        files.initialize();
        var repository = mock(ArchiveExportJobRepository.class);
        var service = new ArchiveExportJobService(mock(com.zjcxph.imgapi.service.ArchiveExportService.class),
                repository, files, props, Runnable::run);
        var controller = new ArchiveExportJobController(service, repository, mock(DeploymentReadinessService.class));
        var session = new AuthSession();
        session.setId(1L); session.setUsername("synthetic-user");
        session.setPermissions(List.of("record:download"));
        var job = new ArchiveExportJob();
        job.setId("synthetic-job"); job.setOwnerUserId(1L); job.setStatus("SUCCESS");
        job.setFormat("ZIP"); job.setFileName("synthetic.zip");
        var file = Files.write(directory.resolve("synthetic.zip"), new byte[]{1,2,3,4}).toRealPath();
        job.setFilePath(file.toString());
        job.setOutputBytes(4L);
        job.setSha256(org.apache.commons.codec.digest.DigestUtils.sha256Hex(new byte[]{1,2,3,4}));
        when(repository.findById(job.getId())).thenReturn(java.util.Optional.of(job));
        java.util.function.Supplier<MockHttpServletRequest> requests = () -> {
            var request = new MockHttpServletRequest("GET", "/download");
            request.setAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE, session);
            return request;
        };
        // Unsatisfiable requests and rejected async tasks must release the pending file lease.
        assertThat(controller.download(job.getId(), "bytes=99-100", requests.get()).getStatusCode().value())
                .isEqualTo(416);
        var rejectedRequest = requests.get();
        rejectedRequest.setAsyncSupported(true);
        var rejected = controller.download(job.getId(), null, rejectedRequest);
        var manager = org.springframework.web.context.request.async.WebAsyncUtils.getAsyncManager(rejectedRequest);
        manager.setAsyncWebRequest(org.springframework.web.context.request.async.WebAsyncUtils.createAsyncWebRequest(
                rejectedRequest, new org.springframework.mock.web.MockHttpServletResponse()));
        manager.setTaskExecutor(new org.springframework.core.task.AsyncTaskExecutor() {
            @Override public void execute(Runnable task) {
                throw new org.springframework.core.task.TaskRejectedException("synthetic rejection");
            }
        });
        manager.startCallableProcessing(() -> {
            rejected.getBody().writeTo(new ByteArrayOutputStream());
            return null;
        });
        assertThat(manager.getConcurrentResult()).isInstanceOf(org.springframework.core.task.TaskRejectedException.class);
        rejectedRequest.getAsyncContext().complete();
        var first = controller.download(job.getId(), null, requests.get());
        var second = controller.download(job.getId(), "bytes=1-2", requests.get());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.download(job.getId(), null, requests.get()))
                .isInstanceOf(com.zjcxph.imgapi.exception.BusinessException.class)
                .satisfies(failure -> assertThat(((com.zjcxph.imgapi.exception.BusinessException)failure).getCode()).isEqualTo(429));
        assertThat(files.deleteManagedFile(file.toString())).isFalse();
        var bytes = new ByteArrayOutputStream();
        first.getBody().writeTo(bytes);
        assertThat(bytes.toByteArray()).containsExactly((byte)1,(byte)2,(byte)3,(byte)4);
        assertThat(files.deleteManagedFile(file.toString())).isFalse();
        // A failed client write releases its handle and quota as well.
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> second.getBody().writeTo(new java.io.OutputStream() {
            @Override public void write(int value) throws java.io.IOException { throw new java.io.IOException("synthetic disconnect"); }
        })).isInstanceOf(java.io.IOException.class);
        assertThat(files.deleteManagedFile(file.toString())).isTrue();
    }

    @Test
    void staleIfRangeReturnsFullRepresentationAndMatchingTagReturnsPartial() throws Exception {
        var service = mock(ArchiveExportJobService.class);
        var repository = mock(ArchiveExportJobRepository.class);
        var readiness = mock(DeploymentReadinessService.class);
        var controller = new ArchiveExportJobController(service, repository, readiness);
        var session = new AuthSession();
        session.setPermissions(List.of("record:download"));
        var job = new ArchiveExportJob();
        job.setFormat("ZIP");
        job.setFileName("synthetic.zip");
        job.setSha256("a".repeat(64));
        var file = directory.resolve("synthetic.zip");
        Files.write(file, new byte[]{1,2,3,4});
        when(service.requireOwned(session, "job")).thenReturn(job);
        var props = new com.zjcxph.imgapi.config.ArchiveExportProperties();
        props.setTempDirectory(directory.toString());
        var files = new com.zjcxph.imgapi.service.impl.ArchiveExportTempFileManager(props);
        files.initialize();
        when(service.openDownload(session, "job")).thenAnswer(call -> files.openDownload(file.toRealPath().toString(), 1L));
        var request = new MockHttpServletRequest("GET", "/download");
        request.setAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE, session);
        request.addHeader("If-Range", "\"" + "b".repeat(64) + "\"");
        var full = controller.download("job", "bytes=1-2", request);
        assertThat(full.getStatusCode().value()).isEqualTo(200);
        var bytes = new ByteArrayOutputStream();
        full.getBody().writeTo(bytes);
        assertThat(bytes.toByteArray()).containsExactly((byte)1,(byte)2,(byte)3,(byte)4);
        request.removeHeader("If-Range");
        request.addHeader("If-Range", "\"" + job.getSha256() + "\"");
        var part = controller.download("job", "bytes=1-2", request);
        assertThat(part.getStatusCode().value()).isEqualTo(206);
        bytes.reset();
        part.getBody().writeTo(bytes);
        assertThat(bytes.toByteArray()).containsExactly((byte)2,(byte)3);
        verify(repository, times(2)).recordDownload("job");
    }
}
