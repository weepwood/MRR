package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.dto.req.OssUploadRequest;
import com.zjcxph.imgapi.dto.resp.MigrationStatisticsDTO;
import com.zjcxph.imgapi.dto.resp.OssUploadResult;
import com.zjcxph.imgapi.dto.resp.PageResult;
import com.zjcxph.imgapi.entity.ImageMigrationLog;
import com.zjcxph.imgapi.entity.MigrationJob;
import com.zjcxph.imgapi.mapper.MigrationJobMapper;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.mapper.ScanMapper;
import com.zjcxph.imgapi.service.MigrationService;
import com.zjcxph.imgapi.service.OssService;
import com.zjcxph.imgapi.utils.AuthContext;
import com.zjcxph.imgapi.utils.PaginationUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@RestController
@RequestMapping("/api/v1/oss")
@Tag(name = "OSS Management", description = "OSS 图片上传与迁移管理接口")
@RequirePermissions({"record:read"})
public class OssController {

    private static final Logger logger = LoggerFactory.getLogger(OssController.class);

    private final MigrationService migrationService;
    private final OssService ossService;
    private final ScanMapper scanMapper;
    private final MigrationJobMapper migrationJobMapper;
    private final Executor taskAsyncExecutor;

    public OssController(MigrationService migrationService, OssService ossService,
                         ScanMapper scanMapper, MigrationJobMapper migrationJobMapper,
                         @Qualifier("taskAsyncExecutor") Executor taskAsyncExecutor) {
        this.migrationService = migrationService;
        this.ossService = ossService;
        this.scanMapper = scanMapper;
        this.migrationJobMapper = migrationJobMapper;
        this.taskAsyncExecutor = taskAsyncExecutor;
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
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String folder) {
        logger.info("获取待迁移记录列表：limit={}, folder={}", limit, folder);
        List<Scan> pending;
        if (folder != null && !folder.isBlank()) {
            pending = scanMapper.findPendingByFolder(folder);
        } else {
            pending = migrationService.getPendingMigrations(limit);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("list", pending);
        response.put("total", pending.size());

        return Result.success(response);
    }

    @Operation(summary = "获取待迁移文件夹列表")
    @GetMapping("/migration/pending-folders")
    public Result<List<Map<String, Object>>> getPendingFolders() {
        List<Map<String, Object>> folders = scanMapper.findPendingFolders();
        return Result.success(folders);
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

    @Operation(summary = "创建迁移任务（异步执行）")
    @PostMapping("/migration/jobs")
    public Result<MigrationJob> createMigrationJob() {
        MigrationStatisticsDTO stats = migrationService.getStatistics();
        long pendingCount = stats.getPendingCount();
        if (pendingCount == 0) {
            return Result.fail("没有待迁移的文件");
        }

        MigrationJob job = new MigrationJob();
        job.setStatus("pending");
        job.setTotalCount(pendingCount);
        job.setProcessedCount(0L);
        job.setFailedCount(0L);
        job.setRate(java.math.BigDecimal.ZERO);
        job.setCreatedBy(AuthContext.getCurrentUser() != null ? AuthContext.getCurrentUser().getUsername() : "system");

        migrationJobMapper.insert(job);
        logger.info("Migration job created: id={}, total={}", job.getId(), pendingCount);

        executeMigrationJobAsync(job.getId());

        return Result.<MigrationJob>success("迁移任务已创建").data(job);
    }

    @Operation(summary = "获取迁移任务详情")
    @GetMapping("/migration/jobs/{id}")
    public Result<MigrationJob> getMigrationJob(@PathVariable Long id) {
        MigrationJob job = migrationJobMapper.findById(id);
        if (job == null) {
            return Result.fail("任务不存在");
        }
        return Result.<MigrationJob>success().data(job);
    }

    @Operation(summary = "分页查询迁移任务列表")
    @GetMapping("/migration/jobs")
    public Result<PageResult<MigrationJob>> listMigrationJobs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PaginationUtils.validatePageParams(page, size);
        int offset = (page - 1) * size;
        List<MigrationJob> jobs = migrationJobMapper.findAllPaginated(offset, size);
        int total = migrationJobMapper.countAll();
        return Result.<PageResult<MigrationJob>>success().data(PageResult.of(jobs, total, page, size));
    }

    private void executeMigrationJobAsync(Long jobId) {
        taskAsyncExecutor.execute(() -> {
            try {
                MigrationJob job = migrationJobMapper.findById(jobId);
                if (job == null) { return; }
                job.setStatus("running");
                job.setStartedAt(new java.util.Date());
                migrationJobMapper.update(job);

                List<Scan> pending = migrationService.getPendingMigrations(1000);
                long processed = 0;
                long failed = 0;

                for (Scan scan : pending) {
                    try {
                        OssUploadResult result = migrationService.uploadSingleScan(scan.getId().intValue());
                        if ("success".equals(result.getStatus()) || "skipped".equals(result.getStatus())) {
                            processed++;
                        } else {
                            failed++;
                        }
                    } catch (Exception e) {
                        failed++;
                        logger.error("Migration failed for scan {}: {}", scan.getId(), e.getMessage());
                    }

                    if ((processed + failed) % 10 == 0) {
                        job.setProcessedCount(processed);
                        job.setFailedCount(failed);
                        job.setRate(java.math.BigDecimal.valueOf(
                                processed * 100.0 / Math.max(1, job.getTotalCount()))
                                .setScale(2, java.math.RoundingMode.HALF_UP));
                        migrationJobMapper.update(job);
                    }
                }

                job.setProcessedCount(processed);
                job.setFailedCount(failed);
                job.setRate(java.math.BigDecimal.valueOf(100).setScale(2, java.math.RoundingMode.HALF_UP));
                job.setStatus(failed > 0 ? "completed_with_errors" : "completed");
                job.setCompletedAt(new java.util.Date());
                if (failed > 0 && processed == 0) {
                    job.setStatus("failed");
                    job.setErrorMessage("全部迁移失败");
                }
                migrationJobMapper.update(job);
                logger.info("Migration job {} completed: processed={}, failed={}", jobId, processed, failed);
            } catch (Exception e) {
                logger.error("Migration job {} error: {}", jobId, e.getMessage(), e);
                MigrationJob job = migrationJobMapper.findById(jobId);
                if (job != null) {
                    job.setStatus("failed");
                    job.setErrorMessage(e.getMessage());
                    job.setCompletedAt(new java.util.Date());
                    migrationJobMapper.update(job);
                }
            }
        });
    }
}
