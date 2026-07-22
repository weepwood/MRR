package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.dto.resp.OssBrowserPageDTO;
import com.zjcxph.imgapi.service.OssService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * OSS 服务安全装饰器。
 *
 * <p>浏览器签名地址与通用签名地址会进入同一个底层缓存。该装饰器保证每次
 * 浏览器调用都先完成受管目录校验，再进入可能命中缓存的原始 OSS 服务，
 * 防止缓存命中绕过 {@code medical-records/} 边界。</p>
 */
@Service
@Primary
public class ManagedPrefixOssService implements OssService {

    private static final String BROWSER_ROOT_PREFIX = "medical-records/";

    private final OssService delegate;

    public ManagedPrefixOssService(@Qualifier("ossServiceImpl") OssService delegate) {
        this.delegate = delegate;
    }

    @Override
    public String uploadFile(String localFilePath, String ossKey) {
        return delegate.uploadFile(localFilePath, ossKey);
    }

    @Override
    public String uploadFile(String localFilePath, String ossKey, String sourceMd5) {
        return delegate.uploadFile(localFilePath, ossKey, sourceMd5);
    }

    @Override
    public String generatePresignedUrl(String ossKey) {
        return delegate.generatePresignedUrl(ossKey);
    }

    @Override
    public OssBrowserPageDTO browseObjects(String prefix, String continuationToken, int maxKeys) {
        return delegate.browseObjects(prefix, continuationToken, maxKeys);
    }

    @Override
    public String generateBrowserPresignedUrl(String ossKey) {
        return delegate.generateBrowserPresignedUrl(validateBrowserObjectKey(ossKey));
    }

    @Override
    public String calculateMd5(String filePath) {
        return delegate.calculateMd5(filePath);
    }

    @Override
    public boolean doesObjectExist(String ossKey) {
        return delegate.doesObjectExist(ossKey);
    }

    @Override
    public void deleteObject(String ossKey) {
        delegate.deleteObject(ossKey);
    }

    @Override
    public long getFileSize(String filePath) {
        return delegate.getFileSize(filePath);
    }

    @Override
    public boolean verifyUploadIntegrity(String ossKey, String expectedMd5) {
        return delegate.verifyUploadIntegrity(ossKey, expectedMd5);
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
