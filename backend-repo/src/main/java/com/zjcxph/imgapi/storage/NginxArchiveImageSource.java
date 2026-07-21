package com.zjcxph.imgapi.storage;

import com.zjcxph.imgapi.config.ArchiveImageSourceProperties;
import com.zjcxph.imgapi.config.ImageProperties;
import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.service.ImageUrlService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * 通过现有 Nginx 静态图片服务读取尚未迁移到 OSS 的影像。
 *
 * <p>该来源复用 {@link ImageUrlService} 的 BA01/BA02/BA03 路由规则，
 * 因此后台 ZIP/PDF 导出与浏览器页面看到的图片使用同一套地址。</p>
 */
@Component
@Order(25)
public class NginxArchiveImageSource implements ArchiveImageSource {

    private final ImageUrlService imageUrlService;
    private final ImageProperties imageProperties;
    private final ArchiveImageSourceProperties sourceProperties;
    private final SourcePermitGuard permitGuard;
    private final HttpClient client;

    public NginxArchiveImageSource(ImageUrlService imageUrlService,
                                   ImageProperties imageProperties,
                                   ArchiveImageSourceProperties sourceProperties) {
        this.imageUrlService = imageUrlService;
        this.imageProperties = imageProperties;
        this.sourceProperties = sourceProperties;
        this.permitGuard = new SourcePermitGuard(
                sourceProperties.getNginxMaxConcurrency(), sourceProperties.getAcquireTimeout());
        this.client = HttpClient.newBuilder()
                .connectTimeout(orDefault(sourceProperties.getNginxConnectTimeout(), Duration.ofSeconds(5)))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public boolean supports(PathDO image) {
        if (image == null) {
            return false;
        }
        String type = image.getSourceType() == null
                ? ""
                : image.getSourceType().trim().toUpperCase(Locale.ROOT);
        if (!(type.isEmpty() || "AUTO".equals(type) || "LOCAL".equals(type) || "OSS".equals(type))) {
            return false;
        }
        try {
            return resolveUri(image) != null;
        } catch (IOException exception) {
            return false;
        }
    }

    @Override
    public InputStream open(PathDO image) throws IOException {
        return permitGuard.open(() -> {
            HttpRequest.Builder request = HttpRequest.newBuilder(requireUri(image))
                    .timeout(orDefault(sourceProperties.getNginxReadTimeout(), Duration.ofSeconds(60)))
                    .header("Accept", "image/*,application/octet-stream;q=0.8")
                    .GET();
            applyBasicAuth(request);
            try {
                HttpResponse<InputStream> response = client.send(
                        request.build(), HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    response.body().close();
                    throw new IOException("Nginx 图片服务返回状态码 " + response.statusCode());
                }
                return response.body();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Nginx 图片读取被中断", exception);
            }
        });
    }

    @Override
    public long size(PathDO image) throws IOException {
        if (image.getFileSize() != null && image.getFileSize() >= 0) {
            return image.getFileSize();
        }
        return permitGuard.call(() -> {
            HttpRequest.Builder request = HttpRequest.newBuilder(requireUri(image))
                    .timeout(orDefault(sourceProperties.getNginxReadTimeout(), Duration.ofSeconds(60)))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody());
            applyBasicAuth(request);
            try {
                HttpResponse<Void> response = client.send(
                        request.build(), HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    return -1L;
                }
                return response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Nginx 图片大小查询被中断", exception);
            }
        });
    }

    @Override
    public String describeSource(PathDO image) {
        return "NGINX";
    }

    private URI requireUri(PathDO image) throws IOException {
        URI uri = resolveUri(image);
        if (uri == null) {
            throw new IOException("无法构建 Nginx 图片地址");
        }
        return uri;
    }

    private URI resolveUri(PathDO image) throws IOException {
        final String rawUrl;
        try {
            rawUrl = imageUrlService.buildImageUrl(image);
        } catch (RuntimeException exception) {
            throw new IOException("无法构建 Nginx 图片地址", exception);
        }
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }

        try {
            URL url = new URL(rawUrl);
            if (!("http".equalsIgnoreCase(url.getProtocol()) || "https".equalsIgnoreCase(url.getProtocol()))) {
                throw new IOException("Nginx 图片地址仅允许 HTTP/HTTPS");
            }
            URI uri = new URI(
                    url.getProtocol(),
                    null,
                    url.getHost(),
                    url.getPort(),
                    url.getPath(),
                    url.getQuery(),
                    null);
            if (uri.getHost() == null || !isConfiguredImageServer(uri)) {
                throw new IOException("Nginx 图片地址不属于已配置的图片服务器");
            }
            return uri;
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw new IOException("Nginx 图片地址格式不正确", exception);
        }
    }

    private boolean isConfiguredImageServer(URI candidate) {
        String authority = authority(candidate);
        return configuredImageUrls().stream()
                .map(this::parseConfiguredUri)
                .filter(uri -> uri != null)
                .map(this::authority)
                .anyMatch(authority::equals);
    }

    private List<String> configuredImageUrls() {
        return List.of(
                valueOrEmpty(imageProperties.getUrl()),
                valueOrEmpty(imageProperties.getServerUrlDefault()),
                valueOrEmpty(imageProperties.getServerUrlBa01()),
                valueOrEmpty(imageProperties.getServerUrlBa02()),
                valueOrEmpty(imageProperties.getServerUrlBa03()));
    }

    private URI parseConfiguredUri(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return URI.create(value.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String authority(URI uri) {
        int port = uri.getPort();
        if (port < 0) {
            port = "http".equalsIgnoreCase(uri.getScheme()) ? 80 : 443;
        }
        return uri.getScheme().toLowerCase(Locale.ROOT)
                + "://"
                + uri.getHost().toLowerCase(Locale.ROOT)
                + ":"
                + port;
    }

    private void applyBasicAuth(HttpRequest.Builder request) {
        String username = trimToNull(imageProperties.getUsername());
        if (username == null) {
            return;
        }
        String password = imageProperties.getPassword() == null ? "" : imageProperties.getPassword();
        String token = Base64.getEncoder().encodeToString(
                (username + ":" + password).getBytes(StandardCharsets.UTF_8));
        request.header("Authorization", "Basic " + token);
    }

    private Duration orDefault(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
