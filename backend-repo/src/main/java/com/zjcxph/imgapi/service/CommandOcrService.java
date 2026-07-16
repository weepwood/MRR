package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.config.ClassificationProperties;
import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.storage.ImageStorage;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class CommandOcrService {

    private final ClassificationProperties properties;
    private final ImageStorage imageStorage;
    private final OssService ossService;
    private final HttpClient httpClient;

    public CommandOcrService(
            ClassificationProperties properties,
            ImageStorage imageStorage,
            OssService ossService
    ) {
        this.properties = properties;
        this.imageStorage = imageStorage;
        this.ossService = ossService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
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
            copyImage(scan, temporaryImage);

            List<String> command = buildCommand(temporaryImage);
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> readOutput(process));

            int timeoutSeconds = Math.max(5, properties.getTimeoutSeconds());
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("OCR 执行超时：" + Duration.ofSeconds(timeoutSeconds));
            }

            String output = awaitOutput(outputFuture).trim();
            if (process.exitValue() != 0) {
                throw new IOException("OCR 命令执行失败，退出码=" + process.exitValue() + "，输出=" + abbreviate(output));
            }
            return output;
        } finally {
            Files.deleteIfExists(temporaryImage);
        }
    }

    private void copyImage(Scan scan, Path target) throws IOException, InterruptedException {
        PathDO image = new PathDO(scan.getFolder(), scan.getFilename(), scan.getBrxh(), scan.getBah());
        IOException localFailure;
        try (InputStream inputStream = imageStorage.open(image)) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            return;
        } catch (IOException exception) {
            localFailure = exception;
        }

        if (scan.getOssUrl() == null || scan.getOssUrl().isBlank()) {
            throw localFailure;
        }

        try {
            String signedUrl = ossService.generatePresignedUrl(scan.getOssUrl());
            if (signedUrl == null || signedUrl.isBlank()) {
                throw new IOException("OSS 签名 URL 为空");
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(signedUrl))
                    .timeout(Duration.ofSeconds(Math.max(15, properties.getTimeoutSeconds())))
                    .GET()
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                response.body().close();
                throw new IOException("OSS 图片下载失败，HTTP " + response.statusCode());
            }
            try (InputStream inputStream = response.body()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | InterruptedException exception) {
            exception.addSuppressed(localFailure);
            throw exception;
        } catch (RuntimeException exception) {
            IOException wrapped = new IOException("OSS 图片读取失败：" + exception.getMessage(), exception);
            wrapped.addSuppressed(localFailure);
            throw wrapped;
        }
    }

    private String readOutput(Process process) {
        try {
            return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private String awaitOutput(CompletableFuture<String> outputFuture) throws IOException, InterruptedException {
        try {
            return outputFuture.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof UncheckedIOException uncheckedIOException) {
                throw uncheckedIOException.getCause();
            }
            throw new IOException("读取 OCR 输出失败", cause);
        } catch (TimeoutException exception) {
            throw new IOException("读取 OCR 输出超时", exception);
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
            if (argument != null && !argument.isBlank()) {
                command.add(argument.replace("{image}", image.toString()));
            }
        }
        if (command.size() == 1) {
            command.add(image.toString());
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
