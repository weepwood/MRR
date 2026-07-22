package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.dto.req.MigrationJobRequest;
import com.zjcxph.imgapi.dto.req.MigrationRetryRequest;
import com.zjcxph.imgapi.dto.req.OssUploadRequest;
import com.zjcxph.imgapi.dto.resp.MigrationReadinessDTO;
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
    private static final int MAX_MANUAL_UPLOAD_SIZE = 500;
    private static final int MAX_PENDING_LIMIT = 1_000;

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
        if (request.getScanIds().size() > MAX_MANUAL_UPLOAD_SIZE) {
            return Result.fail("单次手工上传最多 " + MAX_MANUAL_UPLOAD_SIZE + " 条记录，请使用迁移任务");
        }

        try {
            List<OssUploadResult> results = new ArrayList<>();
            for (Integer scanId : request.getScanIds()) {
                results.add(migrationService.uploadSingleScan(scanId));
            }
            return Result.success(uploadSummary(results)).message("上传完成");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.fail(e.getMessage());
        }
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
        try {
            List<OssUploadResult> results = migrationService.uploadByBah(bah.trim());
            Map<String, Object> response = uploadSummary(results);
            response.put("bah", bah.trim());
            return Result.success(response).message("上传完成");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.fail(e.getMessage());
        }
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
            return Result.fail("获取 OSS URL 失败");
        }
    }

    @Operation(summary = "获取迁移统计信息")
    @GetMapping("/migration/statistics")
    public Result<MigrationStatisticsDTO> getMigrationStatistics() {
        return Result.success(migrationService.getStatistics());
    }

    @Operation(summary = "执行迁移前检查")
    @GetMapping("/migration/readiness")
    public Result<MigrationReadinessDTO> getMigrationReadiness(
            @RequestParam(defaultValue = "100") int sampleSize) {
        return Result.success(migrationService.getReadiness(sampleSize));
    }

    @Operation(summary = "获取待迁移记录列表")
    @GetMapping("/migration/pending")
    public Result<Map<String, Object>> getPendingMigrations(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String folder) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_PENDING_LIMIT));
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
            return Result.fail("删除失败");
        }
    }

    @Operation(summary = "创建分阶段迁移任务")
    @PostMapping("/migration/jobs")
    @RequirePermissions({"record:manage"})
    public Result<MigrationJob> createMigrationJob(
            @RequestBody(required = false) MigrationJobRequest request) {
        try {
            MigrationJob job = migrationService.createMigrationJob(request);
            if (job == null) {
                return Result.fail("当前范围没有可迁移文件");
            }
            if (Boolean.TRUE.equals(job.getReused())) {
                return Result.<MigrationJob>success("已有迁移任务正在运行，已返回现有任务").data(job);
            }
            logger.info("OSS migration job created: id={}, mode={}, total={}",
                    job.getId(), job.getMode(), job.getTotalCount());
            return Result.<MigrationJob>success("迁移任务已创建").data(job);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.fail(e.getMessage());
        }
    }

    @Operation(summary = "安全取消迁移任务")
    @PostMapping("/migration/jobs/{id}/cancel")
    @RequirePermissions({"record:manage"})
    public Result<MigrationJob> cancelMigrationJob(@PathVariable Long id) {
        MigrationJob job = migrationService.cancelMigrationJob(id);
        if (job == null) {
            return Result.fail("任务不存在");
        }
        return Result.<MigrationJob>success("已提交安全取消请求").data(job);
    }

    @Operation(summary = "重置失败记录为待迁移")
    @PostMapping("/migration/retry")
    @RequirePermissions({"record:manage"})
    public Result<Map<String, Object>> retryFailedScans(@RequestBody MigrationRetryRequest request) {
        try {
            int updated = migrationService.retryFailedScans(request == null ? null : request.getScanIds());
            return Result.success(Map.<String, Object>of("updated", updated))
                    .message("失败记录已重置，可重新创建试迁移或批次任务");
        } catch (IllegalArgumentException | IllegalStateException e) {
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

    private Map<String, Object> uploadSummary(List<OssUploadResult> results) {
        long successCount = results.stream()
                .filter(result -> "success".equals(result.getStatus()) || "skipped".equals(result.getStatus()))
                .count();
        long waitingSjhCount = results.stream()
                .filter(result -> "waiting_sjh".equals(result.getStatus()))
                .count();
        long failedCount = results.size() - successCount - waitingSjhCount;
        Map<String, Object> response = new HashMap<>();
        response.put("results", results);
        response.put("total", results.size());
        response.put("success", successCount);
        response.put("waitingSjh", waitingSjhCount);
        response.put("failed", failedCount);
        return response;
    }
}
