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
        root = Path.of(properties.getTempDirectory()).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public synchronized Reservation reserve(String jobId, long estimatedBytes, String extension) throws IOException {
        requireSafeJobId(jobId);
        String safeExtension = requireExtension(extension);
        long requested = Math.max(1L, Math.min(
                Math.max(estimatedBytes, 1L), properties.getMaxFileBytes()));
        long used = currentUsageBytes();
        long reserved = reservations.values().stream().mapToLong(Long::longValue).sum();
        if (used + reserved + requested > properties.getMaxTotalBytes()) {
            throw new BusinessException(507, "导出临时文件配额不足，请清理旧任务后重试");
        }
        reservations.put(jobId, requested);
        Path path = root.resolve(jobId + "." + safeExtension).normalize();
        if (!path.startsWith(root)) {
            reservations.remove(jobId);
            throw new IOException("临时文件路径越过受控目录");
        }
        return new Reservation(jobId, path, requested);
    }

    public OutputStream openOutput(Reservation reservation) throws IOException {
        if (reservation == null || !reservation.path().normalize().startsWith(root)) {
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
                if (increment < 0 || written + increment > properties.getMaxFileBytes()) {
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
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            throw new IOException("导出文件不存在或不在受控目录");
        }
        return path;
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
        if (!Files.exists(root)) {
            return 0;
        }
        try (var stream = Files.list(root)) {
            return stream.filter(Files::isRegularFile)
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
