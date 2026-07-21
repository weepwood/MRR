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

    public NasArchiveImageSource(ArchiveImageSourceProperties properties) {
        this.properties = properties;
        this.permitGuard = new SourcePermitGuard(
                properties.getNasMaxConcurrency(), properties.getAcquireTimeout());
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
        return permitGuard.open(() -> Files.newInputStream(requireReadable(image)));
    }

    @Override
    public long size(PathDO image) throws IOException {
        return permitGuard.call(() -> Files.size(requireReadable(image)));
    }

    @Override
    public String describeSource(PathDO image) {
        return "NAS";
    }

    private Path requireReadable(PathDO image) throws IOException {
        ArchiveImageSourceProperties.NasNode node = properties.getNasNodes().get(image.getSourceNode());
        if (node == null || node.getRoot() == null || node.getRoot().isBlank()) {
            throw new IOException("NAS 图片节点未配置: " + image.getSourceNode());
        }
        Path root = Path.of(node.getRoot()).toAbsolutePath().normalize();
        Path resolved = root.resolve(ArchiveImagePathSupport.relativePath(image)).normalize();
        if (!resolved.startsWith(root)) {
            throw new InvalidImagePathException("NAS 图片路径越过受控根目录");
        }
        if (!Files.isRegularFile(resolved) || !Files.isReadable(resolved)) {
            throw new FileNotFoundException("NAS 图片不存在或不可读");
        }
        return resolved;
    }
}
