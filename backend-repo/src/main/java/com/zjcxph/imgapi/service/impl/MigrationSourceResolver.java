package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.config.ImageProperties;
import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.storage.ImageStorage;
import com.zjcxph.imgapi.utils.MedicalRecordCodeUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;

/**
 * 为 OSS 迁移解析可读取的源文件。
 *
 * <p>本地文件可直接上传；NAS、HTTP、Nginx 等来源通过现有 ImageStorage
 * 读取并物化为单个临时文件，上传结束后立即删除。</p>
 */
@Component
public class MigrationSourceResolver {

    private static final Set<String> DIRECT_LOCAL_TYPES = Set.of("", "AUTO", "LOCAL", "OSS");
    private static final String TEMP_DIRECTORY_NAME = "mrr-oss-migration";

    private final ImageStorage imageStorage;
    private final ImageProperties imageProperties;

    public MigrationSourceResolver(ImageStorage imageStorage, ImageProperties imageProperties) {
        this.imageStorage = imageStorage;
        this.imageProperties = imageProperties;
    }

    public ResolvedSource resolve(Scan scan) throws IOException {
        validateScan(scan);
        Path directPath = resolveDirectPath(scan);
        if (directPath != null && Files.isRegularFile(directPath) && Files.isReadable(directPath)) {
            return new ResolvedSource(directPath, directPath.toString(), false);
        }

        Path tempDirectory = Path.of(System.getProperty("java.io.tmpdir"), TEMP_DIRECTORY_NAME)
                .toAbsolutePath()
                .normalize();
        Files.createDirectories(tempDirectory);
        Path tempFile = Files.createTempFile(
                tempDirectory,
                "scan-" + scan.getId() + "-",
                safeSuffix(scan.getFilename())
        );
        try (InputStream input = imageStorage.open(toPathDO(scan))) {
            Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING);
            if (!Files.isRegularFile(tempFile) || !Files.isReadable(tempFile)) {
                throw new IOException("多来源图片读取后未生成可读临时文件");
            }
            return new ResolvedSource(tempFile, describe(scan), true);
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(tempFile);
            if (exception instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("多来源图片读取失败", exception);
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
        String basePath = trimToNull(imageProperties.getBasePath());
        return basePath != null;
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

    private String normalizeType(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record ResolvedSource(Path path, String description, boolean temporary) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            if (temporary) {
                Files.deleteIfExists(path);
            }
        }
    }
}
