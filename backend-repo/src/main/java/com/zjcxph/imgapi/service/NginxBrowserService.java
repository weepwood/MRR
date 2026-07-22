package com.zjcxph.imgapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.config.ArchiveImageSourceProperties;
import com.zjcxph.imgapi.config.ImageProperties;
import com.zjcxph.imgapi.dto.resp.NginxBrowserPageDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通过 Nginx autoindex 以只读方式浏览静态资源目录。
 *
 * <p>支持 {@code autoindex_format json} 和 Nginx 默认 HTML 索引。所有请求只能落在
 * IMAGE_SERVER_URL_DEFAULT、IMAGE_SERVER_URL_BA01/02/03 配置的根路径内。</p>
 */
@Service
public class NginxBrowserService {

    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 500;
    private static final int MAX_DIRECTORY_RESPONSE_BYTES = 8 * 1024 * 1024;
    private static final Pattern HTML_LINK_PATTERN = Pattern.compile(
            "(?is)<a\\s+[^>]*href\\s*=\\s*([\"'])(.*?)\\1[^>]*>.*?</a>");

    private final ImageProperties imageProperties;
    private final ArchiveImageSourceProperties sourceProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public NginxBrowserService(ImageProperties imageProperties,
                               ArchiveImageSourceProperties sourceProperties,
                               ObjectMapper objectMapper) {
        this(
                imageProperties,
                sourceProperties,
                objectMapper,
                HttpClient.newBuilder()
                        .connectTimeout(validDuration(sourceProperties.getNginxConnectTimeout(), Duration.ofSeconds(5)))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build()
        );
    }

    NginxBrowserService(ImageProperties imageProperties,
                        ArchiveImageSourceProperties sourceProperties,
                        ObjectMapper objectMapper,
                        HttpClient httpClient) {
        this.imageProperties = imageProperties;
        this.sourceProperties = sourceProperties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public List<NginxBrowserPageDTO.Server> listServers() {
        return serverDefinitions().stream()
                .map(server -> new NginxBrowserPageDTO.Server(
                        server.key(),
                        server.name(),
                        server.baseUri() == null ? null : server.baseUri().toString(),
                        server.baseUri() != null
                ))
                .toList();
    }

    public NginxBrowserPageDTO browse(String serverKey, String path, int offset, int limit) {
        ServerDefinition server = requireConfiguredServer(serverKey);
        String normalizedPath = normalizeRelativePath(path, true);
        int safeOffset = Math.max(0, offset);
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        URI directoryUri = resolve(server.baseUri(), normalizedPath, true);

        HttpRequest.Builder request = HttpRequest.newBuilder(directoryUri)
                .timeout(validDuration(sourceProperties.getNginxReadTimeout(), Duration.ofSeconds(60)))
                .header("Accept", "application/json,text/html;q=0.9,*/*;q=0.5")
                .GET();
        applyBasicAuth(request);

        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new NginxBrowserException("Nginx 目录读取被中断", exception);
        } catch (IOException exception) {
            throw new NginxBrowserException("无法连接 Nginx 图片服务器", exception);
        }

        byte[] body;
        try (InputStream input = response.body()) {
            ensureDirectoryResponseStatus(response.statusCode());
            body = readBounded(input, MAX_DIRECTORY_RESPONSE_BYTES);
        } catch (IOException exception) {
            throw new NginxBrowserException(exception.getMessage(), exception);
        }

        String contentType = response.headers().firstValue("Content-Type").orElse("");
        List<NginxBrowserPageDTO.Entry> allEntries = parseDirectoryBody(
                server,
                directoryUri,
                normalizedPath,
                body,
                contentType
        );

        int fromIndex = Math.min(safeOffset, allEntries.size());
        int toIndex = Math.min(fromIndex + safeLimit, allEntries.size());
        List<NginxBrowserPageDTO.Entry> entries = List.copyOf(allEntries.subList(fromIndex, toIndex));
        int loadedDirectories = (int) entries.stream().filter(NginxBrowserPageDTO.Entry::directory).count();
        int loadedFiles = entries.size() - loadedDirectories;
        long loadedBytes = entries.stream()
                .filter(entry -> !entry.directory() && entry.size() > 0)
                .mapToLong(NginxBrowserPageDTO.Entry::size)
                .sum();

        return new NginxBrowserPageDTO(
                server.key(),
                server.name(),
                server.baseUri().toString(),
                normalizedPath,
                entries,
                fromIndex,
                safeLimit,
                allEntries.size(),
                toIndex < allEntries.size(),
                loadedDirectories,
                loadedFiles,
                loadedBytes
        );
    }

    public RemoteFile openFile(String serverKey, String path) {
        ServerDefinition server = requireConfiguredServer(serverKey);
        String normalizedPath = normalizeRelativePath(path, false);
        if (normalizedPath.isEmpty() || normalizedPath.endsWith("/")) {
            throw new IllegalArgumentException("文件路径不能为空或指向目录");
        }
        URI fileUri = resolve(server.baseUri(), normalizedPath, false);
        HttpRequest.Builder request = HttpRequest.newBuilder(fileUri)
                .timeout(validDuration(sourceProperties.getNginxReadTimeout(), Duration.ofSeconds(60)))
                .header("Accept", "*/*")
                .GET();
        applyBasicAuth(request);

        try {
            HttpResponse<InputStream> response = httpClient.send(
                    request.build(),
                    HttpResponse.BodyHandlers.ofInputStream()
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                response.body().close();
                throw new NginxBrowserException(statusMessage(response.statusCode(), false));
            }
            return new RemoteFile(
                    response.body(),
                    response.headers().firstValue("Content-Type").orElse("application/octet-stream"),
                    response.headers().firstValueAsLong("Content-Length").orElse(-1L),
                    fileName(normalizedPath),
                    response.headers().firstValue("ETag").orElse(null),
                    response.headers().firstValue("Last-Modified").orElse(null)
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new NginxBrowserException("Nginx 文件读取被中断", exception);
        } catch (IOException exception) {
            throw new NginxBrowserException("无法读取 Nginx 文件", exception);
        }
    }

    private List<NginxBrowserPageDTO.Entry> parseDirectoryBody(ServerDefinition server,
                                                                 URI directoryUri,
                                                                 String currentPath,
                                                                 byte[] body,
                                                                 String contentType) {
        String text = new String(body, StandardCharsets.UTF_8);
        String trimmed = text.stripLeading();
        List<NginxBrowserPageDTO.Entry> entries;
        if (contentType.toLowerCase(Locale.ROOT).contains("json") || trimmed.startsWith("[")) {
            entries = parseJsonDirectory(currentPath, text);
        } else {
            entries = parseHtmlDirectory(server, directoryUri, currentPath, text);
        }
        if (entries.isEmpty() && looksLikeNonIndexPage(text)) {
            throw new NginxBrowserException(
                    "Nginx 未返回目录索引，请在对应 location 中启用 autoindex on（推荐同时配置 autoindex_format json）"
            );
        }
        return entries.stream()
                .filter(Objects::nonNull)
                .collect(
                        LinkedHashMap<String, NginxBrowserPageDTO.Entry>::new,
                        (map, entry) -> map.putIfAbsent(entry.path(), entry),
                        LinkedHashMap::putAll
                )
                .values()
                .stream()
                .sorted(Comparator
                        .comparing(NginxBrowserPageDTO.Entry::directory).reversed()
                        .thenComparing(entry -> entry.name().toLowerCase(Locale.ROOT)))
                .toList();
    }

    private List<NginxBrowserPageDTO.Entry> parseJsonDirectory(String currentPath, String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (!root.isArray()) {
                throw new NginxBrowserException("Nginx JSON 目录索引格式不正确");
            }
            List<NginxBrowserPageDTO.Entry> entries = new ArrayList<>();
            for (JsonNode node : root) {
                String name = node.path("name").asText("");
                String type = node.path("type").asText("");
                boolean directory = "directory".equalsIgnoreCase(type) || name.endsWith("/");
                String childPath = joinChildPath(currentPath, name, directory);
                if (childPath == null) {
                    continue;
                }
                entries.add(new NginxBrowserPageDTO.Entry(
                        fileName(childPath),
                        childPath,
                        directory,
                        directory ? 0L : Math.max(0L, node.path("size").asLong(0L)),
                        parseInstant(node.path("mtime").asText(null))
                ));
            }
            return entries;
        } catch (NginxBrowserException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new NginxBrowserException("无法解析 Nginx JSON 目录索引", exception);
        }
    }

    private List<NginxBrowserPageDTO.Entry> parseHtmlDirectory(ServerDefinition server,
                                                                 URI directoryUri,
                                                                 String currentPath,
                                                                 String body) {
        List<NginxBrowserPageDTO.Entry> entries = new ArrayList<>();
        Matcher matcher = HTML_LINK_PATTERN.matcher(body);
        while (matcher.find()) {
            String href = decodeBasicHtmlEntities(matcher.group(2));
            if (href == null || href.isBlank() || href.startsWith("?") || href.startsWith("#")) {
                continue;
            }
            try {
                URI resolved = directoryUri.resolve(href);
                if (!sameOrigin(server.baseUri(), resolved)) {
                    continue;
                }
                String basePath = trailingSlash(server.baseUri().getPath());
                String resolvedPath = resolved.getPath();
                if (resolvedPath == null || !resolvedPath.startsWith(basePath)) {
                    continue;
                }
                boolean directory = resolvedPath.endsWith("/");
                String relativePath = normalizeRelativePath(resolvedPath.substring(basePath.length()), directory);
                if (relativePath.isEmpty() || relativePath.equals(currentPath)) {
                    continue;
                }
                if (!parentPath(relativePath).equals(currentPath)) {
                    continue;
                }
                entries.add(new NginxBrowserPageDTO.Entry(
                        fileName(relativePath),
                        relativePath,
                        directory,
                        0L,
                        null
                ));
            } catch (IllegalArgumentException ignored) {
                // 跳过目录页中不属于当前受控根路径的链接。
            }
        }
        return entries;
    }

    private String joinChildPath(String currentPath, String rawName, boolean directory) {
        if (rawName == null) {
            return null;
        }
        String name = rawName.trim();
        while (name.endsWith("/")) {
            name = name.substring(0, name.length() - 1);
        }
        if (name.isEmpty() || ".".equals(name) || "..".equals(name)
                || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || containsControlCharacter(name)) {
            return null;
        }
        return currentPath + name + (directory ? "/" : "");
    }

    private void ensureDirectoryResponseStatus(int statusCode) throws IOException {
        if (statusCode >= 200 && statusCode < 300) {
            return;
        }
        throw new IOException(statusMessage(statusCode, true));
    }

    private String statusMessage(int statusCode, boolean directory) {
        String target = directory ? "目录" : "文件";
        return switch (statusCode) {
            case 401 -> "Nginx " + target + "需要认证，请检查 IMAGE_USERNAME / IMAGE_PASSWORD";
            case 403 -> "Nginx 拒绝访问" + target + "，目录浏览需启用 autoindex on 并允许后端服务器访问";
            case 404 -> "Nginx " + target + "不存在";
            default -> statusCode >= 300 && statusCode < 400
                    ? "Nginx 返回重定向，浏览器仅允许配置根路径内的直接访问"
                    : "Nginx " + target + "服务返回状态码 " + statusCode;
        };
    }

    private byte[] readBounded(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, 64 * 1024));
        byte[] buffer = new byte[16 * 1024];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > maxBytes) {
                throw new IOException("Nginx 目录索引过大，请缩小目录层级或拆分静态资源目录");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private ServerDefinition requireConfiguredServer(String key) {
        String normalizedKey = key == null || key.isBlank() ? "default" : key.trim().toLowerCase(Locale.ROOT);
        ServerDefinition server = serverDefinitions().stream()
                .filter(candidate -> candidate.key().equals(normalizedKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知的 Nginx 图片服务器：" + normalizedKey));
        if (server.baseUri() == null) {
            throw new IllegalStateException(server.name() + " 未配置访问地址");
        }
        return server;
    }

    private List<ServerDefinition> serverDefinitions() {
        return List.of(
                server("default", "默认图片服务器", imageProperties.getServerUrlDefault()),
                server("ba01", "BA01 图片服务器", imageProperties.getServerUrlBa01()),
                server("ba02", "BA02 图片服务器", imageProperties.getServerUrlBa02()),
                server("ba03", "BA03 图片服务器", imageProperties.getServerUrlBa03())
        );
    }

    private ServerDefinition server(String key, String name, String rawBaseUrl) {
        return new ServerDefinition(key, name, parseBaseUri(rawBaseUrl));
    }

    private URI parseBaseUri(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            URI raw = URI.create(value.trim());
            String scheme = raw.getScheme() == null ? "" : raw.getScheme().toLowerCase(Locale.ROOT);
            if (!("http".equals(scheme) || "https".equals(scheme)) || raw.getHost() == null) {
                throw new IllegalStateException("Nginx 图片服务器地址仅允许 HTTP/HTTPS 完整 URL");
            }
            if (raw.getUserInfo() != null || raw.getQuery() != null || raw.getFragment() != null) {
                throw new IllegalStateException("Nginx 图片服务器根地址不能包含凭据、查询参数或片段");
            }
            return new URI(
                    scheme,
                    null,
                    raw.getHost(),
                    raw.getPort(),
                    trailingSlash(raw.getPath()),
                    null,
                    null
            );
        } catch (IllegalArgumentException | URISyntaxException exception) {
            throw new IllegalStateException("Nginx 图片服务器地址格式不正确", exception);
        }
    }

    private URI resolve(URI baseUri, String relativePath, boolean directory) {
        String path = trailingSlash(baseUri.getPath());
        if (!relativePath.isEmpty()) {
            path += relativePath;
        }
        if (directory) {
            path = trailingSlash(path);
        }
        try {
            URI resolved = new URI(
                    baseUri.getScheme(),
                    null,
                    baseUri.getHost(),
                    baseUri.getPort(),
                    path,
                    null,
                    null
            );
            if (!sameOrigin(baseUri, resolved) || !resolved.getPath().startsWith(trailingSlash(baseUri.getPath()))) {
                throw new IllegalArgumentException("路径超出已配置的 Nginx 静态资源根目录");
            }
            return resolved;
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Nginx 文件路径格式不正确", exception);
        }
    }

    private String normalizeRelativePath(String value, boolean directory) {
        String path = value == null ? "" : value.trim();
        if (path.indexOf('\\') >= 0 || path.indexOf('\0') >= 0 || path.indexOf('?') >= 0 || path.indexOf('#') >= 0) {
            throw new IllegalArgumentException("Nginx 路径包含非法字符");
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.isEmpty()) {
            return "";
        }
        StringBuilder normalized = new StringBuilder();
        for (String segment : path.split("/")) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)
                    || containsControlCharacter(segment) || segment.contains(":")) {
                throw new IllegalArgumentException("Nginx 路径包含非法目录段");
            }
            if (!normalized.isEmpty()) {
                normalized.append('/');
            }
            normalized.append(segment);
        }
        if (directory) {
            normalized.append('/');
        }
        return normalized.toString();
    }

    private boolean sameOrigin(URI left, URI right) {
        return left != null
                && right != null
                && left.getScheme().equalsIgnoreCase(right.getScheme())
                && left.getHost().equalsIgnoreCase(right.getHost())
                && effectivePort(left) == effectivePort(right);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "http".equalsIgnoreCase(uri.getScheme()) ? 80 : 443;
    }

    private String parentPath(String path) {
        String withoutTrailingSlash = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int separator = withoutTrailingSlash.lastIndexOf('/');
        return separator < 0 ? "" : withoutTrailingSlash.substring(0, separator + 1);
    }

    private String fileName(String path) {
        String normalized = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int separator = normalized.lastIndexOf('/');
        return separator < 0 ? normalized : normalized.substring(separator + 1);
    }

    private String trailingSlash(String value) {
        String path = value == null || value.isBlank() ? "/" : value;
        return path.endsWith("/") ? path : path + "/";
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            try {
                return OffsetDateTime.parse(value).toInstant();
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }

    private boolean looksLikeNonIndexPage(String body) {
        String lower = body == null ? "" : body.toLowerCase(Locale.ROOT);
        return lower.contains("<html") && !HTML_LINK_PATTERN.matcher(body).find();
    }

    private String decodeBasicHtmlEntities(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }

    private boolean containsControlCharacter(String value) {
        return value.chars().anyMatch(character -> character < 32 || character == 127);
    }

    private void applyBasicAuth(HttpRequest.Builder request) {
        String username = trimToNull(imageProperties.getUsername());
        if (username == null) {
            return;
        }
        String password = imageProperties.getPassword() == null ? "" : imageProperties.getPassword();
        String token = Base64.getEncoder().encodeToString(
                (username + ":" + password).getBytes(StandardCharsets.UTF_8)
        );
        request.header("Authorization", "Basic " + token);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Duration validDuration(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }

    private record ServerDefinition(String key, String name, URI baseUri) {
    }

    public record RemoteFile(
            InputStream inputStream,
            String contentType,
            long contentLength,
            String fileName,
            String etag,
            String lastModified
    ) {
    }

    public static class NginxBrowserException extends RuntimeException {
        public NginxBrowserException(String message) {
            super(message);
        }

        public NginxBrowserException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
