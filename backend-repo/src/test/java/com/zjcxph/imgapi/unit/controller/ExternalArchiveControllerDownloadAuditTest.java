package com.zjcxph.imgapi.unit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.controller.ExternalArchiveController;
import com.zjcxph.imgapi.dto.resp.ExternalArchiveCaseDTO;
import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.security.ExternalArchiveGrant;
import com.zjcxph.imgapi.service.ArchiveExportService;
import com.zjcxph.imgapi.service.ExternalArchiveAccessService;
import com.zjcxph.imgapi.service.ImageContentService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalArchiveControllerDownloadAuditTest {

    @Mock private ObjectMapper objectMapper;
    @Mock private ExternalArchiveAccessService externalAccess;
    @Mock private ArchiveExportService exports;
    @Mock private ImageContentService images;
    @Mock private HttpServletRequest request;

    private ExternalArchiveController controller;
    private ExternalArchiveGrant grant;
    private ArchiveExportService.BatchZipExport prepared;

    @BeforeEach
    void setUp() {
        controller = new ExternalArchiveController(objectMapper, externalAccess, exports, images);
        grant = new ExternalArchiveGrant(
                "his-test-client", "synthetic-user", true,
                System.currentTimeMillis() + 60_000L,
                List.of(new ExternalArchiveCaseDTO("123456", "1234", null, null, null)));
        prepared = new ArchiveExportService.BatchZipExport(List.of(new PathDO()));
    }

    @Test
    void recordsSuccessOnlyAfterStreamingCompletes() throws Exception {
        prepareDownload();
        doAnswer(invocation -> {
            OutputStream output = invocation.getArgument(1);
            output.write("zip-content".getBytes(StandardCharsets.UTF_8));
            return null;
        }).when(exports).writeBatchZip(eq(prepared), any(OutputStream.class));

        ResponseEntity<StreamingResponseBody> response = controller.download("123456", "1234", request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("private, no-store");
        verify(externalAccess, never()).recordAudit(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        response.getBody().writeTo(output);

        assertThat(output.toString(StandardCharsets.UTF_8)).isEqualTo("zip-content");
        verify(externalAccess).recordAudit(
                grant, "123456", "1234", "ARCHIVE_DOWNLOAD", null,
                "10.0.0.8", "JUnit", "request-42", "SUCCESS", "images=1");
        verify(externalAccess, never()).recordAudit(
                grant, "123456", "1234", "ARCHIVE_DOWNLOAD", null,
                "10.0.0.8", "JUnit", "request-42", "FAILED", "images=1");
    }

    @Test
    void recordsFailedOutcomeAndRethrowsWhenStreamingFails() throws Exception {
        prepareDownload();
        doThrow(new IOException("simulated broken stream"))
                .when(exports).writeBatchZip(eq(prepared), any(OutputStream.class));

        ResponseEntity<StreamingResponseBody> response = controller.download("123456", "1234", request);
        assertThatThrownBy(() -> response.getBody().writeTo(new ByteArrayOutputStream()))
                .isInstanceOf(IOException.class)
                .hasMessage("simulated broken stream");

        verify(externalAccess).recordAudit(
                grant, "123456", "1234", "ARCHIVE_DOWNLOAD", null,
                "10.0.0.8", "JUnit", "request-42", "FAILED", "images=1");
        verify(externalAccess, never()).recordAudit(
                grant, "123456", "1234", "ARCHIVE_DOWNLOAD", null,
                "10.0.0.8", "JUnit", "request-42", "SUCCESS", "images=1");
    }

    private void prepareDownload() {
        when(request.getCookies()).thenReturn(new Cookie[]{
                new Cookie(ExternalArchiveAccessService.SESSION_COOKIE_NAME, "synthetic-session")
        });
        when(request.getRemoteAddr()).thenReturn("10.0.0.8");
        when(request.getHeader("User-Agent")).thenReturn("JUnit");
        when(request.getHeader("X-Request-Id")).thenReturn("request-42");
        when(externalAccess.requireSession("synthetic-session")).thenReturn(grant);
        when(exports.prepareArchive("123456", "1234")).thenReturn(prepared);
    }
}
