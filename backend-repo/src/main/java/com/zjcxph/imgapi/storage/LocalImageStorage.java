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
     * 根据统一业务目录规则解析影像文件，并阻止绝对路径、路径分隔符和目录穿越。
     */
    public Path resolve(PathDO image) throws IOException {
        String configuredBasePath = imageProperties.getBasePath();
        if (configuredBasePath == null || configuredBasePath.isBlank()) {
            throw new IOException("未配置 image.basePath");
        }

        Path root = Paths.get(configuredBasePath).toAbsolutePath().normalize();
        Path resolved = root.resolve(ArchiveImagePathSupport.relativePath(image)).normalize();
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

        Path configuredRoot = Paths.get(imageProperties.getBasePath()).toAbsolutePath().normalize();
        Path realRoot = configuredRoot.toRealPath();
        Path realFile = resolved.toRealPath();
        if (!realFile.startsWith(realRoot)) {
            throw new InvalidImagePathException("影像文件符号链接越过配置根目录");
        }
        return realFile;
    }
}
