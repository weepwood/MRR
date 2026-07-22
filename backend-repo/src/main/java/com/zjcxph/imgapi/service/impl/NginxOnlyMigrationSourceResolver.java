package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.config.ImageProperties;
import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.storage.ImageStorage;
import com.zjcxph.imgapi.storage.NginxArchiveImageSource;
import org.springframework.context.annotation.Primary;
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
import java.util.Locale;

/**
 * OSS 迁移专用图片源：无论后端是否挂载本地目录，统一经受控 Nginx 地址读取。
 */
@Component
@Primary
public class NginxOnlyMigrationSourceResolver extends MigrationSourceResolver {

    private static final String TEMP_DIRECTORY_NAME = "mrr-oss-migration";

    private final NginxArchiveImageSource nginxSource;

    public NginxOnlyMigrationSourceResolver(ImageStorage imageStorage,
                                            ImageProperties imageProperties,
                                            NginxArchiveImageSource nginxSource) {
        super(imageStorage, imageProperties);
        this.nginxSource = nginxSource;
    }

    @Override
    public ResolvedSource resolve(Scan scan) throws IOException {
        validateScan(scan);
        Path directory = tempDirectory();
        Path tempFile;
        try {
            Files.createDirectories(directory);
            tempFile = Files.createTempFile(directory, "scan-" + scan.getId() + "-", safeSuffix(scan.getFilename()));
        } catch (IOException | RuntimeException exception) {
            throw new SourceResolutionException("无法创建 OSS 迁移临时文件", false, exception);
        }

        try (InputStream input = nginxSource.open(toPathDO(scan))) {
            Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING);
            if (!Files.isRegularFile(tempFile) || !Files.isReadable(tempFile)) {
                throw new IOException("Nginx 图片读取后未生成可读临时文件");
            }
            return new ResolvedSource(tempFile, describe(scan), true);
        } catch (IOException | RuntimeException exception) {
            deleteQuietly(tempFile);
            throw new SourceResolutionException(safeMessage(exception), isPermanent(exception), exception);
        }
    }

    @Override
    public boolean canRead(Scan scan) {
        try {
            validateScan(scan);
            try (InputStream input = nginxSource.open(toPathDO(scan))) {
                return input.read() >= 0;
            }
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    @Override
    public boolean isLocalBasePathConfigured() {
        // 迁移不再依赖后端本地目录；Nginx 配置通过抽样读取验证。
        return true;
    }

    @Override
    public boolean isLocalBasePathReadable() {
        return false;
    }

    @Override
    public String describe(Scan scan) {
        return "NGINX:scan:" + (scan == null || scan.getId() == null ? "unknown" : scan.getId());
    }

    private PathDO toPathDO(Scan scan) {
        return new PathDO(
                scan.getId(), scan.getFolder(), scan.getFilename(), scan.getBrxh(), scan.getBah(), scan.getSjh(),
                scan.getSourceType(), scan.getSourceNode(), scan.getSourceRef(), scan.getOssUrl(), scan.getFileSize());
    }

    private void validateScan(Scan scan) throws IOException {
        if (scan == null || scan.getId() == null) {
            throw new IOException("扫描记录或 Scan ID 为空");
        }
    }

    private Path tempDirectory() {
        String systemTemp = System.getProperty("java.io.tmpdir");
        if (systemTemp == null || systemTemp.isBlank()) {
            systemTemp = ".";
        }
        return Path.of(systemTemp, TEMP_DIRECTORY_NAME).toAbsolutePath().normalize();
    }

    private String safeSuffix(String filename) {
        String normalized = filename == null ? null : filename.trim();
        if (normalized == null || normalized.isEmpty()) {
            return ".bin";
        }
        int dot = normalized.lastIndexOf('.');
        if (dot < 0 || dot == normalized.length() - 1) {
            return ".bin";
        }
        String extension = normalized.substring(dot).toLowerCase(Locale.ROOT);
        return extension.matches("\.[a-z0-9]{1,10}") ? extension : ".bin";
    }

    private boolean isPermanent(Throwable failure) {
        if (failure instanceof SocketTimeoutException
                || failure instanceof ConnectException
                || failure instanceof SocketException) {
            return false;
        }
        if (failure instanceof FileNotFoundException
                || failure instanceof NoSuchFileException
                || failure instanceof InvalidPathException
                || failure instanceof IllegalArgumentException) {
            return true;
        }
        Throwable cause = failure.getCause();
        if (cause != null && cause != failure) {
            if (isPermanent(cause)) {
                return true;
            }
            if (cause instanceof SocketTimeoutException
                    || cause instanceof ConnectException
                    || cause instanceof SocketException) {
                return false;
            }
        }
        String message = safeMessage(failure).toLowerCase(Locale.ROOT);
        return message.contains("状态码 404")
                || message.contains("status 404")
                || message.contains("not found")
                || message.contains("不存在")
                || message.contains("非法")
                || message.contains("不能为空");
    }

    private String safeMessage(Throwable error) {
        if (error == null || error.getMessage() == null || error.getMessage().isBlank()) {
            return error == null ? "未知 Nginx 图片源错误" : error.getClass().getSimpleName();
        }
        String message = error.getMessage().replace('', ' ').replace('
', ' ').trim();
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 临时文件还会由启动清理机制兜底。
        }
    }
}
