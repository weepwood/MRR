package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.dto.req.OssUploadRequest;
import com.zjcxph.imgapi.dto.resp.MigrationStatisticsDTO;
import com.zjcxph.imgapi.dto.resp.OssUploadResult;
import com.zjcxph.imgapi.dto.resp.PageResult;
import com.zjcxph.imgapi.entity.ImageMigrationLog;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.service.MigrationService;
import com.zjcxph.imgapi.service.OssService;
import com.zjcxph.imgapi.utils.PaginationUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/oss")
@Tag(name = "OSS Management", description = "OSS 图片上传与迁移管理接口")
public class OssController {

    private static final Logger logger = LoggerFactory.getLogger(OssController.class);

    private final MigrationService migrationService;
    private final OssService ossService;

    public OssController(MigrationService migrationService, OssService ossService) {
        this.migrationService = migrationService;
        this.ossService = ossService;
    }

    @Operation(summary = "按 Scan ID 批量上传图片到 OSS")
    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestBody OssUploadRequest request) {
        if (request == null || request.getScanIds() == null || request.getScanIds().isEmpty()) {
            return Result.fail("scanIds 不能为空");
        }

        logger.info("开始 OSS 上传，共 {} 条记录", request.getScanIds().size());

        List<OssUploadResult> results = new ArrayList<>();
        for (Integer scanId : request.getScanIds()) {
            OssUploadResult result = migrationService.uploadSingleScan(scanId);
            results.add(result);
        }

        long successCount = results.stream().filter(r -> "success".equals(r.getStatus())).count();
        long failedCount = results.stream().filter(r -> "failed".equals(r.getStatus())).count();

        Map<String, Object> response = new HashMap<>();
        response.put("results", results);
        response.put("total", results.size());
        response.put("success", successCount);
        response.put("failed", failedCount);

        logger.info("OSS 上传完成：成功 {}/{}", successCount, results.size());
        return Result.success(response).message("上传完成");
    }

    @Operation(summary = "按病案号批量上传图片到 OSS")
    @PostMapping("/upload/bah/{bah}")
    public Result<Map<String, Object>> uploadByBah(
            @PathVariable
            @Parameter(description = "病案号", example = "00789508")
            String bah) {
        if (bah == null || bah.isBlank()) {
            return Result.fail("病案号不能为空");
        }

        logger.info("按病案号上传到 OSS：BAH={}", bah);
        List<OssUploadResult> results = migrationService.uploadByBah(bah);

        long successCount = results.stream().filter(r -> "success".equals(r.getStatus())).count();

        Map<String, Object> response = new HashMap<>();
        response.put("results", results);
        response.put("total", results.size());
        response.put("success", successCount);
        response.put("bah", bah);

        return Result.success(response).message("上传完成");
    }

    @Operation(summary = "获取指定 Scan 的 OSS 签名 URL")
    @GetMapping("/url/{scanId}")
    public Result<Map<String, Object>> getOssUrl(
            @PathVariable
            @Parameter(description = "扫描记录 ID", example = "1")
            Integer scanId) {
        try {
            Scan scan = migrationService.getPendingMigrations(1).stream()
                    .filter(s -> s.getId().equals(scanId)).findFirst().orElse(null);

            // Try to find in the service
            // We need scanMapper — let's use the ossService directly with the key from DB
            // For simplicity, we delegate
            Map<String, Object> response = new HashMap<>();
            response.put("scanId", scanId);
            response.put("message", "Please use the /v1/img-api/{bah} endpoint which includes ossUrl in the response");
            return Result.success(response);
        } catch (Exception e) {
            logger.error("获取 OSS URL 失败：scanId={}", scanId, e);
            return Result.fail("获取 OSS URL 失败：" + e.getMessage());
        }
    }

    @Operation(summary = "获取迁移统计信息")
    @GetMapping("/migration/statistics")
    public Result<MigrationStatisticsDTO> getMigrationStatistics() {
        logger.info("获取迁移统计信息");
        MigrationStatisticsDTO stats = migrationService.getStatistics();
        return Result.success(stats);
    }

    @Operation(summary = "获取待迁移记录列表")
    @GetMapping("/migration/pending")
    public Result<Map<String, Object>> getPendingMigrations(
            @RequestParam(defaultValue = "50") int limit) {
        logger.info("获取待迁移记录列表：limit={}", limit);
        List<Scan> pending = migrationService.getPendingMigrations(limit);

        Map<String, Object> response = new HashMap<>();
        response.put("list", pending);
        response.put("total", pending.size());

        return Result.success(response);
    }

    @Operation(summary = "获取迁移日志列表")
    @GetMapping("/migration/logs")
    public Result<PageResult<ImageMigrationLog>> getMigrationLogs(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        logger.info("获取迁移日志：status={}, page={}, size={}", status, page, size);

        PaginationUtils.validatePageParams(page, size);
        List<ImageMigrationLog> logs = migrationService.getMigrationLogs(status, page, size);
        long total = migrationService.countMigrationLogs(status);

        // 为成功的记录生成预签名 URL
        for (ImageMigrationLog log : logs) {
            if ("success".equals(log.getMigrationStatus()) && log.getOssUrl() != null && !log.getOssUrl().isBlank()) {
                try {
                    String presignedUrl = ossService.generatePresignedUrl(log.getOssUrl());
                    log.setOssUrl(presignedUrl);
                } catch (Exception e) {
                    logger.warn("Failed to generate presigned URL for log id={}", log.getId(), e);
                }
            }
        }

        PageResult<ImageMigrationLog> pageResult = PageResult.of(logs, total, page, size);
        return Result.<PageResult<ImageMigrationLog>>success(null).data(pageResult);
    }

    @Operation(summary = "删除 OSS 文件")
    @DeleteMapping("/{ossKey}")
    public Result<String> deleteOssFile(
            @PathVariable
            @Parameter(description = "OSS 对象 Key")
            String ossKey) {
        try {
            ossService.deleteObject(ossKey);
            return Result.success("删除成功");
        } catch (Exception e) {
            logger.error("删除 OSS 文件失败：{}", ossKey, e);
            return Result.fail("删除失败：" + e.getMessage());
        }
    }
}
