package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.config.ClassificationProperties;
import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.storage.ImageStorage;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class CommandOcrService {

    private final ClassificationProperties properties;
    private final ImageStorage imageStorage;

    public CommandOcrService(ClassificationProperties properties, ImageStorage imageStorage) {
        this.properties = properties;
        this.imageStorage = imageStorage;
    }

    public String recognize(Scan scan) throws IOException, InterruptedException {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("智能分类功能尚未启用");
        }
        if (!properties.isOcrConfigured()) {
            throw new IllegalStateException("尚未配置 classification.ocr.executable");
        }
        if (scan == null || scan.getId() == null) {
            throw new IllegalArgumentException("扫描记录不能为空");
        }

        Path tempDirectory = resolveTempDirectory();
        Files.createDirectories(tempDirectory);
        Path temporaryImage = Files.createTempFile(
                tempDirectory,
                "mrr-scan-" + scan.getId() + "-",
                safeSuffix(scan.getFilename())
        );

        try {
            PathDO image = new PathDO(scan.getFolder(), scan.getFilename(), scan.getBrxh(), scan.getBah());
            try (InputStream inputStream = imageStorage.open(image)) {
                Files.copy(inputStream, temporaryImage, StandardCopyOption.REPLACE_EXISTING);
            }

            List<String> command = buildCommand(temporaryImage);
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();

            int timeoutSeconds = Math.max(5, properties.getTimeoutSeconds());
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("OCR 执行超时：" + Duration.ofSeconds(timeoutSeconds));
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) {
                throw new IOException("OCR 命令执行失败，退出码=" + process.exitValue() + "，输出=" + abbreviate(output));
            }
            return output;
        } finally {
            Files.deleteIfExists(temporaryImage);
        }
    }

    private Path resolveTempDirectory() {
        String configured = properties.getTempDirectory();
        if (configured == null || configured.isBlank()) {
            return Path.of(System.getProperty("java.io.tmpdir"), "mrr-image-classification");
        }
        return Path.of(configured).toAbsolutePath().normalize();
    }

    private List<String> buildCommand(Path image) {
        List<String> command = new ArrayList<>();
        command.add(properties.getOcr().getExecutable());
        List<String> arguments = properties.getOcr().getArguments();
        if (arguments == null || arguments.isEmpty()) {
            command.add(image.toString());
            return command;
        }
        for (String argument : arguments) {
            command.add(String.valueOf(argument).replace("{image}", image.toString()));
        }
        return command;
    }

    private String safeSuffix(String filename) {
        if (filename == null) {
            return ".img";
        }
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            return ".img";
        }
        String suffix = filename.substring(index).replaceAll("[^A-Za-z0-9.]", "");
        return suffix.length() > 10 || suffix.length() < 2 ? ".img" : suffix;
    }

    private String abbreviate(String value) {
        if (value == null || value.length() <= 500) {
            return value;
        }
        return value.substring(0, 500) + "...";
    }
}
