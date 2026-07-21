package com.zjcxph.imgapi.storage;

import com.zjcxph.imgapi.config.ArchiveImageSourceProperties;
import com.zjcxph.imgapi.entity.PathDO;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
@Order(10)
public class OssArchiveImageSource implements ArchiveImageSource {

    private final OssObjectReader objectReader;
    private final ArchiveImageSourceProperties properties;
    private final SourcePermitGuard permitGuard;

    public OssArchiveImageSource(OssObjectReader objectReader, ArchiveImageSourceProperties properties) {
        this.objectReader = objectReader;
        this.properties = properties;
        this.permitGuard = new SourcePermitGuard(
                properties.getOssMaxConcurrency(), properties.getAcquireTimeout());
    }

    @Override
    public boolean supports(PathDO image) {
        if (image == null) {
            return false;
        }
        String type = image.getSourceType() == null ? "" : image.getSourceType().trim();
        if ("OSS".equalsIgnoreCase(type)) {
            return objectKey(image) != null;
        }
        return properties.isPreferOss()
                && (type.isEmpty() || "AUTO".equalsIgnoreCase(type))
                && objectKey(image) != null;
    }

    @Override
    public InputStream open(PathDO image) throws IOException {
        String key = requireObjectKey(image);
        return permitGuard.open(() -> objectReader.open(key));
    }

    @Override
    public long size(PathDO image) throws IOException {
        if (image.getFileSize() != null && image.getFileSize() >= 0) {
            return image.getFileSize();
        }
        String key = requireObjectKey(image);
        return permitGuard.call(() -> objectReader.size(key));
    }

    @Override
    public String describeSource(PathDO image) {
        return "OSS";
    }

    private String requireObjectKey(PathDO image) throws IOException {
        String key = objectKey(image);
        if (key == null) {
            throw new IOException("OSS 图片缺少 Object Key");
        }
        if (key.startsWith("/") || key.contains("..") || key.contains("\\")) {
            throw new IOException("OSS Object Key 不合法");
        }
        return key;
    }

    private String objectKey(PathDO image) {
        if (image == null) {
            return null;
        }
        String value = image.getSourceRef();
        if (value == null || value.isBlank()) {
            value = image.getOssUrl();
        }
        return value == null || value.isBlank() ? null : value.trim();
    }
}
