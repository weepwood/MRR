package com.zjcxph.imgapi.storage;

import com.zjcxph.imgapi.config.ArchiveImageSourceProperties;
import com.zjcxph.imgapi.entity.PathDO;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@Order(20)
public class HttpArchiveImageSource implements ArchiveImageSource {

    private final ArchiveImageSourceProperties properties;
    private final SourcePermitGuard permitGuard;
    private final ConcurrentHashMap<String, HttpClient> clients = new ConcurrentHashMap<>();

    public HttpArchiveImageSource(ArchiveImageSourceProperties properties) {
        this.properties = properties;
        this.permitGuard = new SourcePermitGuard(
                properties.getHttpMaxConcurrency(), properties.getAcquireTimeout());
    }

    @Override
    public boolean supports(PathDO image) {
        return image != null
                && "HTTP".equalsIgnoreCase(image.getSourceType())
                && image.getSourceNode() != null
                && properties.getHttpNodes().containsKey(image.getSourceNode());
    }

    @Override
    public InputStream open(PathDO image) throws IOException {
        return permitGuard.open(() -> {
            ArchiveImageSourceProperties.HttpNode node = requireNode(image);
            HttpRequest.Builder request = HttpRequest.newBuilder(resolveUri(node, image))
                    .timeout(orDefault(node.getReadTimeout(), Duration.ofSeconds(60)))
                    .GET();
            applyAuth(node, request);
            try {
                HttpResponse<InputStream> response = client(image.getSourceNode(), node).send(
                        request.build(), HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    response.body().close();
                    throw new IOException("HTTP 图片节点返回状态码 " + response.statusCode());
                }
                return response.body();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("HTTP 图片读取被中断", exception);
            }
        });
    }

    @Override
    public long size(PathDO image) throws IOException {
        if (image.getFileSize() != null && image.getFileSize() >= 0) {
            return image.getFileSize();
        }
        return permitGuard.call(() -> {
            ArchiveImageSourceProperties.HttpNode node = requireNode(image);
            HttpRequest.Builder request = HttpRequest.newBuilder(resolveUri(node, image))
                    .timeout(orDefault(node.getReadTimeout(), Duration.ofSeconds(60)))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody());
            applyAuth(node, request);
            try {
                HttpResponse<Void> response = client(image.getSourceNode(), node).send(
                        request.build(), HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() < 200 || response.statusCode() >= 400) {
                    return -1L;
                }
                return response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("HTTP 图片大小查询被中断", exception);
            }
        });
    }

    @Override
    public String describeSource(PathDO image) {
        return "HTTP";
    }

    private ArchiveImageSourceProperties.HttpNode requireNode(PathDO image) throws IOException {
        ArchiveImageSourceProperties.HttpNode node = properties.getHttpNodes().get(image.getSourceNode());
        if (node == null || node.getBaseUrl() == null || node.getBaseUrl().isBlank()) {
            throw new IOException("HTTP 图片节点未配置: " + image.getSourceNode());
        }
        return node;
    }

    private URI resolveUri(ArchiveImageSourceProperties.HttpNode node, PathDO image) throws IOException {
        Path relative = ArchiveImagePathSupport.relativePath(image);
        String safePath = Arrays.stream(relative.toString().replace('\\', '/').split("/"))
                .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(Collectors.joining("/"));
        String base = node.getBaseUrl().trim();
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            throw new IOException("HTTP 图片节点仅允许 http/https 协议");
        }
        if (!base.endsWith("/")) {
            base += "/";
        }
        URI baseUri = URI.create(base);
        if (baseUri.getHost() == null) {
            throw new IOException("HTTP 图片节点地址无效");
        }
        return baseUri.resolve(safePath);
    }

    private HttpClient client(String sourceNode, ArchiveImageSourceProperties.HttpNode node) {
        Duration connectTimeout = orDefault(node.getConnectTimeout(), Duration.ofSeconds(5));
        String key = sourceNode + "|" + node.getBaseUrl().trim() + "|" + connectTimeout.toMillis();
        return clients.computeIfAbsent(key, ignored -> HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    private void applyAuth(ArchiveImageSourceProperties.HttpNode node, HttpRequest.Builder request) {
        if (node.getAuthValue() != null && !node.getAuthValue().isBlank()) {
            String header = node.getAuthHeader() == null || node.getAuthHeader().isBlank()
                    ? "Authorization"
                    : node.getAuthHeader().trim();
            request.header(header, node.getAuthValue());
        }
    }

    private Duration orDefault(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }
}
