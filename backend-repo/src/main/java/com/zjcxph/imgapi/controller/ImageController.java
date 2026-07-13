package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.config.ImageProperties;
import com.zjcxph.imgapi.dto.req.ImageRequest;
import com.zjcxph.imgapi.dto.resp.BAHDataResponseDTO;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.service.ImageUrlService;
import com.zjcxph.imgapi.service.OssService;
import com.zjcxph.imgapi.service.PdfService;
import com.zjcxph.imgapi.service.ScanService;
import com.zjcxph.imgapi.utils.MedicalRecordCodeUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final ImageUrlService imageUrlService;

    public ImageController(ImageProperties imageProperties, ScanService scanService,
                           PdfService pdfService, OssService ossService,
                           ImageUrlService imageUrlService) {
        this.imageProperties = imageProperties;
        this.scanService = scanService;
        this.pdfService = pdfService;
        this.ossService = ossService;
        this.imageUrlService = imageUrlService;
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
                                                       @Pattern(regexp = "\\d{1,8}", message = "请输入 1-8 位数字病案号")
                                                       @Parameter(description = "病案号，可省略前导零", example = "789508")
                                                       String BAH) throws IOException {
        String normalizedBah = MedicalRecordCodeUtils.normalizeOrEmpty(BAH);
        File zipFile = scanService.createZipForBAH(normalizedBah);
        zipFile.deleteOnExit();
        String fileNameZip = normalizedBah + ".zip";
        FileSystemResource fileSystemResource = new FileSystemResource(zipFile);

        logger.info("生成压缩包:{}", fileNameZip);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileNameZip + "\"");

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
            @Pattern(regexp = "\\d{1,8}", message = "请输入 1-8 位数字病案号")
            @Parameter(description = "病案号，可省略前导零", example = "789508")
            String bah) {
        String normalizedBah = MedicalRecordCodeUtils.normalizeOrEmpty(bah);
        String bahSearchCode = MedicalRecordCodeUtils.toSearchTerm(bah);
        List<Scan> imageListByBAH = scanService.getImageListByBAH(normalizedBah, bahSearchCode);
        List<BAHDataResponseDTO> items = imageUrlService.toDtoList(imageListByBAH);
        return Result.success(items).message(normalizedBah + " 数据获取成功");
    }

    @Operation(summary = "按病案号和/或上架号查询图片数据")
    @GetMapping("/search")
    public Result<List<BAHDataResponseDTO>> searchByCode(
            @Parameter(description = "病案号，可省略前导零")
            @RequestParam(required = false) String bah,
            @Parameter(description = "上架号，可省略前导零")
            @RequestParam(required = false) String sjh) {
        String normalizedBah = MedicalRecordCodeUtils.normalizeOrEmpty(bah);
        String normalizedSjh = MedicalRecordCodeUtils.normalizeOrEmpty(sjh);
        if (normalizedBah.isEmpty() && normalizedSjh.isEmpty()) {
            return Result.fail("病案号和上架号不能同时为空");
        }
        List<Scan> list = scanService.getImageListByCode(
                normalizedBah,
                MedicalRecordCodeUtils.toSearchTerm(bah),
                normalizedSjh,
                MedicalRecordCodeUtils.toSearchTerm(sjh)
        );
        List<BAHDataResponseDTO> items = imageUrlService.toDtoList(list);
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

        logger.info("获取图片:{}", resolvedPath);

        if (!Files.exists(resolvedPath) || !Files.isRegularFile(resolvedPath)) {
            logger.error("文件不存在:{}", resolvedPath);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.fail("图片不存在"));
        }

        FileSystemResource resource = new FileSystemResource(resolvedPath.toFile());

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline;filename=" + FILENAME);
        headers.add("Cache-Control", "public, max-age=86400, immutable");
        headers.setContentType(MediaType.IMAGE_JPEG);

        try {
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(Files.size(resolvedPath))
                    .body(resource);
        } catch (IOException e) {
            logger.error("文件读取错误:{}", resolvedPath, e);
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

    @Operation(summary = "获取图片URL")
    @GetMapping("/url/{id}")
    public Result<String> getImageUrl(
            @PathVariable
            @Parameter(description = "扫描记录 ID", example = "1")
            Integer id) {
        Scan scan = scanService.findById(id);
        if (scan == null) {
            return Result.fail("扫描记录不存在");
        }
        String url = imageUrlService.buildImageUrl(scan);
        if (url == null) {
            return Result.fail("无法构造图片URL，缺少必要字段");
        }
        return Result.<String>successWithData(url);
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
}
