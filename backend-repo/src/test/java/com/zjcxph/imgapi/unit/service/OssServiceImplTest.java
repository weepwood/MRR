package com.zjcxph.imgapi.unit.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.zjcxph.imgapi.config.OssProperties;
import com.zjcxph.imgapi.service.impl.OssServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URL;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("OssServiceImpl OSS 服务测试")
@ExtendWith(MockitoExtension.class)
class OssServiceImplTest {

    @Mock
    private OssProperties ossProperties;

    @Mock
    private AmazonS3 amazonS3;

    private OssServiceImpl ossService;

    @BeforeEach
    void setUp() throws Exception {
        when(ossProperties.getBucket()).thenReturn("test-bucket");
        ossService = new OssServiceImpl(ossProperties);

        java.lang.reflect.Field field = OssServiceImpl.class.getDeclaredField("s3Client");
        field.setAccessible(true);
        field.set(ossService, amazonS3);
    }

    @Test
    @DisplayName("generatePresignedUrl — 生成带签名的临时 URL")
    void generatePresignedUrlReturnsSignedUrl() throws Exception {
        String ossKey = "scans/2026/test.jpg";
        URL mockUrl = new URL(
                "https://oss.example.com/test-bucket/scans/2026/test.jpg?signature=abc123"
        );
        when(amazonS3.generatePresignedUrl(any())).thenReturn(mockUrl);
        when(ossProperties.getUrlExpireSeconds()).thenReturn(3600);

        String signedUrl = ossService.generatePresignedUrl(ossKey);

        assertThat(signedUrl).contains("https://oss.example.com");
        verify(amazonS3).generatePresignedUrl(any());
    }

    @Test
    @DisplayName("deleteObject — 不抛异常即成功")
    void deleteObjectDoesNotThrow() {
        ossService.deleteObject("scans/2026/delete-me.jpg");

        verify(amazonS3).deleteObject(anyString(), anyString());
    }

    @Test
    @DisplayName("对象存在性检查异常不能被当成对象不存在")
    void objectExistenceFailureIsPropagated() {
        when(amazonS3.doesObjectExist("test-bucket", "medical-records/test.jpg"))
                .thenThrow(new RuntimeException("network timeout"));

        assertThatThrownBy(() -> ossService.doesObjectExist("medical-records/test.jpg"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("existence check failed");
    }

    @Test
    @DisplayName("优先使用上传时保存的源文件 MD5 元数据校验同名对象")
    void verifiesExistingObjectWithSourceMd5Metadata() {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setUserMetadata(Map.of("source-md5", "abc123"));
        when(amazonS3.getObjectMetadata("test-bucket", "medical-records/test.jpg"))
                .thenReturn(metadata);

        boolean verified = ossService.verifyUploadIntegrity(
                "medical-records/test.jpg",
                "abc123"
        );

        assertThat(verified).isTrue();
    }
}
