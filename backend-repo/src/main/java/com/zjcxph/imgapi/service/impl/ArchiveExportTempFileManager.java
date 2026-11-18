package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.config.ArchiveExportProperties;
import com.zjcxph.imgapi.utils.ExportSizeLimitOutputStream;
import com.zjcxph.imgapi.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.InvalidPathException;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ArchiveExportTempFileManager {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveExportTempFileManager.class);
    private final AtomicLong cleanupFailures = new AtomicLong();
    private final ArchiveExportProperties properties;
    private final Map<String, Long> reservations = new HashMap<>();
    private Path root;

    public ArchiveExportTempFileManager(ArchiveExportProperties properties) {
        this.properties = properties;
    }

    @Autowired
    public void registerCleanupMetrics(MeterRegistry registry) {
        Gauge.builder("mrr.archive.export.cleanup.failures", cleanupFailures, AtomicLong::get)
                .description("Cumulative export temporary file deletion failures")
                .register(registry);
    }

    @PostConstruct
    public void initialize() throws IOException {
        if (properties.getTempDirectory() == null || properties.getTempDirectory().isBlank()) {
            throw new IOException("导出临时目录不能为空");
        }
        if (properties.getMaxFileBytes() <= 0 || properties.getMaxTotalBytes() <= 0
                || properties.getMaxFileBytes() > properties.getMaxTotalBytes()) {
            throw new IOException("导出临时文件配额配置不正确");
        }
        Path configuredRoot = Path.of(properties.getTempDirectory()).toAbsolutePath().normalize();
        Files.createDirectories(configuredRoot);
        if (Files.isSymbolicLink(configuredRoot)) {
            throw new IOException("导出临时目录不能是符号链接");
        }
        root = configuredRoot.toRealPath();
    }

    public synchronized Reservation reserve(String jobId, long estimatedBytes, String extension) throws IOException {
        requireSafeJobId(jobId);
        String safeExtension = requireExtension(extension);
        if (estimatedBytes > properties.getMaxFileBytes()) {
            throw new BusinessException(413, "预计导出文件超过单文件配额");
        }

        // 运行中的任务始终按单文件最大值预留。正在写入的任务文件已经由预留额度覆盖，
        // 计算磁盘已用空间时必须排除这些文件，避免同一字节被重复计算。
        long requested = properties.getMaxFileBytes();
        long used = currentUsageBytesExcludingReservations();
        long reserved = reservations.values().stream().mapToLong(Long::longValue).sum();
        long maxTotal = properties.getMaxTotalBytes();
        if (reserved > maxTotal
                || requested > maxTotal - reserved
                || used > maxTotal - reserved - requested) {
            throw new BusinessException(507, "导出临时文件配额不足，请清理旧任务后重试");
        }
        Path path = root.resolve(jobId + "." + safeExtension).normalize();
        if (!path.startsWith(root) || Files.isSymbolicLink(path)) {
            throw new IOException("临时文件路径越过受控目录或指向符号链接");
        }
        reservations.put(jobId, requested);
        return new Reservation(jobId, path, requested);
    }

    public OutputStream openOutput(Reservation reservation) throws IOException {
        if (reservation == null || !reservation.path().normalize().startsWith(root)
                || Files.isSymbolicLink(reservation.path())) {
            throw new IOException("临时文件预留无效");
        }
        OutputStream delegate = Files.newOutputStream(
                reservation.path(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        return new ExportSizeLimitOutputStream(delegate, properties.getMaxFileBytes());
    }

    public Path requireManagedFile(String filePath) throws IOException {
        if (filePath == null || filePath.isBlank()) {
            throw new IOException("导出文件路径为空");
        }
        Path path = Path.of(filePath).toAbsolutePath().normalize();
        if (!path.startsWith(root) || Files.isSymbolicLink(path) || !Files.isRegularFile(path)) {
            throw new IOException("导出文件不存在或不在受控目录");
        }
        Path realPath = path.toRealPath();
        if (!realPath.startsWith(root)) {
            throw new IOException("导出文件越过受控目录");
        }
        return realPath;
    }

    public boolean deleteManagedFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return true;
        }
        try {
            Path path = Path.of(filePath).toAbsolutePath().normalize();
            if (root.equals(path.getParent()) && !Files.isSymbolicLink(path)) {
                Files.deleteIfExists(path);
                return true;
            }
        } catch (IOException | InvalidPathException | SecurityException exception) {
            // 保留任务路径，由后续清理重试；日志不记录内部路径和异常正文。
        }
        logger.warn("Export temporary cleanup failed; failures={}", cleanupFailures.incrementAndGet());
        return false;
    }

    public synchronized long currentUsageBytes() throws IOException {
        return calculateUsageBytes(Set.of());
    }

    private long currentUsageBytesExcludingReservations() throws IOException {
        return calculateUsageBytes(Set.copyOf(reservations.keySet()));
    }

    private long calculateUsageBytes(Set<String> excludedJobIds) throws IOException {
        try (var stream = Files.list(root)) {
            long total = 0;
            var paths = stream.iterator();
            while (paths.hasNext()) {
                Path path = paths.next();
                if (belongsToReservedJob(path, excludedJobIds)) {
                    continue;
                }
                final BasicFileAttributes attributes;
                try {
                    attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                } catch (NoSuchFileException exception) {
                    // Cleanup may remove a file after the directory entry was listed.
                    continue;
                }
                if (attributes.isRegularFile()) {
                    long size = attributes.size();
                    total = size > Long.MAX_VALUE - total ? Long.MAX_VALUE : total + size;
                }
            }
            return total;
        } catch (NoSuchFileException exception) {
            return 0;
        } catch (IOException | UncheckedIOException exception) {
            // Worker logs the exception chain, so do not retain filesystem paths in a cause either.
            throw new IOException("无法核算导出临时目录占用");
        }
    }

    private boolean belongsToReservedJob(Path path, Set<String> jobIds) {
        if (jobIds.isEmpty() || path.getFileName() == null) {
            return false;
        }
        String fileName = path.getFileName().toString();
        return jobIds.stream().anyMatch(jobId -> fileName.startsWith(jobId + "."));
    }

    private void release(String jobId) {
        synchronized (this) {
            reservations.remove(jobId);
        }
    }

    private void requireSafeJobId(String jobId) throws IOException {
        if (jobId == null || !jobId.matches("[A-Za-z0-9-]{8,64}")) {
            throw new IOException("导出任务 ID 不合法");
        }
    }

    private String requireExtension(String extension) throws IOException {
        String normalized = extension == null ? "" : extension.trim().toLowerCase();
        if (!normalized.equals("zip") && !normalized.equals("pdf")) {
            throw new IOException("导出文件扩展名不受支持");
        }
        return normalized;
    }

    public final class Reservation implements AutoCloseable {
        private final String jobId;
        private final Path path;
        private final long reservedBytes;
        private boolean closed;

        private Reservation(String jobId, Path path, long reservedBytes) {
            this.jobId = jobId;
            this.path = path;
            this.reservedBytes = reservedBytes;
        }

        public Path path() {
            return path;
        }

        public long reservedBytes() {
            return reservedBytes;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                release(jobId);
            }
        }
    }
}
