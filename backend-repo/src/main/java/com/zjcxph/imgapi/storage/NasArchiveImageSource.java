package com.zjcxph.imgapi.storage;

import com.zjcxph.imgapi.config.ArchiveImageSourceProperties;
import com.zjcxph.imgapi.entity.PathDO;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@Order(30)
public class NasArchiveImageSource implements ArchiveImageSource {

    private final ArchiveImageSourceProperties properties;
    private final SourcePermitGuard permitGuard;
    private final NasOperationIsolation isolation;

    public NasArchiveImageSource(ArchiveImageSourceProperties properties) {
        this.properties = properties;
        this.permitGuard = new SourcePermitGuard(
                properties.getNasMaxConcurrency(), properties.getAcquireTimeout());
        this.isolation = new NasOperationIsolation(properties.getNasMaxConcurrency(), properties.getNasNodes().keySet());
    }

    @Override
    public boolean supports(PathDO image) {
        return image != null
                && "NAS".equalsIgnoreCase(image.getSourceType())
                && image.getSourceNode() != null
                && properties.getNasNodes().containsKey(image.getSourceNode());
    }

    @Override
    public InputStream open(PathDO image) throws IOException {
        return isolation.call(image.getSourceNode(), properties.getNasOpenTimeout(),
                () -> permitGuard.open(() -> openFile(image)), stream -> {
                    try { stream.close(); } catch (IOException | RuntimeException failure) {
                        org.slf4j.LoggerFactory.getLogger(NasArchiveImageSource.class)
                                .warn("Late NAS stream close failed: errorType={}", failure.getClass().getSimpleName());
                    }
                });
    }

    @Override
    public long size(PathDO image) throws IOException {
        return isolation.call(image.getSourceNode(), properties.getNasOpenTimeout(),
                () -> permitGuard.call(() -> Files.size(requireReadable(image))), ignored -> {});
    }

    @Override
    public String describeSource(PathDO image) {
        return "NAS";
    }

    @jakarta.annotation.PreDestroy
    void shutdown() { isolation.close(); }

    InputStream openFile(PathDO image) throws IOException {
        return ImageReadStream.openFile(requireReadable(image));
    }

    private Path requireReadable(PathDO image) throws IOException {
        ArchiveImageSourceProperties.NasNode node = properties.getNasNodes().get(image.getSourceNode());
        if (node == null || node.getRoot() == null || node.getRoot().isBlank()) {
            throw new IOException("NAS 图片节点未配置: " + image.getSourceNode());
        }
        Path configuredRoot = Path.of(node.getRoot()).toAbsolutePath().normalize();
        if (!Files.isDirectory(configuredRoot)) {
            throw new IOException("NAS 图片节点根目录不存在: " + image.getSourceNode());
        }
        Path resolved = configuredRoot.resolve(ArchiveImagePathSupport.relativePath(image)).normalize();
        if (!resolved.startsWith(configuredRoot)) {
            throw new InvalidImagePathException("NAS 图片路径越过受控根目录");
        }
        if (!Files.isRegularFile(resolved) || !Files.isReadable(resolved)) {
            throw new FileNotFoundException("NAS 图片不存在或不可读");
        }

        Path realRoot = configuredRoot.toRealPath();
        Path realFile = resolved.toRealPath();
        if (!realFile.startsWith(realRoot)) {
            throw new InvalidImagePathException("NAS 图片符号链接越过受控根目录");
        }
        return realFile;
    }
}
