package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.dto.req.ImageRequest;
import com.zjcxph.imgapi.dto.resp.BAHDataResponseDTO;
import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.service.ArchiveAccessService;
import com.zjcxph.imgapi.service.ArchiveExportService;
import com.zjcxph.imgapi.service.ImageUrlService;
import com.zjcxph.imgapi.service.OssService;
import com.zjcxph.imgapi.service.ScanService;
import com.zjcxph.imgapi.storage.ImageStorage;
import com.zjcxph.imgapi.storage.InvalidImagePathException;
import com.zjcxph.imgapi.utils.MedicalRecordCodeUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
    private static final String BAH_REQUIRES_SJH_MESSAGE =
            "病案号大于等于 10000000 时必须同时提供上架号";

    private final ScanService scanService;
    private final ArchiveExportService archiveExportService;
    private final ImageStorage imageStorage;
    private final OssService ossService;
    private final ImageUrlService imageUrlService;
    private final ArchiveAccessService archiveAccessService;

    public ImageController(ScanService scanService,
                           ArchiveExportService archiveExportService,
                           ImageStorage imageStorage,
                           OssService ossService,
                           ImageUrlService imageUrlService,
                           ArchiveAccessService archiveAccessService) {
        this.scanService = scanService;
        this.archiveExportService = archiveExportService;
        this.imageStorage = imageStorage;
        this.ossService = ossService;
        this.imageUrlService = imageUrlService;
        this.archiveAccessService = archiveAccessService;
    }

    @Operation(summary = "服务器心跳")
    @GetMapping("/hello")
    public Result<Map<String, Object>> hello() {
        Map<String, Object> data = new HashMap<>();
        data.put("message", "服务正常");
        return Result.success(data);
    }

    @Operation(summary = "下载病案压缩包")
    @GetMapping("/download/{BAH}")
    public ResponseEntity<StreamingResponseBody> download(
            @PathVariable
            @Pattern(regexp = "\\d{1,8}", message = "请输入 1-8 位数字病案号")
            @Parameter(description = "病案号，可省略前导零", example = "789508")
            String BAH,
            @RequestParam(required = false)
            @Pattern(regexp = "\\d{1,8}", message = "请输入 1-8 位数字上架号")
            @Parameter(description = "唯一上架号；病案号大于等于 10000000 时必填")
            String sjh) {
        String normalizedBah = MedicalRecordCodeUtils.normalizeOrEmpty(BAH);
        String normalizedSjh = MedicalRecordCodeUtils.normalizeOrEmpty(sjh);
        if (MedicalRecordCodeUtils.requiresSjhForBah(normalizedBah) && normalizedSjh.isEmpty()) {
            throw new BusinessException(400, BAH_REQUIRES_SJH_MESSAGE);
        }

        ArchiveExportService.BatchZipExport export =
                archiveExportService.prepareArchive(normalizedBah, normalizedSjh);
        if (export.itemCount() == 0) {
            throw new BusinessException(404, "未找到匹配档案的图片");
        }

        StreamingResponseBody body = outputStream ->
                archiveExportService.writeBatchZip(export, outputStream);
        String archiveCode = normalizedBah + (normalizedSjh.isEmpty() ? "" : "-" + normalizedSjh);
        String fileName = archiveCode + ".zip";
        String contentDisposition = ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8)
                .build()
                .toString();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }

    @Operation(summary = "获取唯一病案号下的图片数据")
    @GetMapping("/{bah}")
    public Result<List<BAHDataResponseDTO>> getDataByBAH(
            @PathVariable
            @Pattern(regexp = "\\d{1,8}", message = "请输入 1-8 位数字病案号")
            @Parameter(description = "小于 10000000 的唯一病案号，可省略前导零", example = "789508")
            String bah) {
        String normalizedBah = MedicalRecordCodeUtils.normalizeOrEmpty(bah);
        if (MedicalRecordCodeUtils.requiresSjhForBah(normalizedBah)) {
            return Result.fail(BAH_REQUIRES_SJH_MESSAGE + "，请使用 /search 接口");
        }
        String bahSearchCode = MedicalRecordCodeUtils.toSearchTerm(bah);
        List<Scan> imageListByBAH = scanService.getImageListByBAH(normalizedBah, bahSearchCode);
        List<BAHDataResponseDTO> items = imageUrlService.toDtoList(imageListByBAH);
        return Result.success(items).message(normalizedBah + " 数据获取成功");
    }

    @Operation(summary = "按病案号和/或唯一上架号查询图片数据")
    @GetMapping("/search")
    public Result<List<BAHDataResponseDTO>> searchByCode(
            @Parameter(description = "病案号，可省略前导零；大于等于 10000000 时必须同时传上架号")
            @Pattern(regexp = "\\d{1,8}", message = "请输入 1-8 位数字病案号")
            @RequestParam(required = false) String bah,
            @Parameter(description = "唯一上架号，可省略前导零")
            @Pattern(regexp = "\\d{1,8}", message = "请输入 1-8 位数字上架号")
            @RequestParam(required = false) String sjh,
            @Parameter(description = "调用方内网系统当前用户 ID")
            @RequestParam(required = false) String userid,
            HttpServletRequest request) {
        String normalizedBah = MedicalRecordCodeUtils.normalizeOrEmpty(bah);
        String normalizedSjh = MedicalRecordCodeUtils.normalizeOrEmpty(sjh);
        if (normalizedBah.isEmpty() && normalizedSjh.isEmpty()) {
            return Result.fail("病案号和上架号不能同时为空");
        }
        if (MedicalRecordCodeUtils.requiresSjhForBah(normalizedBah) && normalizedSjh.isEmpty()) {
            return Result.fail(BAH_REQUIRES_SJH_MESSAGE);
        }

        archiveAccessService.verifyAndRecord(userid, normalizedBah, normalizedSjh, request);

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
        PathDO image = new PathDO(FOLDER, FILENAME, BRXH, BAH);
        try {
            long contentLength = imageStorage.size(image);
            InputStreamResource resource = new InputStreamResource(imageStorage.open(image));
            MediaType mediaType = MediaTypeFactory.getMediaType(FILENAME)
                    .orElse(MediaType.APPLICATION_OCTET_STREAM);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentDisposition(ContentDisposition.inline()
                    .filename(FILENAME, StandardCharsets.UTF_8)
                    .build());
            headers.setCacheControl("public, max-age=86400, immutable");
            headers.setContentType(mediaType);

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(contentLength)
                    .body(resource);
        } catch (InvalidImagePathException exception) {
            logger.warn("拒绝非法影像路径: BAH={}, BRXH={}, FOLDER={}, FILENAME={}, reason={}",
                    BAH, BRXH, FOLDER, FILENAME, exception.getMessage());
            return ResponseEntity.badRequest().body(Result.fail(exception.getMessage()));
        } catch (FileNotFoundException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.fail("图片不存在"));
        } catch (IOException exception) {
            logger.error("读取影像失败: BAH={}, BRXH={}, FOLDER={}, FILENAME={}",
                    BAH, BRXH, FOLDER, FILENAME, exception);
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
        return Result.success("修改图片类型成功");
    }

    @Operation(summary = "获取图片URL（遵循系统图片来源设置）")
    @GetMapping("/url/{id}")
    public Result<String> getImageUrl(
            @PathVariable
            @Parameter(description = "扫描记录 ID", example = "1")
            Integer id) {
        Scan scan = scanService.findById(id);
        if (scan == null) {
            return Result.fail("扫描记录不存在");
        }
        String url = imageUrlService.buildPreferredImageUrl(scan);
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
        } catch (Exception exception) {
            logger.error("获取 OSS 图片失败：id={}", id, exception);
            return ResponseEntity.internalServerError()
                    .body(Result.fail("获取 OSS 图片失败：" + exception.getMessage()));
        }
    }
}
