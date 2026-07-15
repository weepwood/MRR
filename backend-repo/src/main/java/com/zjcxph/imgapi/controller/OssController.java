package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.dto.req.OssUploadRequest;
import com.zjcxph.imgapi.dto.resp.MigrationStatisticsDTO;
import com.zjcxph.imgapi.dto.resp.OssUploadResult;
import com.zjcxph.imgapi.dto.resp.PageResult;
import com.zjcxph.imgapi.entity.ImageMigrationLog;
import com.zjcxph.imgapi.entity.MigrationJob;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.service.MigrationService;
import com.zjcxph.imgapi.service.OssService;
import com.zjcxph.imgapi.utils.PaginationUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/oss")
@Tag(name = "OSS Management", description = "OSS 图片上传与迁移管理接口")
@RequirePermissions({"record:read"})
public class OssController {

    private static final Logger logger = LoggerFactory.getLogger(OssController.class);
    private static final int MAX_MANUAL_BATCH_SIZE = 1000;
    private static final int MAX_PENDING_PAGE_SIZE = 1000;

    private final MigrationService migrationService;
    private final OssService ossService;

    public OssController(MigrationService migrationService, OssService ossService) {
        this.migrationService = migrationService;
        this.ossService = ossService;
    }

    @Operation(summary = "按 Scan ID 批量上传图片到 OSS")
    @PostMapping("/upload")
    @RequirePermissions({"record:manage"})
    public Result<Map<String, Object>> upload(@RequestBody OssUploadRequest request) {
        if (request == null || request.getScanIds() == null || request.getScanIds().isEmpty()) {
            return Result.fail("scanIds 不能为空");
        }
        if (request.getScanIds().size() > MAX_MANUAL_BATCH_SIZE) {
            return Result.fail("单次手工上传最多 " + MAX_MANUAL_BATCH_SIZE + " 条记录");
        }

        logger.info("开始 OSS 手工上传，共 {} 条记录", request.getScanIds().size());
        List<OssUploadResult> results = new ArrayList<>();
        try {
            for (Integer scanId : request.getScanIds()) {
                results.add(migrationService.uploadSingleScan(scanId));
            }
        } catch (IllegalStateException e) {
            return Result.fail(e.getMessage());
        }

        long successCount = results.stream()
                .filter(r -> "success".equals(r.getStatus()) || "skipped".equals(r.getStatus()))
                .count();
        long failedCount = results.stream().filter(r -> "failed".equals(r.getStatus())).count();

        Map<String, Object> response = new HashMap<>();
        response.put("results", results);
        response.put("total", results.size());
        response.put("success", successCount);
        response.put("failed", failedCount);
        return Result.success(response).message("上传完成");
    }

    @Operation(summary = "按病案号批量上传图片到 OSS")
    @PostMapping("/upload/bah/{bah}")
    @RequirePermissions({"record:manage"})
    public Result<Map<String, Object>> uploadByBah(
            @PathVariable
            @Parameter(description = "病案号", example = "00789508")
            String bah) {
        if (bah == null || bah.isBlank()) {
            return Result.fail("病案号不能为空");
        }

        List<OssUploadResult> results;
        try {
            results = migrationService.uploadByBah(bah);
        } catch (IllegalStateException e) {
            return Result.fail(e.getMessage());
        }

        long successCount = results.stream()
                .filter(r -> "success".equals(r.getStatus()) || "skipped".equals(r.getStatus()))
                .count();
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
            String signedUrl = migrationService.getOssSignedUrl(scanId);
            if (signedUrl == null) {
                return Result.fail("该记录未迁移到 OSS");
            }
            Map<String, Object> response = new HashMap<>();
            response.put("scanId", scanId);
            response.put("ossUrl", signedUrl);
            return Result.success(response);
        } catch (Exception e) {
            logger.error("获取 OSS URL 失败：scanId={}", scanId, e);
            return Result.fail("获取 OSS URL 失败：" + e.getMessage());
        }
    }

    @Operation(summary = "获取迁移统计信息")
    @GetMapping("/migration/statistics")
    public Result<MigrationStatisticsDTO> getMigrationStatistics() {
        return Result.success(migrationService.getStatistics());
    }

    @Operation(summary = "获取待迁移记录列表")
    @GetMapping("/migration/pending")
    public Result<Map<String, Object>> getPendingMigrations(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String folder) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_PENDING_PAGE_SIZE));
        List<Scan> pending = migrationService.getPendingMigrations(safeLimit, folder);
        Map<String, Object> response = new HashMap<>();
        response.put("list", pending);
        response.put("total", pending.size());
        response.put("limit", safeLimit);
        return Result.success(response);
    }

    @Operation(summary = "获取待迁移文件夹列表")
    @GetMapping("/migration/pending-folders")
    public Result<List<Map<String, Object>>> getPendingFolders() {
        return Result.success(migrationService.getPendingFolders());
    }

    @Operation(summary = "获取迁移日志列表")
    @GetMapping("/migration/logs")
    public Result<PageResult<ImageMigrationLog>> getMigrationLogs(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PaginationUtils.validatePageParams(page, size);
        List<ImageMigrationLog> logs = migrationService.getMigrationLogs(status, page, size);
        long total = migrationService.countMigrationLogs(status);
        for (ImageMigrationLog log : logs) {
            migrationService.enrichWithPresignedUrl(log);
        }
        return Result.success(PageResult.of(logs, total, page, size));
    }

    @Operation(summary = "删除 OSS 文件")
    @DeleteMapping("/{ossKey}")
    @RequirePermissions({"record:manage"})
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

    @Operation(summary = "创建迁移任务（异步执行）")
    @PostMapping("/migration/jobs")
    @RequirePermissions({"record:manage"})
    public Result<MigrationJob> createMigrationJob() {
        try {
            MigrationJob job = migrationService.createMigrationJob();
            if (job == null) {
                return Result.fail("没有可迁移的文件");
            }
            logger.info("Migration job created: id={}, total={}, maxScanId={}",
                    job.getId(), job.getTotalCount(), job.getMaxScanId());
            return Result.<MigrationJob>success("迁移任务已创建").data(job);
        } catch (IllegalStateException e) {
            return Result.fail(e.getMessage());
        }
    }

    @Operation(summary = "获取迁移任务详情")
    @GetMapping("/migration/jobs/{id}")
    public Result<MigrationJob> getMigrationJob(@PathVariable Long id) {
        MigrationJob job = migrationService.getMigrationJob(id);
        if (job == null) {
            return Result.fail("任务不存在");
        }
        return Result.success(job);
    }

    @Operation(summary = "分页查询迁移任务列表")
    @GetMapping("/migration/jobs")
    public Result<PageResult<MigrationJob>> listMigrationJobs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PaginationUtils.validatePageParams(page, size);
        return Result.success(migrationService.listMigrationJobs(page, size));
    }
}
