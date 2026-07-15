package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.config.DataTransferProperties;
import com.zjcxph.imgapi.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
public class DataTransferStorageService {

    private final DataTransferProperties properties;
    private final Path baseDir;
    private final Path inboxDir;

    public DataTransferStorageService(DataTransferProperties properties) {
        this.properties = properties;
        this.baseDir = Path.of(properties.getBaseDir()).toAbsolutePath().normalize();
        this.inboxDir = Path.of(properties.getInboxDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(baseDir);
            Files.createDirectories(inboxDir);
        }
        catch (IOException exception) {
            throw new IllegalStateException("无法初始化数据交换目录", exception);
        }
    }

    public StoredFile storeUpload(long jobId, int sequenceNo, MultipartFile multipartFile) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new BusinessException(400, "上传文件不能为空");
        }
        if (multipartFile.getSize() > properties.getWebUploadMaxBytes()) {
            throw new BusinessException(400, "文件超过 Web 上传限制，请放入服务器 inbox 目录后登记导入");
        }
        String originalName = sanitizeFilename(multipartFile.getOriginalFilename());
        validateCsvExtension(originalName);
        Path inputDir = jobInputDir(jobId);
        String storedName = String.format(Locale.ROOT, "%04d-%s", sequenceNo, originalName);
        Path target = safeChild(inputDir, storedName);
        try {
            Files.createDirectories(inputDir);
            try (InputStream inputStream = multipartFile.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredFile(originalName, target, Files.size(target), sha256(target));
        }
        catch (IOException exception) {
            throw new BusinessException(500, "保存上传文件失败：" + exception.getMessage());
        }
    }

    public StoredFile registerInboxFile(long jobId, int sequenceNo, String filename) {
        String safeName = sanitizeFilename(filename);
        validateCsvExtension(safeName);
        Path source = safeChild(inboxDir, safeName);
        if (!Files.isRegularFile(source)) {
            throw new BusinessException(404, "inbox 文件不存在：" + safeName);
        }
        Path inputDir = jobInputDir(jobId);
        Path target = safeChild(inputDir, String.format(Locale.ROOT, "%04d-%s", sequenceNo, safeName));
        try {
            Files.createDirectories(inputDir);
            // 采用硬链接优先，跨磁盘或权限不允许时再复制，避免超大文件不必要的重复占用。
            try {
                Files.createLink(target, source);
            }
            catch (IOException | UnsupportedOperationException ignored) {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredFile(safeName, target, Files.size(target), sha256(target));
        }
        catch (IOException exception) {
            throw new BusinessException(500, "登记 inbox 文件失败：" + exception.getMessage());
        }
    }

    public List<String> listInboxFiles() {
        try (var stream = Files.list(inboxDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(this::isCsvFilename)
                    .sorted()
                    .toList();
        }
        catch (IOException exception) {
            throw new BusinessException(500, "读取 inbox 目录失败");
        }
    }

    public Path createOutputPath(long jobId, String filename) {
        Path outputDir = baseDir.resolve("job-" + jobId).resolve("output").normalize();
        try {
            Files.createDirectories(outputDir);
        }
        catch (IOException exception) {
            throw new BusinessException(500, "创建导出目录失败");
        }
        return safeChild(outputDir, sanitizeFilename(filename));
    }

    public Path createErrorReportPath(long jobId, long fileId) {
        Path errorDir = baseDir.resolve("job-" + jobId).resolve("errors").normalize();
        try {
            Files.createDirectories(errorDir);
        }
        catch (IOException exception) {
            throw new BusinessException(500, "创建错误报告目录失败");
        }
        return safeChild(errorDir, "errors-file-" + fileId + ".csv.gz");
    }

    public Path resolveStoredPath(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            throw new BusinessException(404, "文件路径不存在");
        }
        Path path = Path.of(storedPath).toAbsolutePath().normalize();
        if (!path.startsWith(baseDir) || !Files.isRegularFile(path)) {
            throw new BusinessException(404, "文件不存在或路径不受信任");
        }
        return path;
    }

    public String sha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream inputStream = Files.newInputStream(path)) {
                byte[] buffer = new byte[1024 * 1024];
                int read;
                while ((read = inputStream.read(buffer)) >= 0) {
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        }
        catch (IOException | NoSuchAlgorithmException exception) {
            throw new BusinessException(500, "计算文件校验值失败");
        }
    }

    private Path jobInputDir(long jobId) {
        return baseDir.resolve("job-" + jobId).resolve("input").normalize();
    }

    private Path safeChild(Path parent, String childName) {
        Path result = parent.resolve(childName).toAbsolutePath().normalize();
        Path normalizedParent = parent.toAbsolutePath().normalize();
        if (!result.startsWith(normalizedParent)) {
            throw new BusinessException(400, "非法文件路径");
        }
        return result;
    }

    private String sanitizeFilename(String value) {
        if (value == null || value.isBlank()) {
            return "data.csv";
        }
        String filename = Path.of(value).getFileName().toString().trim();
        filename = filename.replaceAll("[\\r\\n\\t\\x00-\\x1f]", "_");
        if (filename.isBlank() || filename.equals(".") || filename.equals("..")) {
            throw new BusinessException(400, "非法文件名");
        }
        return filename;
    }

    private void validateCsvExtension(String filename) {
        if (!isCsvFilename(filename)) {
            throw new BusinessException(400, "仅支持 .csv 或 .csv.gz 文件");
        }
    }

    private boolean isCsvFilename(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".csv") || lower.endsWith(".csv.gz");
    }

    public record StoredFile(String originalFilename, Path path, long size, String sha256) {
    }
}
