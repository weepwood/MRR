package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.config.ImageProperties;
import com.zjcxph.imgapi.entity.*;
import com.zjcxph.imgapi.dto.req.*;
import com.zjcxph.imgapi.dto.resp.*;
import com.zjcxph.imgapi.common.*;
import com.zjcxph.imgapi.service.OssService;
import com.zjcxph.imgapi.service.PdfService;
import com.zjcxph.imgapi.service.ScanService;
import com.zjcxph.imgapi.utils.ZipUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Pattern;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Validated
@RestController
@RequestMapping("/api/v1/img")
@Tag(name = "IMG Controller", description = "图片管理接口")
@RequirePermissions({"record:read"})
public class ImageController {

    private static final Logger logger = LoggerFactory.getLogger(ImageController.class);

    private final ImageProperties imageProperties;
    private final ScanService scanService;
    private final PdfService pdfService;
    private final OssService ossService;

    public ImageController(ImageProperties imageProperties, ScanService scanService,
                           PdfService pdfService, OssService ossService) {
        this.imageProperties = imageProperties;
        this.scanService = scanService;
        this.pdfService = pdfService;
        this.ossService = ossService;
    }

    @Operation(summary = "服务器心跳")
    @GetMapping("/hello")
    public Result<Map<String, Object>> hello() {
        logger.info("服务正常");
        Map<String, Object> data = new HashMap<>();
        data.put("message", "服务正常");
        return Result.success(data);
    }

    @Operation(summary = "下载病案压缩包")
    @GetMapping("/download/{BAH}")
    public ResponseEntity<FileSystemResource> download(@PathVariable
                                                       @Pattern(regexp = "\\d{8}", message = "请输入正确的 8 位病案号")
                                                       @Parameter(description = "病案号", example = "00789508")
                                                       String BAH) throws IOException {
        File zipFile = scanService.createZipForBAH(BAH);
        String fileNameZip = BAH + ".zip";
        FileSystemResource fileSystemResource = new FileSystemResource(zipFile);

        logger.info("生成压缩包:{}", fileNameZip);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileNameZip);

        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(zipFile.length())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(fileSystemResource);
    }

    @Operation(summary = "获取病案号下的图片数据")
    @GetMapping("/{bah}")
    public Result<List<BAHDataResponseDTO>> getDataByBAH(
            @PathVariable
            @Pattern(regexp = "\\d{8}", message = "请输入正确的 8 位病案号")
            @Parameter(description = "病案号", example = "00789508")
            String bah) {
        List<Scan> imageListByBAH = scanService.getImageListByBAH(bah);
        List<BAHDataResponseDTO> items = new ArrayList<>();

        for (Scan scan : imageListByBAH) {
            String folder = scan.getFolder();
            String brxh = scan.getBrxh();
            if (folder == null || folder.isBlank() || brxh == null) {
                logger.warn("跳过扫描记录 id={}, 文件夹或序号为空", scan.getId());
                continue;
            }
            String imgUrl = determineImageUrl(folder);
            String img_url = imgUrl + "/" + extractYearMonth(folder) + "/" + folder + "/" +
                    brxh + "-" + scan.getBah() + "/" + scan.getFilename();
            BAHDataResponseDTO dto = new BAHDataResponseDTO();
            BeanUtils.copyProperties(scan, dto);
            dto.setImg_url(img_url);

            if (scan.getOssUrl() != null && !scan.getOssUrl().isBlank()) {
                try {
                    String signedUrl = ossService.generatePresignedUrl(scan.getOssUrl());
                    dto.setOssUrl(signedUrl);
                } catch (Exception e) {
                    logger.warn("生成 OSS 签名 URL 失败 scan {}: {}", scan.getId(), e.getMessage());
                }
            }

            items.add(dto);
        }
        return Result.success(items).message(bah + " 数据获取成功");
    }

    @Operation(summary = "按病案号和/或上架号查询图片数据")
    @GetMapping("/search")
    public Result<List<BAHDataResponseDTO>> searchByCode(
            @Parameter(description = "病案号")
            @RequestParam(required = false) String bah,
            @Parameter(description = "上架号")
            @RequestParam(required = false) String sjh) {
        String normalizedBah = bah != null ? normalizeCode(bah) : "";
        String normalizedSjh = sjh != null ? sjh.trim() : "";
        if (normalizedBah.isEmpty() && normalizedSjh.isEmpty()) {
            return Result.fail("病案号和上架号不能同时为空");
        }
        List<Scan> list = scanService.getImageListByCode(normalizedBah, normalizedSjh);
        List<BAHDataResponseDTO> items = new ArrayList<>();
        for (Scan scan : list) {
            String folder = scan.getFolder();
            String brxh = scan.getBrxh();
            if (folder == null || folder.isBlank() || brxh == null) {
                logger.warn("跳过扫描记录 id={}, 文件夹或序号为空", scan.getId());
                continue;
            }
            String imgUrl = determineImageUrl(folder);
            String img_url = imgUrl + "/" + extractYearMonth(folder) + "/" + folder + "/" +
                    brxh + "-" + scan.getBah() + "/" + scan.getFilename();
            BAHDataResponseDTO dto = new BAHDataResponseDTO();
            BeanUtils.copyProperties(scan, dto);
            dto.setImg_url(img_url);
            if (scan.getOssUrl() != null && !scan.getOssUrl().isBlank()) {
                try {
                    String signedUrl = ossService.generatePresignedUrl(scan.getOssUrl());
                    dto.setOssUrl(signedUrl);
                } catch (Exception e) {
                    logger.warn("生成 OSS 签名 URL 失败 scan {}: {}", scan.getId(), e.getMessage());
                }
            }
            items.add(dto);
        }
        return Result.success(items);
    }

    @Operation(summary = "获取病案号下的对应单张图片")
    @GetMapping("image/{BAH}/{BRXH}/{FOLDER}/{FILENAME}")
    public ResponseEntity<?> getImage(
            @Parameter(description = "病案号", example = "00789508")
            @PathVariable String BAH,
            @Parameter(description = "病人序号", example = "605746")
            @PathVariable String BRXH,
            @Parameter(description = "文件夹", example = "24.04.30")
            @PathVariable String FOLDER,
            @Parameter(description = "文件名", example = "0072.jpg")
            @PathVariable String FILENAME) {

        if (BAH == null || BRXH == null || FOLDER == null || FILENAME == null) {
            return ResponseEntity.badRequest().body(Result.fail("参数不能为空"));
        }

        if (FOLDER.length() < 5) {
            return ResponseEntity.badRequest().body(Result.fail("文件夹格式错误"));
        }

        if (FILENAME.contains("..") || FOLDER.contains("..") || BAH.contains("..") || BRXH.contains("..")) {
            logger.warn("检测到路径遍历尝试: BAH={}, BRXH={}, FOLDER={}, FILENAME={}", BAH, BRXH, FOLDER, FILENAME);
            return ResponseEntity.badRequest().body(Result.fail("非法的路径参数"));
        }

        String folderName = BRXH + "-" + BAH;
        String parentFolder = FOLDER.substring(0, 5);

        Path basePath = Paths.get(imageProperties.getBasePath()).normalize();
        Path resolvedPath = Paths.get(basePath.toString(), parentFolder, FOLDER, folderName, FILENAME).normalize();

        if (!resolvedPath.startsWith(basePath)) {
            logger.warn("路径遍历拦截: {} 不在允许的基路径内", resolvedPath);
            return ResponseEntity.badRequest().body(Result.fail("非法的路径参数"));
        }

        Path filePath = resolvedPath;

        logger.info("获取图片:{}", filePath);

        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            logger.error("文件不存在:{}", filePath);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.fail("图片不存在"));
        }

        FileSystemResource resource = new FileSystemResource(filePath.toFile());

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline;filename=" + FILENAME);
        headers.add("Cache-Control", "public, max-age=86400, immutable");
        headers.setContentType(MediaType.IMAGE_JPEG);

        try {
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(Files.size(filePath))
                    .body(resource);
        } catch (IOException e) {
            logger.error("文件读取错误:{}", filePath, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.fail("图片读取错误"));
        }
    }

    @Operation(summary = "根据图片id修改对应图片类型")
    @PutMapping("/updateImageType/{id}")
    public Result<Void> updateImageType(
            @PathVariable
            @Parameter(description = "图片id", example = "1")
            Integer id,
            @RequestBody ImageRequest req) {
        Integer imageType = req.getBtype();
        if (imageType == null) {
            return Result.fail("图片类型不能为空");
        }
        if (id == null) {
            return Result.fail("图片id不能为空");
        }
        if (imageType < 0 || imageType > 14) {
            return Result.fail("图片类型错误");
        }
        int result = scanService.updateImageType(id, imageType);
        if (result != 1) {
            logger.error("修改图片 {} 的类型为 {} 失败", id, imageType);
            return Result.fail("修改图片类型失败");
        }

        logger.info("修改图片 {} 的类型为 {}", id, imageType);
        return Result.success("修改图片类型成功");
    }

    @Operation(summary = "通过后端代理获取 OSS 图片")
    @GetMapping("/oss-image/{id}")
    public ResponseEntity<?> getOssImage(
            @PathVariable
            @Parameter(description = "扫描记录 ID", example = "1")
            Integer id) {
        Scan scan = scanService.findById(id);
        if (scan == null) {
            return ResponseEntity.notFound().build();
        }

        String ossKey = scan.getOssUrl();
        if (ossKey == null || ossKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.fail("该记录未迁移到 OSS"));
        }

        try {
            String signedUrl = ossService.generatePresignedUrl(ossKey);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, signedUrl)
                    .header("Cache-Control", "private, max-age=3600")
                    .build();
        } catch (Exception e) {
            logger.error("获取 OSS 图片失败：id={}", id, e);
            return ResponseEntity.internalServerError()
                    .body(Result.fail("获取 OSS 图片失败：" + e.getMessage()));
        }
    }

    private String determineImageUrl(String folder) {
        if (folder == null || folder.isBlank()) {
            return "http://192.2.1.182:8001/ba-img-00";
        }

        Set<String> baImg01YearMonth = Set.of(
            "24.04", "24.05", "24.06", "24.07", "24.08", "24.09",
            "24.10", "24.11", "25.07", "25.08"
        );
        Set<String> baImg02YearMonth = Set.of(
            "2025.08", "2025.09", "2025.10", "2025.11", "2025.12",
            "2026.01", "2026.02", "2026.03", "2026.04", "2026.05", "2026.06"
        );
        Set<String> baImg03Exact = Set.of(
            "2026.06.05", "2026.06.08", "2026.06.09"
        );

        if (baImg03Exact.contains(folder)) {
            return "http://192.2.1.135:8001/ba-img-03";
        }

        String yearMonth = extractYearMonth(folder);

        if (baImg02YearMonth.contains(yearMonth)) {
            return "http://192.2.1.135:8001/ba-img-02";
        }

        if (baImg01YearMonth.contains(yearMonth)) {
            return "http://192.2.1.182:8001/ba-img-01";
        }

        return imageProperties.getUrl();
    }

    public static String extractYearMonth(String dateStr) {
        if (dateStr == null) {
            throw new IllegalArgumentException("dateStr must not be null");
        }
        String[] parts = dateStr.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid date format: " + dateStr);
        }
        return parts[0] + "." + parts[1];
    }

    private static String normalizeCode(String code) {
        if (code == null) return "";
        String trimmed = code.trim();
        if (trimmed.length() > 0 && trimmed.length() < 8 && trimmed.matches("\\d+")) {
            return "0".repeat(8 - trimmed.length()) + trimmed;
        }
        return trimmed;
    }
}
