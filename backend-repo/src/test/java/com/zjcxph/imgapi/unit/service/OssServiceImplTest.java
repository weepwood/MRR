package com.zjcxph.imgapi.unit.service;

import com.amazonaws.services.s3.AmazonS3;
import com.zjcxph.imgapi.config.OssProperties;
import com.zjcxph.imgapi.service.impl.OssServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;
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
        // Only stub getBucket() — it is the only common stub needed by all tests.
        // Other OssProperties getters (accessKeyId, endpoint, etc.) are only used
        // in @PostConstruct init(), which we bypass via reflection below.
        when(ossProperties.getBucket()).thenReturn("test-bucket");

        ossService = new OssServiceImpl(ossProperties);

        // Inject mock s3Client via reflection to bypass @PostConstruct init logic
        java.lang.reflect.Field field = OssServiceImpl.class.getDeclaredField("s3Client");
        field.setAccessible(true);
        field.set(ossService, amazonS3);
    }

    @Test
    @DisplayName("generatePresignedUrl — 生成带签名的临时 URL")
    void generatePresignedUrl_returnsSignedUrl() throws Exception {
        String ossKey = "scans/2026/test.jpg";
        URL mockUrl = new URL("https://oss.example.com/test-bucket/scans/2026/test.jpg?signature=abc123");
        when(amazonS3.generatePresignedUrl(any())).thenReturn(mockUrl);
        when(ossProperties.getUrlExpireSeconds()).thenReturn(3600);

        String signedUrl = ossService.generatePresignedUrl(ossKey);

        assertThat(signedUrl).isNotNull();
        assertThat(signedUrl).contains("https://oss.example.com");
        verify(amazonS3).generatePresignedUrl(any());
    }

    @Test
    @DisplayName("deleteObject — 不抛异常即成功")
    void deleteObject_doesNotThrow() {
        ossService.deleteObject("scans/2026/delete-me.jpg");

        verify(amazonS3).deleteObject(anyString(), anyString());
    }
}
