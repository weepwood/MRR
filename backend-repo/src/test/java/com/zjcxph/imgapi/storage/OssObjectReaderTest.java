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
                GetObjectResponse.builder().build(), bytes);
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(response);
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(3L).build());

        assertThat(reader.size("medical-records/test.jpg")).isEqualTo(3);
        try (InputStream input = reader.open("medical-records/test.jpg")) {
            assertThat(input.readAllBytes()).containsExactly(1, 2, 3);
        }
        assertThat(closed).isTrue();
        reader.close();
        verify(client).close();
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
