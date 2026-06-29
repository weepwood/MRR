package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.config.ImageProperties;
import com.zjcxph.imgapi.dto.resp.MigrationStatisticsDTO;
import com.zjcxph.imgapi.dto.resp.OssUploadResult;
import com.zjcxph.imgapi.dto.resp.PageResult;
import com.zjcxph.imgapi.entity.ImageMigrationLog;
import com.zjcxph.imgapi.entity.MigrationJob;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.mapper.ImageMigrationLogMapper;
import com.zjcxph.imgapi.mapper.MigrationJobMapper;
import com.zjcxph.imgapi.mapper.ScanMapper;
import com.zjcxph.imgapi.service.MigrationService;
import com.zjcxph.imgapi.service.OssService;
import com.zjcxph.imgapi.utils.AuthContext;
import com.zjcxph.imgapi.utils.PaginationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Service
public class MigrationServiceImpl implements MigrationService {

    private static final Logger logger = LoggerFactory.getLogger(MigrationServiceImpl.class);

    private final OssService ossService;
    private final ScanMapper scanMapper;
    private final ImageMigrationLogMapper migrationLogMapper;
    private final MigrationJobMapper migrationJobMapper;
    private final ImageProperties imageProperties;
    private final Executor taskAsyncExecutor;

    public MigrationServiceImpl(OssService ossService, ScanMapper scanMapper,
                                 ImageMigrationLogMapper migrationLogMapper,
                                 MigrationJobMapper migrationJobMapper,
                                 ImageProperties imageProperties,
                                 @Qualifier("taskAsyncExecutor") Executor taskAsyncExecutor) {
        this.ossService = ossService;
        this.scanMapper = scanMapper;
        this.migrationLogMapper = migrationLogMapper;
        this.migrationJobMapper = migrationJobMapper;
        this.imageProperties = imageProperties;
        this.taskAsyncExecutor = taskAsyncExecutor;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OssUploadResult uploadSingleScan(Integer scanId) {
        Scan scan = scanMapper.findById(scanId);
        if (scan == null) {
            return new OssUploadResult(scanId, "failed", "扫描记录不存在: " + scanId);
        }

        if (scan.getOssUrl() != null && !scan.getOssUrl().isBlank()) {
            OssUploadResult result = new OssUploadResult();
            result.setScanId(scanId);
            result.setOssUrl(scan.getOssUrl());
            result.setStatus("skipped");
            result.setErrorMessage("已迁移过");
            return result;
        }

        String localPath = buildLocalPath(scan);
        if (localPath == null) {
            return new OssUploadResult(scanId, "failed", "无法构建本地路径: " + scanId);
        }

        File localFile = new File(localPath);
        if (!localFile.exists()) {
            logMigration(scanId, localPath, null, "failed", "本地文件不存在: " + localPath, null, null);
            return new OssUploadResult(scanId, "failed", "本地文件不存在: " + localPath);
        }

        try {
            String md5 = ossService.calculateMd5(localPath);
            long fileSize = ossService.getFileSize(localPath);

            String ossKey = buildOssKey(scan);

            if (ossService.doesObjectExist(ossKey)) {
                logger.info("File already exists in OSS, updating DB only: {}", ossKey);
            } else {
                ossService.uploadFile(localPath, ossKey);
            }

            String ossUrl = ossKey;
            scanMapper.updateOssInfo(scanId, ossUrl, fileSize, md5, "migrated");
            logMigration(scanId, localPath, ossUrl, "success", null, fileSize, md5);

            OssUploadResult result = new OssUploadResult();
            result.setScanId(scanId);
            result.setOssUrl(ossService.generatePresignedUrl(ossKey));
            result.setFileSize(fileSize);
            result.setChecksumMd5(md5);
            result.setStatus("success");
            return result;

        } catch (Exception e) {
            logger.error("Failed to upload scan {} to OSS", scanId, e);
            logMigration(scanId, localPath, null, "failed", e.getMessage(), null, null);
            return new OssUploadResult(scanId, "failed", e.getMessage());
        }
    }

    @Override
    public List<OssUploadResult> uploadByBah(String bah) {
        List<Scan> scans = scanMapper.findByBah(bah);
        List<OssUploadResult> results = new ArrayList<>();

        if (scans == null || scans.isEmpty()) {
            results.add(new OssUploadResult(null, "failed", "未找到该病案号的扫描记录: " + bah));
            return results;
        }

        logger.info("Starting batch upload for BAH={}, total {} records", bah, scans.size());

        for (Scan scan : scans) {
            if (scan.getUploadFlag() == null || scan.getUploadFlag() == 0) {
                continue;
            }
            results.add(uploadSingleScan(scan.getId()));
        }

        long successCount = results.stream().filter(r -> "success".equals(r.getStatus())).count();
        logger.info("Batch upload for BAH={} completed: {}/{} succeeded", bah, successCount, results.size());

        return results;
    }

    @Override
    public MigrationStatisticsDTO getStatistics() {
        MigrationStatisticsDTO stats = new MigrationStatisticsDTO();

        Map<String, Object> counts = scanMapper.countMigrationStats();
        long total = ((Number) counts.getOrDefault("total", 0L)).longValue();
        long migrated = ((Number) counts.getOrDefault("migrated", 0L)).longValue();
        long verified = ((Number) counts.getOrDefault("verified", 0L)).longValue();

        stats.setTotalCount(total);
        stats.setMigratedCount(migrated + verified);
        stats.setPendingCount(total - migrated - verified);
        stats.setFailedCount(0);

        if (total > 0) {
            stats.setPercentage(Math.round((migrated + verified) * 10000.0 / total) / 100.0);
        }

        try {
            long failedLogs = migrationLogMapper.countWithFilter("failed");
            stats.setFailedCount(failedLogs);
        } catch (Exception e) {
            logger.warn("Failed to count failed migration logs", e);
        }

        return stats;
    }

    @Override
    public List<Scan> getPendingMigrations(int limit) {
        return scanMapper.findPendingMigration(limit);
    }

    @Override
    public List<Scan> getPendingMigrations(int limit, String folder) {
        if (folder != null && !folder.isBlank()) {
            return scanMapper.findPendingByFolder(folder);
        }
        return getPendingMigrations(limit);
    }

    @Override
    public List<Map<String, Object>> getPendingFolders() {
        return scanMapper.findPendingFolders();
    }

    @Override
    public String getOssSignedUrl(Integer scanId) {
        Scan scan = scanMapper.findById(scanId);
        if (scan == null) {
            return null;
        }
        String ossKey = scan.getOssUrl();
        if (ossKey == null || ossKey.isBlank()) {
            return null;
        }
        return ossService.generatePresignedUrl(ossKey);
    }

    @Override
    public List<ImageMigrationLog> getMigrationLogs(String status, int page, int size) {
        PaginationUtils.validatePageParams(page, size);
        int offset = PaginationUtils.calculateOffset(page, size);
        return migrationLogMapper.findWithPagination(status, offset, size);
    }

    @Override
    public long countMigrationLogs(String status) {
        return migrationLogMapper.countWithFilter(status);
    }

    @Override
    public void enrichWithPresignedUrl(ImageMigrationLog log) {
        if ("success".equals(log.getMigrationStatus()) && log.getOssUrl() != null && !log.getOssUrl().isBlank()) {
            try {
                String presignedUrl = ossService.generatePresignedUrl(log.getOssUrl());
                log.setOssUrl(presignedUrl);
            } catch (Exception e) {
                logger.warn("Failed to generate presigned URL for log id={}", log.getId(), e);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MigrationJob createMigrationJob() {
        MigrationStatisticsDTO stats = getStatistics();
        long pendingCount = stats.getPendingCount();
        if (pendingCount == 0) {
            return null;
        }

        MigrationJob job = new MigrationJob();
        job.setStatus("pending");
        job.setTotalCount(pendingCount);
        job.setProcessedCount(0L);
        job.setFailedCount(0L);
        job.setRate(BigDecimal.ZERO);
        job.setCreatedBy(AuthContext.getCurrentUser() != null ? AuthContext.getCurrentUser().getUsername() : "system");

        migrationJobMapper.insert(job);
        executeMigrationJobAsync(job.getId());
        return job;
    }

    @Override
    public MigrationJob getMigrationJob(Long id) {
        return migrationJobMapper.findById(id);
    }

    @Override
    public PageResult<MigrationJob> listMigrationJobs(int page, int size) {
        int offset = (page - 1) * size;
        List<MigrationJob> jobs = migrationJobMapper.findAllPaginated(offset, size);
        int total = migrationJobMapper.countAll();
        return PageResult.of(jobs, total, page, size);
    }

    private void executeMigrationJobAsync(Long jobId) {
        taskAsyncExecutor.execute(() -> {
            try {
                MigrationJob job = migrationJobMapper.findById(jobId);
                if (job == null) { return; }
                job.setStatus("running");
                job.setStartedAt(new Date());
                migrationJobMapper.update(job);

                long processed = 0;
                long failed = 0;
                int batchSize = 1000;

                while (true) {
                    List<Scan> batch = getPendingMigrations(batchSize);
                    if (batch.isEmpty()) { break; }

                    for (Scan scan : batch) {
                        try {
                            OssUploadResult result = uploadSingleScan(scan.getId());
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
                            job.setRate(BigDecimal.valueOf(processed * 100.0 / Math.max(1, processed + failed))
                                    .setScale(2, RoundingMode.HALF_UP));
                            migrationJobMapper.update(job);
                        }
                    }
                }

                job.setProcessedCount(processed);
                job.setFailedCount(failed);
                job.setRate(BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP));
                job.setStatus(failed > 0 ? "completed_with_errors" : "completed");
                job.setCompletedAt(new Date());
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
                    job.setCompletedAt(new Date());
                    migrationJobMapper.update(job);
                }
            }
        });
    }

    // ==================== Private helpers ====================

    private String buildLocalPath(Scan scan) {
        String folder = scan.getFolder();
        String brxh = scan.getBrxh();
        String bah = scan.getBah();
        String filename = scan.getFilename();

        if (folder == null || folder.length() < 5 || brxh == null || bah == null || filename == null) {
            logger.warn("Incomplete scan data for path building: id={}", scan.getId());
            return null;
        }

        String parentFolder = folder.substring(0, 5);
        String folderName = brxh + "-" + bah;
        Path path = Paths.get(imageProperties.getBasePath(), parentFolder, folder, folderName, filename);
        return path.toString();
    }

    private String buildOssKey(Scan scan) {
        String folder = scan.getFolder();
        String parentFolder = folder.substring(0, 5);
        String folderName = scan.getBrxh() + "-" + scan.getBah();
        return String.format("medical-records/%s/%s/%s/%s",
                parentFolder, folder, folderName, scan.getFilename());
    }

    private void logMigration(Integer scanId, String localPath, String ossUrl,
                               String status, String errorMessage, Long fileSize, String md5) {
        try {
            ImageMigrationLog log = new ImageMigrationLog();
            log.setScanId(scanId);
            log.setLocalPath(localPath);
            log.setOssUrl(ossUrl);
            log.setMigrationStatus(status);
            log.setErrorMessage(errorMessage);
            log.setFileSize(fileSize);
            log.setChecksumMd5(md5);
            if ("success".equals(status)) {
                log.setMigratedAt(new Date());
            }
            migrationLogMapper.insert(log);
        } catch (Exception e) {
            logger.error("Failed to insert migration log for scanId={}", scanId, e);
        }
    }
}
