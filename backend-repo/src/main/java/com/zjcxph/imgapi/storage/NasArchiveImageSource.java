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
    private final java.util.Map<String, SourcePermitGuard> nodePermits;
    private final NasOperationIsolation isolation;

    @org.springframework.beans.factory.annotation.Autowired
    public NasArchiveImageSource(ArchiveImageSourceProperties properties) {
        this(properties, System::nanoTime);
    }

    NasArchiveImageSource(ArchiveImageSourceProperties properties, java.util.function.LongSupplier clock) {
        this.properties = properties;
        this.permitGuard = new SourcePermitGuard(
                properties.getNasMaxConcurrency(), properties.getAcquireTimeout());
        int nodeLimit = Math.max(1, properties.getNasMaxConcurrency() / Math.max(1, properties.getNasNodes().size()));
        this.nodePermits = properties.getNasNodes().keySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                node -> node, node -> new SourcePermitGuard(nodeLimit, properties.getAcquireTimeout())));
        this.isolation = new NasOperationIsolation(properties.getNasMaxConcurrency(), properties.getNasNodes().keySet(), clock);
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
                mark -> nodePermits.get(image.getSourceNode()).open(() -> permitGuard.open(() -> { mark.run(); return openFile(image); })), stream -> {
                    try { stream.close(); } catch (IOException | RuntimeException failure) {
                        org.slf4j.LoggerFactory.getLogger(NasArchiveImageSource.class)
                                .warn("Late NAS stream close failed: errorType={}", failure.getClass().getSimpleName());
                    }
                }, (stream, finish) -> NasBodyStream.wrap(stream, properties.getNasBodyTimeout(), finish));
    }

    @Override
    public long size(PathDO image) throws IOException {
        return isolation.call(image.getSourceNode(), properties.getNasOpenTimeout(),
                mark -> nodePermits.get(image.getSourceNode()).call(() -> permitGuard.call(() -> { mark.run(); return Files.size(requireReadable(image)); })), ignored -> {});
    }

    /** Passive process-local observation; no filesystem calls, names or addresses. */
    public java.util.Map<String, Object> runtimeSnapshot() {
        var names = nodePermits.keySet().stream().sorted().toList();
        var nodes = new java.util.ArrayList<java.util.Map<String, Object>>();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            var guard = nodePermits.get(name);
            nodes.add(java.util.Map.of("ordinal", i + 1, "state", isolation.circuitState(name),
                    "inUse", guard.inUse(), "limit", guard.limit()));
        }
        return java.util.Map.of("inUse", permitGuard.inUse(), "limit", permitGuard.limit(),
                "nodes", java.util.List.copyOf(nodes));
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
