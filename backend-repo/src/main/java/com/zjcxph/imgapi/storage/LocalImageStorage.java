package com.zjcxph.imgapi.storage;

import com.zjcxph.imgapi.config.ImageProperties;
import com.zjcxph.imgapi.entity.PathDO;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 基于本地磁盘或 Windows/NAS 挂载目录的影像存储实现。
 */
@Component
public class LocalImageStorage implements ImageStorage {

    private final ImageProperties imageProperties;

    public LocalImageStorage(ImageProperties imageProperties) {
        this.imageProperties = imageProperties;
    }

    @Override
    public InputStream open(PathDO image) throws IOException {
        return Files.newInputStream(requireReadableFile(image));
    }

    @Override
    public long size(PathDO image) throws IOException {
        return Files.size(requireReadableFile(image));
    }

    /**
     * 根据历史目录规则解析影像文件，并阻止绝对路径、路径分隔符和目录穿越。
     */
    public Path resolve(PathDO image) throws IOException {
        if (image == null) {
            throw new InvalidImagePathException("影像路径信息不能为空");
        }

        String configuredBasePath = imageProperties.getBasePath();
        if (configuredBasePath == null || configuredBasePath.isBlank()) {
            throw new IOException("未配置 image.basePath");
        }

        String folder = requireSegment(image.getFolder(), "folder");
        if (folder.length() < 5) {
            throw new InvalidImagePathException("folder 长度不足 5 位");
        }
        String brxh = requireSegment(image.getBrxh(), "brxh");
        String bah = requireSegment(image.getBah(), "bah");
        String filename = requireSegment(image.getFilename(), "filename");

        Path root = Paths.get(configuredBasePath).toAbsolutePath().normalize();
        Path resolved = root
                .resolve(folder.substring(0, 5))
                .resolve(folder)
                .resolve(brxh + "-" + bah)
                .resolve(filename)
                .normalize();

        if (!resolved.startsWith(root)) {
            throw new InvalidImagePathException("影像路径越过配置根目录");
        }
        return resolved;
    }

    private Path requireReadableFile(PathDO image) throws IOException {
        Path resolved = resolve(image);
        if (!Files.isRegularFile(resolved) || !Files.isReadable(resolved)) {
            throw new FileNotFoundException("影像文件不存在或不可读: " + resolved);
        }
        return resolved;
    }

    private String requireSegment(String value, String field) throws InvalidImagePathException {
        if (value == null || value.isBlank()) {
            throw new InvalidImagePathException(field + " 不能为空");
        }
        String normalized = value.trim();
        if (normalized.equals(".")
                || normalized.equals("..")
                || normalized.contains("/")
                || normalized.contains("\\")
                || normalized.indexOf('\0') >= 0
                || containsWindowsReservedCharacter(normalized)) {
            throw new InvalidImagePathException(field + " 包含非法路径字符");
        }
        return normalized;
    }

    private boolean containsWindowsReservedCharacter(String value) {
        return value.indexOf(':') >= 0
                || value.indexOf('*') >= 0
                || value.indexOf('?') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('<') >= 0
                || value.indexOf('>') >= 0
                || value.indexOf('|') >= 0;
    }
}
