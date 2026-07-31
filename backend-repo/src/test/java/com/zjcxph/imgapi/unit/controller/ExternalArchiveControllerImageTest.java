package com.zjcxph.imgapi.unit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.controller.ExternalArchiveController;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.security.ExternalArchiveGrant;
import com.zjcxph.imgapi.service.ArchiveExportService;
import com.zjcxph.imgapi.service.ExternalArchiveAccessService;
import com.zjcxph.imgapi.service.ImageContentService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ExternalArchiveController 外部影像流式接口测试")
@ExtendWith(MockitoExtension.class)
class ExternalArchiveControllerImageTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ExternalArchiveAccessService externalArchiveAccessService;

    @Mock
    private ArchiveExportService archiveExportService;

    @Mock
    private ImageContentService imageContentService;

    @Mock
    private HttpServletRequest request;

    private ExternalArchiveController controller;

    @BeforeEach
    void setUp() {
        controller = new ExternalArchiveController(
                objectMapper,
                externalArchiveAccessService,
                archiveExportService,
                imageContentService
        );
    }

    @Test
    @DisplayName("授权后直接返回私有禁缓存影像流且不再重定向到底层地址")
    void streamsAuthorizedImageWithoutRedirect() throws Exception {
        ExternalArchiveGrant grant = new ExternalArchiveGrant(
                "his-client",
                "doctor-001",
                false,
                System.currentTimeMillis() + 60_000,
                List.of()
        );
        Scan scan = new Scan();
        scan.setId(42);
        scan.setBah("123456");
        scan.setSjh("A-001");

        byte[] payload = new byte[]{1, 3, 5, 7};
        TrackingInputStream input = new TrackingInputStream(payload);
        when(request.getCookies()).thenReturn(new Cookie[]{
                new Cookie(ExternalArchiveAccessService.SESSION_COOKIE_NAME, "session-token")
        });
        when(request.getRemoteAddr()).thenReturn("10.0.0.8");
        when(request.getHeader("User-Agent")).thenReturn("JUnit");
        when(request.getHeader("X-Request-Id")).thenReturn("request-42");
        when(externalArchiveAccessService.requireSession("session-token")).thenReturn(grant);
        when(externalArchiveAccessService.requireImage(grant, 42)).thenReturn(scan);
        when(imageContentService.open(42)).thenReturn(new ImageContentService.ImageContent(
                input,
                "病案首页-01.jpg",
                MediaType.IMAGE_JPEG,
                (long) payload.length
        ));

        ResponseEntity<StreamingResponseBody> response = controller.image(42, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getLocation()).isNull();
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_JPEG);
        assertThat(response.getHeaders().getContentLength()).isEqualTo(payload.length);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("private, no-store");
        assertThat(response.getHeaders().getFirst(HttpHeaders.PRAGMA)).isEqualTo("no-cache");
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeaders().getFirst("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(response.getHeaders().getContentDisposition().getType()).isEqualTo("inline");
        assertThat(response.getHeaders().getContentDisposition().getFilename())
                .isEqualTo("病案首页-01.jpg");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(response.getBody()).isNotNull();
        response.getBody().writeTo(output);

        assertThat(output.toByteArray()).containsExactly(payload);
        assertThat(input.isClosed()).isTrue();
        verify(imageContentService).open(42);
        verify(externalArchiveAccessService).recordAudit(
                grant,
                "123456",
                "A-001",
                "IMAGE_VIEW",
                42,
                "10.0.0.8",
                "JUnit",
                "request-42",
                "SUCCESS",
                null
        );
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {
        private boolean closed;

        private TrackingInputStream(byte[] buffer) {
            super(buffer);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }

        private boolean isClosed() {
            return closed;
        }
    }
}
