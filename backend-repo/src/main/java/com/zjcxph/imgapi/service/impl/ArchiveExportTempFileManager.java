package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.config.ArchiveExportProperties;
import com.zjcxph.imgapi.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
public class ArchiveExportTempFileManager {

    private final ArchiveExportProperties properties;
    private final Map<String, Long> reservations = new HashMap<>();
    private Path root;

    public ArchiveExportTempFileManager(ArchiveExportProperties properties) {
        this.properties = properties;
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
        return new FilterOutputStream(delegate) {
            private long written;

            @Override
            public void write(int value) throws IOException {
                ensureCapacity(1);
                super.write(value);
                written++;
            }

            @Override
            public void write(byte[] bytes, int offset, int length) throws IOException {
                ensureCapacity(length);
                out.write(bytes, offset, length);
                written += length;
            }

            private void ensureCapacity(int increment) throws IOException {
                if (increment < 0 || written > properties.getMaxFileBytes() - increment) {
                    throw new IOException("导出文件超过单文件配额");
                }
            }
        };
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

    public void deleteManagedFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        try {
            Path path = Path.of(filePath).toAbsolutePath().normalize();
            if (path.startsWith(root)) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // 清理失败由下一轮任务继续处理。
        }
    }

    public synchronized long currentUsageBytes() throws IOException {
        return calculateUsageBytes(Set.of());
    }

    private long currentUsageBytesExcludingReservations() throws IOException {
        return calculateUsageBytes(Set.copyOf(reservations.keySet()));
    }

    private long calculateUsageBytes(Set<String> excludedJobIds) throws IOException {
        if (!Files.exists(root)) {
            return 0;
        }
        try (var stream = Files.list(root)) {
            return stream.filter(path -> !Files.isSymbolicLink(path) && Files.isRegularFile(path))
                    .filter(path -> !belongsToReservedJob(path, excludedJobIds))
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException exception) {
                            return 0L;
                        }
                    })
                    .sum();
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
