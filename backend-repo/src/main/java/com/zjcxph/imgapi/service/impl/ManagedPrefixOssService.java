package com.zjcxph.imgapi.service.impl;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.zjcxph.imgapi.dto.resp.OssBrowserPageDTO;
import com.zjcxph.imgapi.exception.OssErrorType;
import com.zjcxph.imgapi.exception.OssOperationException;
import com.zjcxph.imgapi.service.OssService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * OSS 服务安全装饰器。
 *
 * <p>浏览器签名地址与通用签名地址会进入同一个底层缓存。该装饰器保证每次
 * 浏览器调用都先完成受管目录校验，再进入可能命中缓存的原始 OSS 服务，
 * 防止缓存命中绕过 {@code medical-records/} 边界。</p>
 *
 * <p>所有远程 OSS 调用同时在此边界映射为稳定、脱敏的业务错误；底层异常
 * 作为 cause 保留给服务端日志，但不会把 endpoint、bucket、Object Key 或 SDK
 * 原始消息直接返回给客户端。</p>
 */
@Service
@Primary
public class ManagedPrefixOssService implements OssService {

    private static final Logger logger = LoggerFactory.getLogger(ManagedPrefixOssService.class);
    private static final String BROWSER_ROOT_PREFIX = "medical-records/";

    private final OssService delegate;

    public ManagedPrefixOssService(@Qualifier("ossServiceImpl") OssService delegate) {
        this.delegate = delegate;
    }

    @Override
    public String uploadFile(String localFilePath, String ossKey) {
        return executeRemote("upload", () -> delegate.uploadFile(localFilePath, ossKey));
    }

    @Override
    public String uploadFile(String localFilePath, String ossKey, String sourceMd5) {
        return executeRemote("upload", () -> delegate.uploadFile(localFilePath, ossKey, sourceMd5));
    }

    @Override
    public String generatePresignedUrl(String ossKey) {
        return executeRemote("presign", () -> delegate.generatePresignedUrl(ossKey));
    }

    @Override
    public OssBrowserPageDTO browseObjects(String prefix, String continuationToken, int maxKeys) {
        return executeRemote("browse", () -> delegate.browseObjects(prefix, continuationToken, maxKeys));
    }

    @Override
    public String generateBrowserPresignedUrl(String ossKey) {
        String safeKey = validateBrowserObjectKey(ossKey);
        return executeRemote("browser-presign", () -> delegate.generateBrowserPresignedUrl(safeKey));
    }

    @Override
    public String calculateMd5(String filePath) {
        return delegate.calculateMd5(filePath);
    }

    @Override
    public boolean doesObjectExist(String ossKey) {
        return executeRemote("exists", () -> delegate.doesObjectExist(ossKey));
    }

    @Override
    public void deleteObject(String ossKey) {
        executeRemote("delete", () -> {
            delegate.deleteObject(ossKey);
            return null;
        });
    }

    @Override
    public long getFileSize(String filePath) {
        return delegate.getFileSize(filePath);
    }

    @Override
    public boolean verifyUploadIntegrity(String ossKey, String expectedMd5) {
        return executeRemote(
                "verify-integrity",
                () -> delegate.verifyUploadIntegrity(ossKey, expectedMd5)
        );
    }

    private <T> T executeRemote(String operation, Supplier<T> action) {
        try {
            return action.get();
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (OssOperationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            OssOperationException mapped = mapFailure(exception);
            logger.warn(
                    "OSS remote operation failed: operation={}, type={}",
                    operation,
                    mapped.getType(),
                    exception
            );
            throw mapped;
        }
    }

    private OssOperationException mapFailure(RuntimeException failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof OssOperationException operationException) {
                return operationException;
            }
            if (current instanceof AmazonServiceException serviceException) {
                return new OssOperationException(classifyServiceFailure(serviceException), failure);
            }
            if (current instanceof SocketTimeoutException
                    || current instanceof ConnectException
                    || current instanceof SocketException
                    || current instanceof InterruptedIOException
                    || current instanceof AmazonClientException) {
                return new OssOperationException(OssErrorType.OSS_UNAVAILABLE, failure);
            }
            if (current instanceof IllegalStateException
                    && containsAny(current.getMessage(), "not initialized", "尚未配置", "未完成配置")) {
                return new OssOperationException(OssErrorType.OSS_NOT_CONFIGURED, failure);
            }
            current = current.getCause();
        }

        String message = safeLowerMessage(failure);
        if (containsAny(message, "not initialized", "尚未配置", "未完成配置")) {
            return new OssOperationException(OssErrorType.OSS_NOT_CONFIGURED, failure);
        }
        if (containsAny(
                message,
                "timeout",
                "timed out",
                "connection reset",
                "connection refused",
                "connection closed",
                "连接超时",
                "连接失败",
                "连接中断"
        )) {
            return new OssOperationException(OssErrorType.OSS_UNAVAILABLE, failure);
        }
        if (containsAny(
                message,
                "access denied",
                "forbidden",
                "invalidaccesskeyid",
                "signaturedoesnotmatch",
                "权限不足",
                "凭据无效"
        )) {
            return new OssOperationException(OssErrorType.OSS_UNAUTHORIZED, failure);
        }
        if (containsAny(message, "nosuchkey", "object not found", "对象不存在")) {
            return new OssOperationException(OssErrorType.OSS_OBJECT_NOT_FOUND, failure);
        }
        return new OssOperationException(OssErrorType.OSS_OPERATION_FAILED, failure);
    }

    private OssErrorType classifyServiceFailure(AmazonServiceException exception) {
        int statusCode = exception.getStatusCode();
        String errorCode = safeLowerMessage(exception.getErrorCode());

        if (statusCode == 401
                || statusCode == 403
                || containsAny(
                errorCode,
                "accessdenied",
                "invalidaccesskeyid",
                "signaturedoesnotmatch",
                "expiredtoken"
        )) {
            return OssErrorType.OSS_UNAUTHORIZED;
        }
        if (statusCode == 404 || containsAny(errorCode, "nosuchkey", "notfound")) {
            return OssErrorType.OSS_OBJECT_NOT_FOUND;
        }
        if (statusCode == 408 || statusCode == 429 || statusCode >= 500) {
            return OssErrorType.OSS_UNAVAILABLE;
        }
        return OssErrorType.OSS_OPERATION_FAILED;
    }

    private boolean containsAny(String value, String... fragments) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (String fragment : fragments) {
            if (normalized.contains(fragment.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String safeLowerMessage(Object value) {
        return value == null ? "" : value.toString().toLowerCase(Locale.ROOT);
    }

    private String validateBrowserObjectKey(String ossKey) {
        String normalized = ossKey == null ? null : ossKey.trim();
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException("OSS Object Key 不能为空");
        }
        if (!normalized.startsWith(BROWSER_ROOT_PREFIX)) {
            throw new IllegalArgumentException("只允许访问 medical-records/ 下的 OSS 文件");
        }
        if (normalized.equals(BROWSER_ROOT_PREFIX)
                || normalized.endsWith("/")
                || normalized.indexOf('\\') >= 0
                || normalized.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("OSS 浏览签名地址只能用于具体文件");
        }

        String relative = normalized.substring(BROWSER_ROOT_PREFIX.length());
        for (String segment : relative.split("/", -1)) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("OSS Object Key 包含非法相对路径");
            }
        }
        return normalized;
    }
}
