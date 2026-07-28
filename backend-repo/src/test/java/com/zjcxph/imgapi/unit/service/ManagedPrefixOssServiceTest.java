package com.zjcxph.imgapi.unit.service;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.zjcxph.imgapi.common.ResultCode;
import com.zjcxph.imgapi.exception.OssErrorType;
import com.zjcxph.imgapi.exception.OssOperationException;
import com.zjcxph.imgapi.service.OssService;
import com.zjcxph.imgapi.service.impl.ManagedPrefixOssService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ManagedPrefixOssService OSS 安全边界与错误语义测试")
@ExtendWith(MockitoExtension.class)
class ManagedPrefixOssServiceTest {

    private static final String MANAGED_KEY = "medical-records/0012/00123456-00789124/0013.jpg";

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
        when(delegate.generateBrowserPresignedUrl(MANAGED_KEY))
                .thenReturn("https://oss.example.com/signed/0013.jpg");

        String signedUrl = service.generateBrowserPresignedUrl("  " + MANAGED_KEY + "  ");

        assertThat(signedUrl).isEqualTo("https://oss.example.com/signed/0013.jpg");
        verify(delegate).generateBrowserPresignedUrl(MANAGED_KEY);
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

    @Test
    @DisplayName("未初始化客户端映射为 OSS_NOT_CONFIGURED")
    void mapsUninitializedClientToNotConfigured() {
        when(delegate.generatePresignedUrl(MANAGED_KEY))
                .thenThrow(new IllegalStateException(
                        "OSS client is not initialized. Please configure OSS credentials."
                ));

        assertMappedFailure(
                () -> service.generatePresignedUrl(MANAGED_KEY),
                OssErrorType.OSS_NOT_CONFIGURED,
                ResultCode.OSS_NOT_CONFIGURED,
                HttpStatus.SERVICE_UNAVAILABLE
        );
    }

    @Test
    @DisplayName("上游 403 映射为 OSS_UNAUTHORIZED 且隐藏 SDK 原文")
    void mapsForbiddenToUnauthorizedWithoutLeakingDetails() {
        AmazonServiceException cause = serviceFailure(
                403,
                "AccessDenied",
                "Access denied for bucket hospital-private at http://10.0.0.10:9000"
        );
        when(delegate.browseObjects("medical-records/", null, 100))
                .thenThrow(new RuntimeException("OSS directory listing failed: internal endpoint", cause));

        assertThatThrownBy(() -> service.browseObjects("medical-records/", null, 100))
                .isInstanceOfSatisfying(OssOperationException.class, exception -> {
                    assertThat(exception.getType()).isEqualTo(OssErrorType.OSS_UNAUTHORIZED);
                    assertThat(exception.getCode()).isEqualTo(ResultCode.OSS_UNAUTHORIZED.getCode());
                    assertThat(exception.getMessage()).isEqualTo(ResultCode.OSS_UNAUTHORIZED.getMessage());
                    assertThat(exception.getMessage())
                            .doesNotContain("hospital-private", "10.0.0.10", "internal endpoint");
                    assertThat(exception.getCause()).isNotNull();
                });
    }

    @Test
    @DisplayName("上游 404 映射为 OSS_OBJECT_NOT_FOUND")
    void mapsNotFoundToObjectNotFound() {
        when(delegate.generateBrowserPresignedUrl(MANAGED_KEY))
                .thenThrow(serviceFailure(404, "NoSuchKey", "missing key: " + MANAGED_KEY));

        assertMappedFailure(
                () -> service.generateBrowserPresignedUrl(MANAGED_KEY),
                OssErrorType.OSS_OBJECT_NOT_FOUND,
                ResultCode.OSS_OBJECT_NOT_FOUND,
                HttpStatus.NOT_FOUND
        );
    }

    @Test
    @DisplayName("上游 5xx 映射为 OSS_UNAVAILABLE")
    void mapsServerFailureToUnavailable() {
        when(delegate.doesObjectExist(MANAGED_KEY))
                .thenThrow(serviceFailure(503, "ServiceUnavailable", "temporary backend outage"));

        assertMappedFailure(
                () -> service.doesObjectExist(MANAGED_KEY),
                OssErrorType.OSS_UNAVAILABLE,
                ResultCode.OSS_UNAVAILABLE,
                HttpStatus.SERVICE_UNAVAILABLE
        );
    }

    @Test
    @DisplayName("客户端超时映射为 OSS_UNAVAILABLE")
    void mapsTimeoutToUnavailable() {
        AmazonClientException clientFailure = new AmazonClientException(
                "request timed out for http://10.0.0.10:9000",
                new SocketTimeoutException("read timed out")
        );
        when(delegate.generatePresignedUrl(MANAGED_KEY)).thenThrow(clientFailure);

        assertThatThrownBy(() -> service.generatePresignedUrl(MANAGED_KEY))
                .isInstanceOfSatisfying(OssOperationException.class, exception -> {
                    assertThat(exception.getType()).isEqualTo(OssErrorType.OSS_UNAVAILABLE);
                    assertThat(exception.getMessage()).isEqualTo(ResultCode.OSS_UNAVAILABLE.getMessage());
                    assertThat(exception.getMessage()).doesNotContain("10.0.0.10");
                });
    }

    @Test
    @DisplayName("未知远程失败映射为 OSS_OPERATION_FAILED")
    void mapsUnknownRemoteFailureToOperationFailed() {
        when(delegate.generatePresignedUrl(MANAGED_KEY))
                .thenThrow(new RuntimeException("unexpected SDK detail for secret bucket"));

        assertMappedFailure(
                () -> service.generatePresignedUrl(MANAGED_KEY),
                OssErrorType.OSS_OPERATION_FAILED,
                ResultCode.OSS_OPERATION_FAILED,
                HttpStatus.BAD_GATEWAY
        );
    }

    @Test
    @DisplayName("对象不存在的正常 false 结果不会被改写成基础设施异常")
    void preservesNormalObjectAbsenceResult() {
        when(delegate.doesObjectExist(MANAGED_KEY)).thenReturn(false);

        assertThat(service.doesObjectExist(MANAGED_KEY)).isFalse();
    }

    @Test
    @DisplayName("完整性不一致继续返回 false 供迁移状态机标记 OBJECT_CONFLICT")
    void preservesIntegrityMismatchResult() {
        when(delegate.verifyUploadIntegrity(MANAGED_KEY, "0123456789abcdef0123456789abcdef"))
                .thenReturn(false);

        assertThat(service.verifyUploadIntegrity(
                MANAGED_KEY,
                "0123456789abcdef0123456789abcdef"
        )).isFalse();
    }

    @Test
    @DisplayName("调用方参数错误保持 400 语义，不包装为 OSS 基础设施错误")
    void preservesIllegalArgumentException() {
        when(delegate.browseObjects("medical-records/", null, 100))
                .thenThrow(new IllegalArgumentException("OSS 分页游标过长"));

        assertThatThrownBy(() -> service.browseObjects("medical-records/", null, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("OSS 分页游标过长");
    }

    private void assertMappedFailure(Runnable action,
                                     OssErrorType expectedType,
                                     ResultCode expectedCode,
                                     HttpStatus expectedStatus) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(OssOperationException.class, exception -> {
                    assertThat(exception.getType()).isEqualTo(expectedType);
                    assertThat(exception.getCode()).isEqualTo(expectedCode.getCode());
                    assertThat(exception.getMessage()).isEqualTo(expectedCode.getMessage());
                    assertThat(ResultCode.resolveHttpStatus(exception.getCode())).isEqualTo(expectedStatus);
                });
    }

    private AmazonServiceException serviceFailure(int statusCode, String errorCode, String message) {
        AmazonServiceException exception = new AmazonServiceException(message);
        exception.setStatusCode(statusCode);
        exception.setErrorCode(errorCode);
        return exception;
    }
}
