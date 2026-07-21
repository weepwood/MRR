package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.dto.req.ImageRequest;
import com.zjcxph.imgapi.dto.resp.ArchiveLookupResult;
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
import jakarta.servlet.http.HttpServletResponse;
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
            "病案号大于等于 10000000 时必须使用上架号查询";
    private static final String LOOKUP_STRATEGY_HEADER = "X-MRR-Lookup-Strategy";
    private static final String LOOKUP_ARCHIVE_ID_HEADER = "X-MRR-Archive-Id";
    private static final String LOOKUP_FALLBACK_REASON_HEADER = "X-MRR-Fallback-Reason";
    private static final String LOOKUP_IMAGE_COUNT_HEADER = "X-MRR-Image-Count";

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
    @RequirePermissions({"record:download"})
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
            String bah,
            HttpServletResponse response) {
        String normalizedBah = MedicalRecordCodeUtils.normalizeOrEmpty(bah);
        if (MedicalRecordCodeUtils.requiresSjhForBah(normalizedBah)) {
            return Result.fail(BAH_REQUIRES_SJH_MESSAGE + "，请使用 /search 接口");
        }
        String bahSearchCode = MedicalRecordCodeUtils.toSearchTerm(bah);
        ArchiveLookupResult lookupResult = scanService.getImageLookupByBAH(normalizedBah, bahSearchCode);
        applyLookupMetadata(response, lookupResult);
        List<BAHDataResponseDTO> items = imageUrlService.toDtoList(lookupResult.scans());
        return Result.success(items).message(normalizedBah + " 数据获取成功");
    }

    @Operation(summary = "按统一规则查询图片数据")
    @GetMapping("/search")
    public Result<List<BAHDataResponseDTO>> searchByCode(
            @Parameter(description = "病案号；小于 10000000 时作为查询键，大于等于 10000000 时仅用于审计和展示")
            @Pattern(regexp = "\\d{1,8}", message = "请输入 1-8 位数字病案号")
            @RequestParam(required = false) String bah,
            @Parameter(description = "唯一上架号；病案号大于等于 10000000 时作为查询键")
            @Pattern(regexp = "\\d{1,8}", message = "请输入 1-8 位数字上架号")
            @RequestParam(required = false) String sjh,
            @Parameter(description = "调用方内网系统当前用户 ID")
            @RequestParam(required = false) String userid,
            HttpServletRequest request,
            HttpServletResponse response) {
        String normalizedBah = MedicalRecordCodeUtils.normalizeOrEmpty(bah);
        String normalizedSjh = MedicalRecordCodeUtils.normalizeOrEmpty(sjh);
        if (normalizedBah.isEmpty() && normalizedSjh.isEmpty()) {
            return Result.fail("病案号和上架号不能同时为空");
        }

        boolean useSjh = normalizedBah.isEmpty()
                || MedicalRecordCodeUtils.requiresSjhForBah(normalizedBah);
        if (useSjh && normalizedSjh.isEmpty()) {
            return Result.fail(BAH_REQUIRES_SJH_MESSAGE);
        }

        archiveAccessService.verifyAndRecord(userid, normalizedBah, normalizedSjh, request);

        String queryBah = useSjh ? "" : normalizedBah;
        String queryBahSearchCode = useSjh ? "" : MedicalRecordCodeUtils.toSearchTerm(bah);
        String querySjh = useSjh ? normalizedSjh : "";
        String querySjhSearchCode = useSjh ? MedicalRecordCodeUtils.toSearchTerm(sjh) : "";

        ArchiveLookupResult lookupResult = scanService.getImageLookupByCode(
                queryBah,
                queryBahSearchCode,
                querySjh,
                querySjhSearchCode
        );
        applyLookupMetadata(response, lookupResult);
        List<BAHDataResponseDTO> items = imageUrlService.toDtoList(lookupResult.scans());
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
            headers.setContentLength(contentLength);
            return new ResponseEntity<>(resource, headers, HttpStatus.OK);
        } catch (InvalidImagePathException exception) {
            logger.warn("非法图片路径请求被拒绝: BAH={}, BRXH={}, FOLDER={}, FILENAME={}",
                    BAH, BRXH, FOLDER, FILENAME);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("非法图片路径");
        } catch (FileNotFoundException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("图片不存在");
        } catch (IOException exception) {
            logger.error("读取本地图片失败", exception);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("图片读取失败");
        }
    }

    @Operation(summary = "更新图片类型")
    @PutMapping("/{id}/type")
    @RequirePermissions({"record:edit"})
    public Result<String> updateImageType(
            @PathVariable Integer id,
            @RequestBody ImageRequest request) {
        if (request == null || request.getBtype() == null) {
            return Result.fail("图片类型不能为空");
        }
        int updated = scanService.updateImageType(id, request.getBtype());
        return updated > 0 ? Result.success("图片类型更新成功") : Result.fail("图片类型更新失败");
    }

    @Operation(summary = "获取 OSS 图片访问地址")
    @GetMapping("/oss-url")
    public Result<String> getOssUrl(@RequestParam String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return Result.fail("objectKey 不能为空");
        }
        try {
            return Result.success(ossService.generatePresignedUrl(objectKey));
        } catch (Exception exception) {
            logger.error("生成 OSS 访问地址失败", exception);
            return Result.fail("生成 OSS 访问地址失败");
        }
    }

    private void applyLookupMetadata(HttpServletResponse response, ArchiveLookupResult result) {
        response.setHeader(LOOKUP_STRATEGY_HEADER, result.strategy().name());
        response.setHeader(LOOKUP_IMAGE_COUNT_HEADER, String.valueOf(result.resultCount()));
        if (result.archiveId() != null) {
            response.setHeader(LOOKUP_ARCHIVE_ID_HEADER, String.valueOf(result.archiveId()));
        }
        if (result.fallbackReason() != null) {
            response.setHeader(LOOKUP_FALLBACK_REASON_HEADER, result.fallbackReason().name());
        }
    }
}
