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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    void externalContextAndInvalidTicketResponsesCarryPrivacyHeaders() throws Exception {
        var reqGrant=grant();
        when(externalArchiveAccessService.requireSession("session-token")).thenReturn(reqGrant);
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller)
                .addFilters(new com.zjcxph.imgapi.filter.ExternalArchivePrivacyFilter())
                .setControllerAdvice(new com.zjcxph.imgapi.handler.GlobalExceptionHandler()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/external/archive/context")
                        .cookie(new Cookie(ExternalArchiveAccessService.SESSION_COOKIE_NAME,"session-token")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control","private, no-store"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Referrer-Policy","no-referrer"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/integration/archive/tickets"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control","private, no-store"));
    }

    @Test
    void oldAuthorizedMetadataCannotOpenDifferentCurrentArchive() throws Exception {
        var granted = new com.zjcxph.imgapi.dto.resp.ExternalArchiveCaseDTO();
        granted.setBah("123456"); granted.setSjh("A-001");
        var grant = new ExternalArchiveGrant("synthetic","synthetic",false,System.currentTimeMillis()+60000,List.of(granted));
        var actual = scan();
        actual.setBah("654321"); actual.setSjh("B-002");
        var mapper = org.mockito.Mockito.mock(com.zjcxph.imgapi.mapper.ImageContentMapper.class);
        var storage = org.mockito.Mockito.mock(com.zjcxph.imgapi.storage.ImageStorage.class);
        when(mapper.findActiveById(42)).thenReturn(actual);
        org.mockito.Mockito.lenient().when(storage.open(org.mockito.ArgumentMatchers.any())).thenReturn(new ByteArrayInputStream(new byte[]{9}));
        var real = new ExternalArchiveController(objectMapper,externalArchiveAccessService,archiveExportService,
                new com.zjcxph.imgapi.service.impl.ImageContentServiceImpl(mapper,storage));
        var req = new MockHttpServletRequest("GET","/api/v1/external/archive/image/42");
        req.setCookies(new Cookie(ExternalArchiveAccessService.SESSION_COOKIE_NAME,"session-token"));
        when(externalArchiveAccessService.requireSession("session-token")).thenReturn(grant);
        assertThatThrownBy(()->real.image(42,req)).isInstanceOfSatisfying(
                com.zjcxph.imgapi.exception.BusinessException.class,e->assertThat(e.getCode()).isEqualTo(403));
        org.mockito.Mockito.verifyNoInteractions(storage);
    }

    @Test
    @DisplayName("授权后直接返回私有禁缓存影像流且不再重定向到底层地址")
    void streamsAuthorizedImageWithoutRedirect() throws Exception {
        ExternalArchiveGrant grant = grant();
        Scan scan = scan();
        byte[] payload = new byte[]{1, 3, 5, 7};
        TrackingInputStream input = new TrackingInputStream(payload);
        prepareRequestAndAuthorization(grant, scan);
        when(imageContentService.openExternal(42, grant)).thenReturn(new ImageContentService.ScopedImage(new ImageContentService.ImageContent(
                input,
                "病案首页-01.jpg",
                MediaType.IMAGE_JPEG,
                (long) payload.length
        ), "123456", "A-001"));

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
        verify(imageContentService).openExternal(42, grant);
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

    @Test
    @DisplayName("上游读取失败时关闭流并记录失败审计")
    void recordsFailedAuditWhenStreamingFails() {
        ExternalArchiveGrant grant = grant();
        Scan scan = scan();
        FailingInputStream input = new FailingInputStream();
        prepareRequestAndAuthorization(grant, scan);
        when(imageContentService.openExternal(42, grant)).thenReturn(new ImageContentService.ScopedImage(new ImageContentService.ImageContent(
                input,
                "病案首页-01.jpg",
                MediaType.IMAGE_JPEG,
                null
        ), "123456", "A-001"));

        ResponseEntity<StreamingResponseBody> response = controller.image(42, request);

        assertThat(response.getBody()).isNotNull();
        assertThatThrownBy(() -> response.getBody().writeTo(new ByteArrayOutputStream()))
                .isInstanceOf(IOException.class)
                .hasMessage("simulated read failure");
        assertThat(input.isClosed()).isTrue();
        verify(externalArchiveAccessService).recordAudit(
                grant,
                "123456",
                "A-001",
                "IMAGE_VIEW",
                42,
                "10.0.0.8",
                "JUnit",
                "request-42",
                "FAILED",
                null
        );
    }

    @Test
    void transferBudgetRejectsBytesReturningAfterDeadlineAndAuditsFailure() throws Exception {
        var grant = grant();
        var scan = scan();
        prepareRequestAndAuthorization(grant, scan);
        var closed = new java.util.concurrent.atomic.AtomicBoolean();
        var input = new InputStream() {
            private boolean readOnce;
            @Override public int read() throws IOException {
                if (readOnce) return -1;
                readOnce = true;
                try { Thread.sleep(100); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
                }
                return 1;
            }
            @Override public int read(byte[] bytes, int offset, int count) throws IOException {
                int value = read();
                if (value < 0) return -1;
                bytes[offset] = (byte) value;
                return 1;
            }
            @Override public void close() { closed.set(true); }
        };
        when(imageContentService.openExternal(42, grant)).thenReturn(new ImageContentService.ScopedImage(new ImageContentService.ImageContent(
                input, "synthetic.jpg", MediaType.IMAGE_JPEG, null, null, java.time.Duration.ofMillis(40)), "123456", "A-001"));
        var response = controller.image(42, request);
        var out = new ByteArrayOutputStream();
        assertThatThrownBy(() -> response.getBody().writeTo(out))
                .isInstanceOf(java.net.SocketTimeoutException.class);
        assertThat(out.size()).isZero();
        assertThat(closed).isTrue();
        verify(externalArchiveAccessService).recordAudit(grant, "123456", "A-001", "IMAGE_VIEW", 42,
                "10.0.0.8", "JUnit", "request-42", "FAILED", null);
    }

    @Test
    void externalSingleRangePreservesAuthorizationAndAuditsSuccessfulTransfer() throws Exception {
        var grant = grant();
        var scan = scan();
        var root = java.nio.file.Files.createTempDirectory("mrr-external-range");
        var file = root.resolve("synthetic.jpg");
        java.nio.file.Files.write(file, new byte[]{0, 1, 2, 3, 4, 5});
        var properties = new com.zjcxph.imgapi.config.ArchiveImageSourceProperties();
        properties.setNasMaxConcurrency(1);
        properties.setAcquireTimeout(java.time.Duration.ofMillis(100));
        var node = new com.zjcxph.imgapi.config.ArchiveImageSourceProperties.NasNode();
        node.setRoot(root.toString());
        properties.setNasNodes(java.util.Map.of("test", node));
        var path = new com.zjcxph.imgapi.entity.PathDO();
        path.setSourceType("NAS");
        path.setSourceNode("test");
        path.setSourceRef("synthetic.jpg");
        var source = new com.zjcxph.imgapi.storage.NasArchiveImageSource(properties);
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/api/v1/external/archive/image/42");
        servletRequest.setCookies(new Cookie(ExternalArchiveAccessService.SESSION_COOKIE_NAME, "session-token"));
        servletRequest.setRemoteAddr("10.0.0.8");
        servletRequest.addHeader("User-Agent", "JUnit");
        servletRequest.addHeader("X-Request-Id", "request-42");
        servletRequest.addHeader("Range", "bytes=-2");
        when(externalArchiveAccessService.requireSession("session-token")).thenReturn(grant);
        try (var stream = source.open(path)) {
            when(imageContentService.openExternal(42, grant)).thenReturn(new ImageContentService.ScopedImage(new ImageContentService.ImageContent(
                    stream, "synthetic.jpg", MediaType.IMAGE_JPEG, 999L), "123456", "A-001"));
            var response = controller.image(42, servletRequest);
            assertThat(response.getStatusCode().value()).isEqualTo(206);
            assertThat(response.getHeaders().getFirst("Content-Range")).isEqualTo("bytes 4-5/6");
            var out = new ByteArrayOutputStream();
            response.getBody().writeTo(out);
            assertThat(out.toByteArray()).containsExactly((byte) 4, (byte) 5);
            verify(imageContentService).openExternal(42, grant);
            verify(externalArchiveAccessService).recordAudit(grant, "123456", "A-001", "IMAGE_VIEW", 42,
                    "10.0.0.8", "JUnit", "request-42", "SUCCESS", null);
        } finally {
            java.nio.file.Files.deleteIfExists(file);
            java.nio.file.Files.deleteIfExists(root);
        }
    }

    @Test
    void rejectedExternalStreamClosesSourceAndAuditsFailureOnce() throws Exception {
        ExternalArchiveGrant grant = grant();
        Scan scan = scan();
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/api/v1/external/archive/image/42");
        servletRequest.setAsyncSupported(true);
        servletRequest.setCookies(new Cookie(ExternalArchiveAccessService.SESSION_COOKIE_NAME, "session-token"));
        servletRequest.setRemoteAddr("10.0.0.8");
        servletRequest.addHeader("User-Agent", "JUnit");
        servletRequest.addHeader("X-Request-Id", "request-42");
        when(externalArchiveAccessService.requireSession("session-token")).thenReturn(grant);
        TrackingInputStream input = new TrackingInputStream(new byte[]{1});
        when(imageContentService.openExternal(42, grant)).thenReturn(new ImageContentService.ScopedImage(new ImageContentService.ImageContent(
                input, "image.jpg", MediaType.IMAGE_JPEG, 1L), "123456", "A-001"));
        var response = controller.image(42, servletRequest);
        var manager = WebAsyncUtils.getAsyncManager(servletRequest);
        manager.setAsyncWebRequest(WebAsyncUtils.createAsyncWebRequest(servletRequest, new MockHttpServletResponse()));
        manager.setTaskExecutor(new AsyncTaskExecutor() {
            @Override
            public void execute(Runnable task) {
                throw new TaskRejectedException("synthetic rejection");
            }
        });
        manager.startCallableProcessing(() -> {
            response.getBody().writeTo(new ByteArrayOutputStream());
            return null;
        });
        assertThat(manager.getConcurrentResult()).isInstanceOf(TaskRejectedException.class);
        servletRequest.getAsyncContext().complete();
        assertThat(input.isClosed()).isTrue();
        assertThatThrownBy(() -> response.getBody().writeTo(new ByteArrayOutputStream()))
                .isInstanceOf(IOException.class);
        verify(externalArchiveAccessService).recordAudit(grant, "123456", "A-001", "IMAGE_VIEW", 42,
                "10.0.0.8", "JUnit", "request-42", "FAILED", null);
    }

    private void prepareRequestAndAuthorization(ExternalArchiveGrant grant, Scan scan) {
        when(request.getCookies()).thenReturn(new Cookie[]{
                new Cookie(ExternalArchiveAccessService.SESSION_COOKIE_NAME, "session-token")
        });
        when(request.getRemoteAddr()).thenReturn("10.0.0.8");
        when(request.getHeader("User-Agent")).thenReturn("JUnit");
        when(request.getHeader("X-Request-Id")).thenReturn("request-42");
        when(externalArchiveAccessService.requireSession("session-token")).thenReturn(grant);
    }

    private ExternalArchiveGrant grant() {
        return new ExternalArchiveGrant(
                "his-client",
                "doctor-001",
                false,
                System.currentTimeMillis() + 60_000,
                List.of()
        );
    }

    private Scan scan() {
        Scan scan = new Scan();
        scan.setId(42);
        scan.setBah("123456");
        scan.setSjh("A-001");
        return scan;
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

    private static final class FailingInputStream extends InputStream {
        private boolean closed;

        @Override
        public int read() throws IOException {
            throw new IOException("simulated read failure");
        }

        @Override
        public void close() {
            closed = true;
        }

        private boolean isClosed() {
            return closed;
        }
    }
}
