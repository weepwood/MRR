package com.zjcxph.imgapi.unit.service;

import com.sun.net.httpserver.HttpServer;
import com.zjcxph.imgapi.config.OssProperties;
import com.zjcxph.imgapi.dto.resp.OssBrowserPageDTO;
import com.zjcxph.imgapi.service.impl.OssServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URL;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("OssServiceImpl OSS 服务测试")
@ExtendWith(MockitoExtension.class)
class OssServiceImplTest {

    @Mock private OssProperties ossProperties;
    @Mock private S3Client s3Client;
    @Mock private S3Presigner s3Presigner;
    @Mock private PresignedGetObjectRequest presignedRequest;
    @TempDir Path tempDir;

    private OssServiceImpl ossService;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(ossProperties.getBucket()).thenReturn("test-bucket");
        ossService = new OssServiceImpl(ossProperties);
        setField("s3Client", s3Client);
        setField("s3Presigner", s3Presigner);
    }

    @Test
    @DisplayName("临时下载链接由 v2 签名器生成且保留有效期")
    void generatePresignedUrlReturnsSignedUrl() throws Exception {
        when(ossProperties.getUrlExpireSeconds()).thenReturn(3600);
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(presignedRequest);
        when(presignedRequest.url()).thenReturn(new URL("https://oss.example.com/signed/test.jpg"));

        String signedUrl = ossService.generatePresignedUrl("scans/2026/test.jpg");

        assertThat(signedUrl).isEqualTo("https://oss.example.com/signed/test.jpg");
        ArgumentCaptor<GetObjectPresignRequest> captor = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(captor.capture());
        assertThat(captor.getValue().signatureDuration().getSeconds()).isEqualTo(3600);
        assertThat(captor.getValue().getObjectRequest().bucket()).isEqualTo("test-bucket");
        assertThat(captor.getValue().getObjectRequest().key()).isEqualTo("scans/2026/test.jpg");
    }

    @Test
    @DisplayName("OSS 浏览保留 prefix、delimiter 和 continuation token")
    void browseObjectsUsesVirtualDirectoryPagination() {
        when(ossProperties.getEndpoint()).thenReturn("oss.example.com");
        when(ossProperties.getRegion()).thenReturn("test-region");
        S3Object file = S3Object.builder()
                .key("medical-records/0012/0013.jpg")
                .size(235_000L)
                .eTag("etag-0013")
                .storageClass("STANDARD")
                .lastModified(Instant.parse("2026-07-22T10:00:00Z"))
                .build();
        ListObjectsV2Response listing = ListObjectsV2Response.builder()
                .commonPrefixes(CommonPrefix.builder()
                        .prefix("medical-records/0012/00123456-00789124/").build())
                .contents(file)
                .isTruncated(true)
                .nextContinuationToken("next-token")
                .build();
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listing);

        OssBrowserPageDTO page = ossService.browseObjects("medical-records/0012", "current-token", 200);

        ArgumentCaptor<ListObjectsV2Request> captor = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client).listObjectsV2(captor.capture());
        ListObjectsV2Request request = captor.getValue();
        assertThat(request.bucket()).isEqualTo("test-bucket");
        assertThat(request.prefix()).isEqualTo("medical-records/0012/");
        assertThat(request.delimiter()).isEqualTo("/");
        assertThat(request.continuationToken()).isEqualTo("current-token");
        assertThat(request.maxKeys()).isEqualTo(200);
        assertThat(page.getEntries()).hasSize(2);
        assertThat(page.getEntries().getFirst().isDirectory()).isTrue();
        assertThat(page.getEntries().getLast().getName()).isEqualTo("0013.jpg");
        assertThat(page.getLoadedBytes()).isEqualTo(235_000);
        assertThat(page.getNextContinuationToken()).isEqualTo("next-token");
        assertThat(page.isTruncated()).isTrue();
    }

    @Test
    @DisplayName("兼容未返回对象大小的 S3 兼容目录结果")
    void browseObjectsDefaultsMissingSizeToZero() {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder()
                        .contents(S3Object.builder().key("medical-records/test.jpg").build())
                        .build());

        OssBrowserPageDTO page = ossService.browseObjects("medical-records/", null, 100);

        assertThat(page.getEntries()).hasSize(1);
        assertThat(page.getEntries().getFirst().getSize()).isZero();
    }

    @Test
    @DisplayName("OSS 浏览和签名只接受受管路径")
    void browserRejectsKeysOutsideManagedPrefix() {
        assertThatThrownBy(() -> ossService.browseObjects("private/", null, 100))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("medical-records");
        assertThatThrownBy(() -> ossService.generateBrowserPresignedUrl("medical-records/../secret.txt"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("相对路径");
    }

    @Test
    @DisplayName("删除使用当前桶与指定对象键")
    void deleteObjectDoesNotThrow() {
        ossService.deleteObject("scans/2026/delete-me.jpg");
        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(captor.getValue().key()).isEqualTo("scans/2026/delete-me.jpg");
    }

    @Test
    @DisplayName("HEAD 404 表示对象不存在，其他上游错误不能误判为不存在")
    void objectExistenceDistinguishesNotFoundFromFailure() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).build())
                .thenThrow(S3Exception.builder().statusCode(503).build());

        assertThat(ossService.doesObjectExist("medical-records/test.jpg")).isFalse();
        assertThatThrownBy(() -> ossService.doesObjectExist("medical-records/test.jpg"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("OSS object existence check failed")
                .hasCauseInstanceOf(S3Exception.class);
    }

    @Test
    @DisplayName("优先用上传时保存的源文件 MD5 元数据校验同名对象")
    void verifiesExistingObjectWithSourceMd5Metadata() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().metadata(Map.of("source-md5", "abc123")).build());

        assertThat(ossService.verifyUploadIntegrity("medical-records/test.jpg", "abc123")).isTrue();
        assertThat(ossService.verifyUploadIntegrity("medical-records/test.jpg", "different")).isFalse();
    }

    @Test
    @DisplayName("上传使用调用方已计算的 MD5 并写入元数据")
    void uploadUsesPrecomputedMd5() throws Exception {
        Path file = tempDir.resolve("test.jpg");
        Files.write(file, new byte[]{1, 2, 3});
        String md5 = "0123456789abcdef0123456789abcdef";
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().eTag("etag").build());

        assertThat(ossService.uploadFile(file.toString(), "medical-records/test.jpg", md5))
                .isEqualTo("medical-records/test.jpg");
        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().contentMD5()).isEqualTo("ASNFZ4mrze8BI0VniavN7w==");
        assertThat(captor.getValue().metadata()).containsEntry("source-md5", md5);
        assertThat(captor.getValue().contentLength()).isEqualTo(3);
    }

    @Test
    @DisplayName("参数错误不把本地路径或对象键写进异常消息")
    void invalidArgumentsDoNotExposePaths() {
        String localPath = "C:\\private\\patient-file.jpg";
        String objectKey = "medical-records/private-object.jpg";

        assertThatThrownBy(() -> ossService.uploadFile(localPath, objectKey, "0123456789abcdef0123456789abcdef"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Local file does not exist");
        assertThatThrownBy(() -> ossService.verifyUploadIntegrity(objectKey, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Expected MD5 is empty");
    }

    @Test
    @DisplayName("v2 客户端保持自定义 endpoint、path-style、MD5 和预签名地址")
    void realClientKeepsS3CompatibleWireContract() throws Exception {
        AtomicReference<String> putPath = new AtomicReference<>();
        AtomicReference<String> contentMd5 = new AtomicReference<>();
        AtomicReference<String> crc32 = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            if ("HEAD".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(404, -1);
            } else if ("PUT".equals(exchange.getRequestMethod())) {
                putPath.set(exchange.getRequestURI().getPath());
                contentMd5.set(exchange.getRequestHeaders().getFirst("Content-MD5"));
                crc32.set(exchange.getRequestHeaders().getFirst("x-amz-checksum-crc32"));
                exchange.getRequestBody().readAllBytes();
                exchange.getResponseHeaders().add("ETag", "\"etag-test\"");
                exchange.sendResponseHeaders(200, -1);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
            exchange.close();
        });
        server.start();
        OssServiceImpl realService = new OssServiceImpl(ossProperties);
        try {
            when(ossProperties.getAccessKeyId()).thenReturn("test-access");
            when(ossProperties.getAccessKeySecret()).thenReturn("test-secret");
            when(ossProperties.getEndpoint()).thenReturn("http://127.0.0.1:" + server.getAddress().getPort());
            when(ossProperties.getRegion()).thenReturn("us-east-1");
            when(ossProperties.isPathStyleAccess()).thenReturn(true);
            when(ossProperties.getUrlExpireSeconds()).thenReturn(3600);
            realService.init();

            assertThat(realService.doesObjectExist("medical-records/missing.jpg")).isFalse();
            assertThat(realService.generatePresignedUrl("medical-records/test.jpg"))
                    .contains("/test-bucket/medical-records/test.jpg", "X-Amz-Signature=");

            Path file = tempDir.resolve("wire-test.jpg");
            Files.writeString(file, "synthetic image bytes", StandardCharsets.UTF_8);
            String md5 = realService.calculateMd5(file.toString());
            realService.uploadFile(file.toString(), "medical-records/wire-test.jpg", md5);
            assertThat(putPath.get()).isEqualTo("/test-bucket/medical-records/wire-test.jpg");
            assertThat(contentMd5.get()).isNotBlank();
            assertThat(crc32.get()).isNull();
        } finally {
            realService.destroy();
            server.stop(0);
        }
    }

    private void setField(String name, Object value) throws Exception {
        java.lang.reflect.Field field = OssServiceImpl.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(ossService, value);
    }
}
