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
        when(service.requireDownloadFile(session, "job")).thenReturn(file);
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
