package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.config.ImageProperties;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.service.ScanService;
import com.zjcxph.imgapi.utils.MedicalRecordCodeUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/v1/img")
@Tag(name = "Archive Image Export Controller", description = "前端 PDF 合成所需的同源影像流接口")
@RequirePermissions({"record:read"})
public class ArchiveImageExportController {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveImageExportController.class);

    private final ImageProperties imageProperties;
    private final ScanService scanService;

    public ArchiveImageExportController(ImageProperties imageProperties, ScanService scanService) {
        this.imageProperties = imageProperties;
        this.scanService = scanService;
    }

    @Operation(summary = "通过当前系统后端读取单张原始影像，供前端合成 PDF")
    @GetMapping("/export-image/{id}")
    public ResponseEntity<?> getExportImage(
            @PathVariable
            @Parameter(description = "扫描记录 ID", example = "1")
            Integer id) {
        Scan scan = scanService.findById(id);
        if (scan == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.fail("影像记录不存在"));
        }

        Path imagePath = resolveImagePath(scan);
        if (imagePath == null) {
            return ResponseEntity.badRequest()
                    .body(Result.fail("影像路径信息不完整"));
        }
        if (!Files.isRegularFile(imagePath)) {
            logger.warn("PDF 导出影像文件不存在：id={}, path={}", id, imagePath);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.fail("影像文件不存在"));
        }

        try {
            String detectedType = Files.probeContentType(imagePath);
            MediaType mediaType = detectedType == null
                    ? MediaType.IMAGE_JPEG
                    : MediaType.parseMediaType(detectedType);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(mediaType);
            headers.setContentLength(Files.size(imagePath));
            headers.setCacheControl("private, max-age=300");
            headers.set(HttpHeaders.CONTENT_DISPOSITION,
                    "inline; filename=\"" + sanitizeFileName(scan.getFilename()) + "\"");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(new FileSystemResource(imagePath));
        } catch (IOException | IllegalArgumentException ex) {
            logger.error("读取 PDF 导出影像失败：id={}, path={}", id, imagePath, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.fail("影像文件读取失败"));
        }
    }

    private Path resolveImagePath(Scan scan) {
        String folder = scan.getFolder();
        String filename = scan.getFilename();
        String normalizedBah = MedicalRecordCodeUtils.normalizeOrEmpty(scan.getBah());
        String normalizedSjh = MedicalRecordCodeUtils.normalizeOrEmpty(scan.getSjh());
        String brxh = scan.getBrxh();

        if (folder == null || folder.length() < 5 || filename == null || normalizedBah.isEmpty()) {
            return null;
        }

        String folderKey;
        if (MedicalRecordCodeUtils.requiresSjhForBah(normalizedBah)) {
            if (normalizedSjh.isEmpty()) {
                return null;
            }
            folderKey = normalizedSjh;
        } else {
            if (brxh == null || brxh.isBlank()) {
                return null;
            }
            folderKey = brxh;
        }

        Path basePath = Paths.get(imageProperties.getBasePath()).toAbsolutePath().normalize();
        Path resolvedPath = basePath
                .resolve(folder.substring(0, 5))
                .resolve(folder)
                .resolve(folderKey + "-" + normalizedBah)
                .resolve(filename)
                .normalize();
        return resolvedPath.startsWith(basePath) ? resolvedPath : null;
    }

    private String sanitizeFileName(String filename) {
        if (filename == null || filename.isBlank()) {
            return "image.jpg";
        }
        return filename.replace("\\", "_").replace("/", "_").replace("\"", "_");
    }
}
