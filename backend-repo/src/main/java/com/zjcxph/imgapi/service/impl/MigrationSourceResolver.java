package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.config.ImageProperties;
import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.storage.ImageStorage;
import com.zjcxph.imgapi.utils.MedicalRecordCodeUtils;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 为 OSS 迁移解析可读取的源文件。
 *
 * <p>本地文件可直接上传；NAS、HTTP、Nginx 等来源通过现有 ImageStorage
 * 读取并物化为单个临时文件，上传结束后立即删除。</p>
 */
@Component
public class MigrationSourceResolver {

    private static final Logger logger = LoggerFactory.getLogger(MigrationSourceResolver.class);
    private static final Set<String> DIRECT_LOCAL_TYPES = Set.of("", "AUTO", "LOCAL", "OSS");
    private static final String TEMP_DIRECTORY_NAME = "mrr-oss-migration";
    private static final Duration STALE_TEMP_AGE = Duration.ofHours(24);
    private static final int MAX_STARTUP_CLEANUP_FILES = 10_000;

    private final ImageStorage imageStorage;
    private final ImageProperties imageProperties;

    public MigrationSourceResolver(ImageStorage imageStorage, ImageProperties imageProperties) {
        this.imageStorage = imageStorage;
        this.imageProperties = imageProperties;
    }

    @PostConstruct
    public void cleanupStaleTemporaryFiles() {
        Path directory = tempDirectory();
        if (!Files.isDirectory(directory)) {
            return;
        }
        Instant cutoff = Instant.now().minus(STALE_TEMP_AGE);
        int deleted = 0;
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.limit(MAX_STARTUP_CLEANUP_FILES).toList()) {
                try {
                    if (Files.isRegularFile(file)
                            && Files.getLastModifiedTime(file).toInstant().isBefore(cutoff)
                            && Files.deleteIfExists(file)) {
                        deleted++;
                    }
                } catch (IOException exception) {
                    logger.debug("Unable to delete stale OSS migration temp file: {}", file, exception);
                }
            }
        } catch (IOException exception) {
            logger.warn("Unable to inspect OSS migration temp directory: {}", directory, exception);
        }
        if (deleted > 0) {
            logger.info("Deleted {} stale OSS migration temporary files", deleted);
        }
    }

    public ResolvedSource resolve(Scan scan) throws IOException {
        try {
            validateScan(scan);
            Path directPath = resolveDirectPath(scan);
            if (directPath != null && Files.isRegularFile(directPath) && Files.isReadable(directPath)) {
                return new ResolvedSource(directPath, directPath.toString(), false);
            }
        } catch (IOException | InvalidPathException exception) {
            throw new SourceResolutionException(safeMessage(exception), true, exception);
        }

        Path directory = tempDirectory();
        Path tempFile;
        try {
            Files.createDirectories(directory);
            tempFile = Files.createTempFile(
                    directory,
                    "scan-" + scan.getId() + "-",
                    safeSuffix(scan.getFilename())
            );
        } catch (IOException | RuntimeException exception) {
            throw new SourceResolutionException("无法创建 OSS 迁移临时文件", false, exception);
        }

        try (InputStream input = imageStorage.open(toPathDO(scan))) {
            Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING);
            if (!Files.isRegularFile(tempFile) || !Files.isReadable(tempFile)) {
                throw new IOException("多来源图片读取后未生成可读临时文件");
            }
            return new ResolvedSource(tempFile, describe(scan), true);
        } catch (IOException | RuntimeException exception) {
            deleteQuietly(tempFile);
            FailureNature nature = classifyFailure(exception);
            throw new SourceResolutionException(
                    safeMessage(exception),
                    nature == FailureNature.PERMANENT,
                    exception
            );
        }
    }

    public boolean canRead(Scan scan) {
        try {
            validateScan(scan);
            Path directPath = resolveDirectPath(scan);
            if (directPath != null && Files.isRegularFile(directPath) && Files.isReadable(directPath)) {
                return true;
            }
            try (InputStream input = imageStorage.open(toPathDO(scan))) {
                return input.read() >= 0;
            }
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    public boolean isLocalBasePathConfigured() {
        return trimToNull(imageProperties.getBasePath()) != null;
    }

    public boolean isLocalBasePathReadable() {
        String basePath = trimToNull(imageProperties.getBasePath());
        if (basePath == null) {
            return false;
        }
        Path path = Path.of(basePath).toAbsolutePath().normalize();
        return Files.isDirectory(path) && Files.isReadable(path);
    }

    public String describe(Scan scan) {
        String sourceType = normalizeType(scan == null ? null : scan.getSourceType());
        if (sourceType.isEmpty()) {
            sourceType = "AUTO";
        }
        return sourceType + ":scan:" + (scan == null || scan.getId() == null ? "unknown" : scan.getId());
    }

    private Path resolveDirectPath(Scan scan) throws IOException {
        String sourceType = normalizeType(scan.getSourceType());
        if (!DIRECT_LOCAL_TYPES.contains(sourceType)) {
            return null;
        }
        String basePath = trimToNull(imageProperties.getBasePath());
        if (basePath == null) {
            return null;
        }
        Path base = Path.of(basePath).toAbsolutePath().normalize();
        Path relative = legacyRelativePath(scan);
        Path resolved = base.resolve(relative).normalize();
        if (!resolved.startsWith(base)) {
            throw new IOException("图片源路径越出已配置根目录");
        }
        return resolved;
    }

    private Path legacyRelativePath(Scan scan) throws IOException {
        String sourceType = normalizeType(scan.getSourceType());
        String sourceRef = trimToNull(scan.getSourceRef());
        if ("LOCAL".equals(sourceType) && sourceRef != null) {
            return controlledRelativePath(sourceRef);
        }

        String folder = safeSegment(scan.getFolder(), "folder");
        if (folder.length() < 5) {
            throw new IOException("folder 长度不足 5 位");
        }
        String bah = safeSegment(scan.getBah(), "bah");
        String directoryKey = MedicalRecordCodeUtils.requiresSjhForBah(bah)
                ? safeSegment(scan.getSjh(), "sjh")
                : safeSegment(scan.getBrxh(), "brxh");
        String filename = safeSegment(scan.getFilename(), "filename");
        return Path.of(folder.substring(0, 5), folder, directoryKey + "-" + bah, filename);
    }

    private Path controlledRelativePath(String sourceRef) throws IOException {
        String normalized = sourceRef.trim().replace('\\', '/');
        if (normalized.indexOf('\0') >= 0
                || normalized.contains(":")
                || normalized.startsWith("/")
                || normalized.startsWith("//")) {
            throw new IOException("图片来源引用必须是受控相对路径");
        }
        Path relative = Path.of(normalized).normalize();
        if (relative.isAbsolute() || relative.startsWith("..")) {
            throw new IOException("图片来源引用必须是受控相对路径");
        }
        return relative;
    }

    private PathDO toPathDO(Scan scan) {
        return new PathDO(
                scan.getId(),
                scan.getFolder(),
                scan.getFilename(),
                scan.getBrxh(),
                scan.getBah(),
                scan.getSjh(),
                scan.getSourceType(),
                scan.getSourceNode(),
                scan.getSourceRef(),
                scan.getOssUrl(),
                scan.getFileSize()
        );
    }

    private void validateScan(Scan scan) throws IOException {
        if (scan == null || scan.getId() == null) {
            throw new IOException("扫描记录或 Scan ID 为空");
        }
    }

    private String safeSegment(String value, String field) throws IOException {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IOException(field + " 不能为空");
        }
        if (normalized.equals(".")
                || normalized.equals("..")
                || normalized.contains("/")
                || normalized.contains("\\")
                || normalized.indexOf('\0') >= 0
                || normalized.contains(":")) {
            throw new IOException(field + " 包含非法路径字符");
        }
        return normalized;
    }

    private String safeSuffix(String filename) {
        String normalized = trimToNull(filename);
        if (normalized == null) {
            return ".bin";
        }
        int dot = normalized.lastIndexOf('.');
        if (dot < 0 || dot == normalized.length() - 1) {
            return ".bin";
        }
        String extension = normalized.substring(dot).toLowerCase(Locale.ROOT);
        return extension.matches("\\.[a-z0-9]{1,10}") ? extension : ".bin";
    }

    private Path tempDirectory() {
        String systemTemp = System.getProperty("java.io.tmpdir");
        if (systemTemp == null || systemTemp.isBlank()) {
            systemTemp = ".";
        }
        return Path.of(systemTemp, TEMP_DIRECTORY_NAME).toAbsolutePath().normalize();
    }

    private FailureNature classifyFailure(Throwable failure) {
        if (failure == null) {
            return FailureNature.UNKNOWN;
        }

        Throwable[] suppressed = failure.getSuppressed();
        if (suppressed.length > 0) {
            boolean allPermanent = true;
            for (Throwable item : suppressed) {
                FailureNature itemNature = classifyFailure(item);
                if (itemNature == FailureNature.TRANSIENT) {
                    return FailureNature.TRANSIENT;
                }
                if (itemNature != FailureNature.PERMANENT) {
                    allPermanent = false;
                }
            }
            return allPermanent ? FailureNature.PERMANENT : FailureNature.UNKNOWN;
        }

        if (failure instanceof SocketTimeoutException
                || failure instanceof ConnectException
                || failure instanceof SocketException) {
            return FailureNature.TRANSIENT;
        }
        if (failure instanceof NoSuchFileException
                || failure instanceof FileNotFoundException
                || failure instanceof InvalidPathException
                || failure instanceof IllegalArgumentException) {
            return FailureNature.PERMANENT;
        }

        Throwable cause = failure.getCause();
        if (cause != null && cause != failure) {
            FailureNature causeNature = classifyFailure(cause);
            if (causeNature != FailureNature.UNKNOWN) {
                return causeNature;
            }
        }

        String message = safeMessage(failure).toLowerCase(Locale.ROOT);
        if (message.contains("timeout")
                || message.contains("timed out")
                || message.contains("超时")
                || message.contains("connection reset")
                || message.contains("connection refused")
                || message.contains("连接中断")
                || message.contains("连接失败")) {
            return FailureNature.TRANSIENT;
        }
        if (message.contains("status 404")
                || message.contains("状态码 404")
                || message.contains("not found")
                || message.contains("不存在")
                || message.contains("不可读")
                || message.contains("不能为空")
                || message.contains("非法")
                || message.contains("越出")
                || message.contains("没有可用")
                || message.contains("access denied")
                || message.contains("forbidden")
                || message.contains("无权限")) {
            return FailureNature.PERMANENT;
        }
        return FailureNature.UNKNOWN;
    }

    private String safeMessage(Throwable error) {
        if (error == null || error.getMessage() == null || error.getMessage().isBlank()) {
            return error == null ? "未知图片源错误" : error.getClass().getSimpleName();
        }
        String message = error.getMessage().replace('\r', ' ').replace('\n', ' ').trim();
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            logger.warn("Unable to delete OSS migration temporary file: {}", path, exception);
        }
    }

    private String normalizeType(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private enum FailureNature {
        PERMANENT,
        TRANSIENT,
        UNKNOWN
    }

    public static final class SourceResolutionException extends IOException {
        private final boolean permanent;

        public SourceResolutionException(String message, boolean permanent, Throwable cause) {
            super(message, cause);
            this.permanent = permanent;
        }

        public boolean isPermanent() {
            return permanent;
        }
    }

    public record ResolvedSource(Path path, String description, boolean temporary) implements AutoCloseable {
        @Override
        public void close() {
            if (temporary) {
                deleteQuietly(path);
            }
        }
    }
}
