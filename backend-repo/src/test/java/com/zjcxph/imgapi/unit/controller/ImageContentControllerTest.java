package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.controller.ImageContentController;
import com.zjcxph.imgapi.service.ImageContentService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("ImageContentController 内部影像内容接口测试")
@ExtendWith(MockitoExtension.class)
class ImageContentControllerTest {

    @Mock
    private ImageContentService imageContentService;

    private ImageContentController controller;

    @BeforeEach
    void setUp() {
        controller = new ImageContentController(imageContentService);
    }

    @Test
    @DisplayName("以私有禁缓存响应逐块输出并关闭上游流")
    void streamsPrivateNoStoreContentAndClosesSource() throws Exception {
        byte[] payload = new byte[]{1, 2, 3, 4, 5};
        TrackingInputStream input = new TrackingInputStream(payload);
        when(imageContentService.open(12)).thenReturn(new ImageContentService.ImageContent(
                input,
                "病案首页-01.jpg",
                MediaType.IMAGE_JPEG,
                (long) payload.length
        ));

        ResponseEntity<StreamingResponseBody> response = controller.getContent(12);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_JPEG);
        assertThat(response.getHeaders().getContentLength()).isEqualTo(payload.length);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("private, no-store");
        assertThat(response.getHeaders().getPragma()).isEqualTo("no-cache");
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
    }

    @Test
    @DisplayName("未知文件大小时不发送错误的 Content-Length")
    void omitsContentLengthWhenUnknown() {
        when(imageContentService.open(13)).thenReturn(new ImageContentService.ImageContent(
                new ByteArrayInputStream(new byte[]{9}),
                "image-13",
                MediaType.APPLICATION_OCTET_STREAM,
                null
        ));

        ResponseEntity<StreamingResponseBody> response = controller.getContent(13);

        assertThat(response.getHeaders().containsKey(HttpHeaders.CONTENT_LENGTH)).isFalse();
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
