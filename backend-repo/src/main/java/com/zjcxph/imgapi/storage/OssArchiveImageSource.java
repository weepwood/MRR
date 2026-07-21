package com.zjcxph.imgapi.storage;

import com.zjcxph.imgapi.config.ArchiveImageSourceProperties;
import com.zjcxph.imgapi.config.OssProperties;
import com.zjcxph.imgapi.entity.PathDO;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Locale;
import java.util.Objects;

@Component
@Order(10)
public class OssArchiveImageSource implements ArchiveImageSource {

    private final OssObjectReader objectReader;
    private final ArchiveImageSourceProperties properties;
    private final OssProperties ossProperties;
    private final SourcePermitGuard permitGuard;

    public OssArchiveImageSource(OssObjectReader objectReader,
                                 ArchiveImageSourceProperties properties,
                                 OssProperties ossProperties) {
        this.objectReader = objectReader;
        this.properties = properties;
        this.ossProperties = ossProperties;
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
            throw new IOException("OSS 图片缺少合法的 Object Key");
        }
        return key;
    }

    private String objectKey(PathDO image) {
        if (image == null) {
            return null;
        }
        String sourceRef = trimToNull(image.getSourceRef());
        if (sourceRef != null) {
            return validateRelativeKey(sourceRef);
        }
        return resolveLegacyOssUrl(image.getOssUrl());
    }

    private String resolveLegacyOssUrl(String value) {
        String reference = trimToNull(value);
        if (reference == null) {
            return null;
        }
        if (!looksLikeUrl(reference)) {
            return validateRelativeKey(reference);
        }

        final URI uri;
        try {
            uri = URI.create(reference);
        } catch (IllegalArgumentException exception) {
            return null;
        }
        if (!isHttp(uri) || !isAllowedOssHost(uri)) {
            return null;
        }

        String path = uri.getPath();
        if (path == null || path.isBlank()) {
            return null;
        }
        String key = stripConfiguredBasePath(uri, path);
        key = stripLeadingSlash(key);

        String bucket = trimToNull(ossProperties.getBucket());
        if (bucket != null && key.startsWith(bucket + "/")) {
            key = key.substring(bucket.length() + 1);
        }
        return validateRelativeKey(key);
    }

    private String stripConfiguredBasePath(URI uri, String path) {
        URI baseUri = parseConfiguredUri(ossProperties.getBaseUrl());
        if (baseUri == null || !sameAuthority(uri, baseUri)) {
            return path;
        }
        String basePath = baseUri.getPath();
        if (basePath == null || basePath.isBlank() || "/".equals(basePath)) {
            return path;
        }
        String normalizedBasePath = basePath.endsWith("/")
                ? basePath
                : basePath + "/";
        return path.startsWith(normalizedBasePath)
                ? path.substring(normalizedBasePath.length())
                : path;
    }

    private boolean isAllowedOssHost(URI uri) {
        String host = normalizeHost(uri.getHost());
        if (host == null) {
            return false;
        }

        URI baseUri = parseConfiguredUri(ossProperties.getBaseUrl());
        if (baseUri != null && Objects.equals(host, normalizeHost(baseUri.getHost()))) {
            return true;
        }

        URI endpointUri = parseConfiguredUri(ossProperties.getEndpoint());
        String endpointHost = endpointUri == null ? null : normalizeHost(endpointUri.getHost());
        if (endpointHost == null) {
            return false;
        }
        if (host.equals(endpointHost)) {
            return true;
        }
        String bucket = trimToNull(ossProperties.getBucket());
        return bucket != null && host.equals(bucket.toLowerCase(Locale.ROOT) + "." + endpointHost);
    }

    private URI parseConfiguredUri(String value) {
        String configured = trimToNull(value);
        if (configured == null) {
            return null;
        }
        try {
            return URI.create(looksLikeUrl(configured) ? configured : "https://" + configured);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean sameAuthority(URI left, URI right) {
        return Objects.equals(normalizeHost(left.getHost()), normalizeHost(right.getHost()))
                && effectivePort(left) == effectivePort(right);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "http".equalsIgnoreCase(uri.getScheme()) ? 80 : 443;
    }

    private boolean isHttp(URI uri) {
        return "http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme());
    }

    private String validateRelativeKey(String value) {
        String key = trimToNull(value);
        if (key == null) {
            return null;
        }
        key = stripLeadingSlash(key);
        if (key.isBlank()
                || key.contains("..")
                || key.contains("\\")
                || key.indexOf('\0') >= 0
                || looksLikeUrl(key)) {
            return null;
        }
        return key;
    }

    private String stripLeadingSlash(String value) {
        String result = value == null ? "" : value;
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        return result;
    }

    private boolean looksLikeUrl(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("http://") || normalized.startsWith("https://");
    }

    private String normalizeHost(String value) {
        return value == null || value.isBlank() ? null : value.toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
