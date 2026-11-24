package com.zjcxph.imgapi.storage;

import com.zjcxph.imgapi.config.OssProperties;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OssObjectReaderTest {

    @Test
    void readsAndClosesV2ResponseStream() throws Exception {
        S3Client client = mock(S3Client.class);
        OssObjectReader reader = readerWith(client);
        AtomicBoolean closed = new AtomicBoolean();
        ByteArrayInputStream bytes = new ByteArrayInputStream(new byte[]{1, 2, 3}) {
            @Override
            public void close() throws java.io.IOException {
                closed.set(true);
                super.close();
            }
        };
        ResponseInputStream<GetObjectResponse> response = new ResponseInputStream<>(
                GetObjectResponse.builder().contentLength(3L).build(), bytes);
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(response);
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(3L).build());

        assertThat(reader.size("medical-records/test.jpg")).isEqualTo(3);
        try (InputStream input = reader.open("medical-records/test.jpg")) {
            assertThat(input).isInstanceOf(ImageReadStream.class);
            assertThat(((ImageReadStream) input).length()).isEqualTo(3L);
            assertThat(((ImageReadStream) input).seekable()).isFalse();
            assertThat(input.readAllBytes()).containsExactly(1, 2, 3);
        }
        assertThat(closed).isTrue();
        reader.close();
        verify(client).close();
    }

    @Test
    void abortsUnreadOssResponseInsteadOfDrainingIt() throws Exception {
        S3Client client = mock(S3Client.class);
        OssObjectReader reader = readerWith(client);
        var response = mock(ResponseInputStream.class);
        when(response.response()).thenReturn(GetObjectResponse.builder().contentLength(100L).build());
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(response);
        try (var ignored = reader.open("synthetic-object")) {
            // Diagnostic or rejected response closes before reading the object.
        }
        verify(response).abort();
        org.mockito.Mockito.verify(response, org.mockito.Mockito.never()).close();
    }

    @Test
    void realLoopbackSdkAbortDoesNotWaitForRemainingBody() throws Exception {
        var release = new java.util.concurrent.CountDownLatch(1);
        var workers = java.util.concurrent.Executors.newCachedThreadPool();
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(workers);
        server.createContext("/synthetic-bucket/slow", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(200, 100);
                exchange.getResponseBody().write(1);
                exchange.getResponseBody().flush();
                try { release.await(8, java.util.concurrent.TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        });
        server.createContext("/synthetic-bucket/fast", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(200, 1);
                exchange.getResponseBody().write(7);
            }
        });
        server.start();
        var properties = new OssProperties();
        properties.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setBucket("synthetic-bucket");
        properties.setRegion("us-east-1");
        properties.setPathStyleAccess(true);
        properties.setAccessKeyId("synthetic-test-key");
        properties.setAccessKeySecret("synthetic-test-secret");
        var reader = new OssObjectReader(properties);
        try {
            var result = workers.submit(() -> {
                try (var slow = reader.open("slow")) { assertThat(slow.read()).isEqualTo(1); }
                try (var fast = reader.open("fast")) { return fast.readAllBytes(); }
            });
            assertThat(result.get(5, java.util.concurrent.TimeUnit.SECONDS)).containsExactly((byte)7);
        } finally {
            release.countDown();
            reader.close();
            server.stop(0);
            workers.shutdownNow();
        }
    }

    @Test
    void wrapsRemoteFailureWithoutExposingEndpointInMessage() throws Exception {
        S3Client client = mock(S3Client.class);
        OssObjectReader reader = readerWith(client);
        when(client.getObject(any(GetObjectRequest.class))).thenThrow(
                S3Exception.builder().statusCode(403).message("private endpoint").build());

        assertThatThrownBy(() -> reader.open("medical-records/test.jpg"))
                .isInstanceOf(java.io.IOException.class)
                .hasMessage("OSS 图片读取失败")
                .hasCauseInstanceOf(S3Exception.class);
    }

    private OssObjectReader readerWith(S3Client client) throws Exception {
        OssProperties properties = mock(OssProperties.class);
        when(properties.getBucket()).thenReturn("test-bucket");
        OssObjectReader reader = new OssObjectReader(properties);
        java.lang.reflect.Field field = OssObjectReader.class.getDeclaredField("client");
        field.setAccessible(true);
        field.set(reader, client);
        return reader;
    }
}
