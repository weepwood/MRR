package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.service.OssService;
import com.zjcxph.imgapi.service.impl.ManagedPrefixOssService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ManagedPrefixOssService OSS 浏览安全边界测试")
@ExtendWith(MockitoExtension.class)
class ManagedPrefixOssServiceTest {

    @Mock
    private OssService delegate;

    private ManagedPrefixOssService service;

    @BeforeEach
    void setUp() {
        service = new ManagedPrefixOssService(delegate);
    }

    @Test
    @DisplayName("合法受管文件在进入底层缓存前完成校验")
    void browserSignedUrlDelegatesAfterValidation() {
        String key = "medical-records/0012/00123456-00789124/0013.jpg";
        when(delegate.generateBrowserPresignedUrl(key)).thenReturn("https://oss.example.com/signed/0013.jpg");

        String signedUrl = service.generateBrowserPresignedUrl("  " + key + "  ");

        assertThat(signedUrl).isEqualTo("https://oss.example.com/signed/0013.jpg");
        verify(delegate).generateBrowserPresignedUrl(key);
    }

    @Test
    @DisplayName("通用缓存中存在其他前缀时浏览器仍拒绝访问")
    void browserSignedUrlRejectsKeyOutsideManagedPrefix() {
        assertThatThrownBy(() -> service.generateBrowserPresignedUrl("private/secret.jpg"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("medical-records");

        verify(delegate, never()).generateBrowserPresignedUrl("private/secret.jpg");
    }

    @Test
    @DisplayName("浏览器签名拒绝相对路径和目录对象")
    void browserSignedUrlRejectsRelativePathAndDirectory() {
        assertThatThrownBy(() -> service.generateBrowserPresignedUrl(
                "medical-records/0012/../secret.jpg"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("相对路径");
        assertThatThrownBy(() -> service.generateBrowserPresignedUrl("medical-records/0012/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("具体文件");
    }
}
