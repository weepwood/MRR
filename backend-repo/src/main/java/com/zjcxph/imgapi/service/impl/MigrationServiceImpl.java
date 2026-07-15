package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.config.ImageProperties;
import com.zjcxph.imgapi.config.MigrationProperties;
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
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class MigrationServiceImpl implements MigrationService {

    private static final Logger logger = LoggerFactory.getLogger(MigrationServiceImpl.class);

    private final OssService ossService;
    private final ScanMapper scanMapper;
    private final ImageMigrationLogMapper migrationLogMapper;
    private final MigrationJobMapper migrationJobMapper;
    private final ImageProperties imageProperties;
    private final MigrationProperties migrationProperties;
    private final Executor taskAsyncExecutor;
    private final Executor migrationExecutor;
    private final TransactionTemplate transactionTemplate;

    public MigrationServiceImpl(OssService ossService,
                                ScanMapper scanMapper,
                                ImageMigrationLogMapper migrationLogMapper,
                                MigrationJobMapper migrationJobMapper,
                                ImageProperties imageProperties,
                                MigrationProperties migrationProperties,
                                @Qualifier("taskAsyncExecutor") Executor taskAsyncExecutor,
                                @Qualifier("migrationExecutor") Executor migrationExecutor,
                                PlatformTransactionManager transactionManager) {
        this.ossService = ossService;
        this.scanMapper = scanMapper;
        this.migrationLogMapper = migrationLogMapper;
        this.migrationJobMapper = migrationJobMapper;
        this.imageProperties = imageProperties;
        this.migrationProperties = migrationProperties;
        this.taskAsyncExecutor = taskAsyncExecutor;
        this.migrationExecutor = migrationExecutor;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * The production deployment currently runs one backend process. If that
     * process stops, in-memory workers disappear, so active jobs and leases
     * from the old process must be released before a new job can be started.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedMigrationState() {
        int interruptedJobs = migrationJobMapper.markActiveJobsInterrupted(
                "后端服务重启，原迁移任务已中断；可创建新任务继续迁移");
        int releasedRows = scanMapper.releaseInterruptedMigrationLeases();
        if (interruptedJobs > 0 || releasedRows > 0) {
            logger.warn("Recovered interrupted OSS migration state: jobs={}, leases={}",
                    interruptedJobs, releasedRows);
        }
    }

    @Override
    public OssUploadResult uploadSingleScan(Integer scanId) {
        ensureNoActiveBulkJob();
        Scan scan = scanMapper.findById(scanId);
        if (scan == null) {
            return new OssUploadResult(scanId, "failed", "扫描记录不存在: " + scanId);
        }
        return migrateManualScan(scan);
    }

    @Override
    public OssUploadResult uploadLoadedScan(Scan scan) {
        return migrateManualScan(scan);
    }

    private OssUploadResult migrateManualScan(Scan scan) {
        UploadExecution execution = performUpload(scan, true);
        if (isSuccessful(execution.result())) {
            if ("success".equals(execution.result().getStatus())) {
                persistSuccess(scan, execution);
            }
        } else {
            safeLogMigration(scan.getId(), execution.localPath(), execution.objectKey(),
                    "failed", execution.result().getErrorMessage(), execution.fileSize(), execution.checksumMd5());
        }
        return execution.result();
    }

    /**
     * Performs file IO and OSS network operations without an open database
     * transaction. Only the small persistence step is transactional.
     */
    private UploadExecution performUpload(Scan scan, boolean includeSignedUrl) {
        Integer scanId = scan.getId();
        if (scan.getOssUrl() != null && !scan.getOssUrl().isBlank()) {
            OssUploadResult result = new OssUploadResult();
            result.setScanId(scanId);
            result.setOssUrl(resolveResultUrl(scan.getOssUrl(), includeSignedUrl));
            result.setStatus("skipped");
            result.setErrorMessage("已迁移过");
            return new UploadExecution(result, null, scan.getOssUrl(), scan.getFileSize(),
                    scan.getChecksumMd5(), false);
        }

        String localPath;
        try {
            localPath = buildLocalPath(scan);
        } catch (Exception e) {
            return failedExecution(scanId, null, null, null, null,
                    "无法构建安全的本地路径: " + e.getMessage(), true);
        }
        if (localPath == null) {
            return failedExecution(scanId, null, null, null, null,
                    "无法构建本地路径: " + scanId, true);
        }

        File localFile = new File(localPath);
        if (!localFile.exists() || !localFile.isFile()) {
            return failedExecution(scanId, localPath, null, null, null,
                    "本地文件不存在: " + localPath, true);
        }
        if (!localFile.canRead()) {
            return failedExecution(scanId, localPath, null, localFile.length(), null,
                    "本地文件不可读: " + localPath, true);
        }

        String objectKey;
        try {
            objectKey = buildOssKey(scan);
        } catch (Exception e) {
            return failedExecution(scanId, localPath, null, localFile.length(), null,
                    "无法构建 OSS Key: " + e.getMessage(), true);
        }

        try {
            long fileSize = localFile.length();
            String md5 = ossService.calculateMd5(localPath);

            // Do not trust a plain HEAD/existence result. An existing object is
            // accepted only when both size and checksum match the source file.
            if (!ossService.isObjectEquivalent(objectKey, fileSize, md5)) {
                ossService.uploadFile(localPath, objectKey, md5);
            }

            OssUploadResult result = new OssUploadResult();
            result.setScanId(scanId);
            result.setOssUrl(resolveResultUrl(objectKey, includeSignedUrl));
            result.setFileSize(fileSize);
            result.setChecksumMd5(md5);
            result.setStatus("success");
            return new UploadExecution(result, localPath, objectKey, fileSize, md5, false);
        } catch (Exception e) {
            logger.error("Failed to upload scan {} to OSS", scanId, e);
            return failedExecution(scanId, localPath, objectKey, localFile.length(), null,
                    rootMessage(e), isPermanentException(e));
        }
    }

    @Override
    public List<OssUploadResult> uploadByBah(String bah) {
        ensureNoActiveBulkJob();
        List<Scan> scans = scanMapper.findByBah(bah);
        List<OssUploadResult> results = new ArrayList<>();

        if (scans == null || scans.isEmpty()) {
            results.add(new OssUploadResult(null, "failed", "未找到该病案号的扫描记录: " + bah));
            return results;
        }

        logger.info("Starting manual batch upload for BAH={}, records={}", bah, scans.size());
        for (Scan scan : scans) {
            if (scan.getUploadFlag() == null || scan.getUploadFlag() == 0) {
                continue;
            }
            results.add(migrateManualScan(scan));
        }

        long successCount = results.stream().filter(this::isSuccessful).count();
        logger.info("Manual batch upload completed for BAH={}: {}/{} succeeded",
                bah, successCount, results.size());
        return results;
    }

    @Override
    public MigrationStatisticsDTO getStatistics() {
        MigrationStatisticsDTO stats = new MigrationStatisticsDTO();
        Map<String, Object> counts = scanMapper.countMigrationStats();
        long total = numberValue(counts, "total");
        long migrated = numberValue(counts, "migrated");
        long verified = numberValue(counts, "verified");
        long failed = numberValue(counts, "failed");
        long completed = migrated + verified;

        stats.setTotalCount(total);
        stats.setMigratedCount(completed);
        stats.setPendingCount(Math.max(0, total - completed - failed));
        stats.setFailedCount(failed);
        if (total > 0) {
            stats.setPercentage(Math.round(completed * 10000.0 / total) / 100.0);
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
            return scanMapper.findPendingByFolder(folder, limit);
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
        if (scan == null || scan.getOssUrl() == null || scan.getOssUrl().isBlank()) {
            return null;
        }
        return ossService.generatePresignedUrl(scan.getOssUrl());
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
        if (("success".equals(log.getMigrationStatus()) || "verified".equals(log.getMigrationStatus()))
                && log.getOssUrl() != null && !log.getOssUrl().isBlank()) {
            try {
                log.setOssUrl(ossService.generatePresignedUrl(log.getOssUrl()));
            } catch (Exception e) {
                logger.warn("Failed to generate presigned URL for migration log id={}", log.getId(), e);
            }
        }
    }

    @Override
    public MigrationJob createMigrationJob() {
        MigrationJob active = migrationJobMapper.findLatestActive();
        if (active != null) {
            throw new IllegalStateException("已有迁移任务正在执行，任务 ID=" + active.getId());
        }

        int maxAttempts = Math.max(1, migrationProperties.getMaxAttempts());
        Long maxScanId = scanMapper.findMaxPendingMigrationId(maxAttempts);
        if (maxScanId == null) {
            return null;
        }
        long pendingCount = scanMapper.countPendingMigrationsUpTo(maxScanId, maxAttempts);
        if (pendingCount == 0) {
            return null;
        }

        MigrationJob job = new MigrationJob();
        job.setStatus("pending");
        job.setTotalCount(pendingCount);
        job.setProcessedCount(0L);
        job.setFailedCount(0L);
        job.setRate(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        job.setMaxScanId(maxScanId);
        job.setCreatedBy(AuthContext.getCurrentUser() != null
                ? AuthContext.getCurrentUser().getUsername()
                : "system");

        try {
            migrationJobMapper.insert(job);
        } catch (DuplicateKeyException e) {
            MigrationJob concurrent = migrationJobMapper.findLatestActive();
            throw new IllegalStateException("已有迁移任务正在执行"
                    + (concurrent == null ? "" : "，任务 ID=" + concurrent.getId()), e);
        }

        executeMigrationJobAsync(job.getId());
        return job;
    }

    @Override
    public MigrationJob getMigrationJob(Long id) {
        return migrationJobMapper.findById(id);
    }

    @Override
    public PageResult<MigrationJob> listMigrationJobs(int page, int size) {
        PaginationUtils.validatePageParams(page, size);
        int offset = PaginationUtils.calculateOffset(page, size);
        List<MigrationJob> jobs = migrationJobMapper.findAllPaginated(offset, size);
        int total = migrationJobMapper.countAll();
        return PageResult.of(jobs, total, page, size);
    }

    private void executeMigrationJobAsync(Long jobId) {
        taskAsyncExecutor.execute(() -> runMigrationJob(jobId));
    }

    private void runMigrationJob(Long jobId) {
        MigrationJob job = migrationJobMapper.findById(jobId);
        if (job == null) {
            return;
        }

        String workerId = buildWorkerId(jobId);
        long succeeded = 0;
        long permanentlyFailed = 0;
        long lastProgressFlush = System.nanoTime();

        try {
            job.setStatus("running");
            job.setStartedAt(new Date());
            migrationJobMapper.update(job);

            int claimBatchSize = Math.max(1, migrationProperties.getClaimBatchSize());
            int maxAttempts = Math.max(1, migrationProperties.getMaxAttempts());
            int leaseSeconds = Math.max(30, migrationProperties.getLeaseSeconds());

            while (!Thread.currentThread().isInterrupted()) {
                List<Scan> batch = scanMapper.claimPendingMigrations(
                        jobId,
                        job.getMaxScanId(),
                        workerId,
                        claimBatchSize,
                        leaseSeconds,
                        maxAttempts
                );

                if (batch.isEmpty()) {
                    long remaining = scanMapper.countPendingMigrationsUpTo(
                            job.getMaxScanId(), maxAttempts);
                    if (remaining == 0) {
                        break;
                    }
                    sleep(Duration.ofSeconds(Math.max(1, migrationProperties.getIdlePollSeconds())));
                    continue;
                }

                List<CompletableFuture<JobItemOutcome>> futures = new ArrayList<>(batch.size());
                for (Scan scan : batch) {
                    futures.add(CompletableFuture.supplyAsync(
                            () -> processClaimedScan(job, scan, workerId), migrationExecutor));
                }

                for (CompletableFuture<JobItemOutcome> future : futures) {
                    JobItemOutcome outcome = future.join();
                    if (outcome == JobItemOutcome.SUCCESS) {
                        succeeded++;
                    } else if (outcome == JobItemOutcome.PERMANENT_FAILURE) {
                        permanentlyFailed++;
                    }
                }

                long now = System.nanoTime();
                if (Duration.ofNanos(now - lastProgressFlush).getSeconds()
                        >= Math.max(1, migrationProperties.getProgressFlushSeconds())) {
                    flushJobProgress(job, succeeded, permanentlyFailed, false);
                    lastProgressFlush = now;
                }
            }

            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Migration coordinator interrupted");
            }

            flushJobProgress(job, succeeded, permanentlyFailed, true);
            job.setStatus(permanentlyFailed > 0 ? "completed_with_errors" : "completed");
            job.setCompletedAt(new Date());
            job.setErrorMessage(permanentlyFailed > 0
                    ? "存在 " + permanentlyFailed + " 个永久失败文件"
                    : null);
            migrationJobMapper.update(job);
            logger.info("Migration job {} completed: success={}, permanentFailure={}, total={}",
                    jobId, succeeded, permanentlyFailed, job.getTotalCount());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failJob(job, "迁移任务被中断");
        } catch (Exception e) {
            logger.error("Migration job {} failed", jobId, e);
            failJob(job, rootMessage(e));
        }
    }

    private JobItemOutcome processClaimedScan(MigrationJob job, Scan scan, String workerId) {
        int leaseSeconds = Math.max(30, migrationProperties.getLeaseSeconds());
        int marked = scanMapper.markMigrationUploading(
                scan.getId(), job.getId(), workerId, leaseSeconds);
        if (marked == 0) {
            logger.warn("Lost migration lease before upload: jobId={}, scanId={}",
                    job.getId(), scan.getId());
            return JobItemOutcome.RETRY_SCHEDULED;
        }

        UploadExecution execution = performUpload(scan, false);
        if (isSuccessful(execution.result())) {
            try {
                if ("success".equals(execution.result().getStatus())) {
                    persistSuccess(scan, execution);
                }
                return JobItemOutcome.SUCCESS;
            } catch (Exception e) {
                execution = failedExecution(scan.getId(), execution.localPath(), execution.objectKey(),
                        execution.fileSize(), execution.checksumMd5(), rootMessage(e), false);
            }
        }

        int attempts = scan.getMigrationAttempts() == null ? 1 : scan.getMigrationAttempts();
        boolean permanent = execution.permanentFailure()
                || attempts >= Math.max(1, migrationProperties.getMaxAttempts());
        String errorMessage = execution.result().getErrorMessage();

        if (permanent) {
            persistJobFailure(job.getId(), scan, execution, true, 0, errorMessage);
            return JobItemOutcome.PERMANENT_FAILURE;
        }

        int retryDelaySeconds = calculateRetryDelaySeconds(attempts);
        persistJobFailure(job.getId(), scan, execution, false, retryDelaySeconds, errorMessage);
        return JobItemOutcome.RETRY_SCHEDULED;
    }

    private void persistSuccess(Scan scan, UploadExecution execution) {
        transactionTemplate.executeWithoutResult(status -> {
            int updated = scanMapper.updateOssInfo(
                    scan.getId(), execution.objectKey(), execution.fileSize(),
                    execution.checksumMd5(), "verified");
            if (updated != 1) {
                throw new IllegalStateException("更新迁移结果失败，scanId=" + scan.getId());
            }
            insertMigrationLog(scan.getId(), execution.localPath(), execution.objectKey(),
                    "success", null, execution.fileSize(), execution.checksumMd5());
        });
    }

    private void persistJobFailure(long jobId,
                                   Scan scan,
                                   UploadExecution execution,
                                   boolean permanent,
                                   int retryDelaySeconds,
                                   String errorMessage) {
        transactionTemplate.executeWithoutResult(status -> {
            if (permanent) {
                scanMapper.markMigrationPermanentFailure(scan.getId(), jobId, errorMessage);
                insertMigrationLog(scan.getId(), execution.localPath(), execution.objectKey(),
                        "failed_permanent", errorMessage, execution.fileSize(), execution.checksumMd5());
            } else {
                scanMapper.markMigrationRetry(scan.getId(), jobId, retryDelaySeconds, errorMessage);
                insertMigrationLog(scan.getId(), execution.localPath(), execution.objectKey(),
                        "failed", errorMessage, execution.fileSize(), execution.checksumMd5());
            }
        });
    }

    private void flushJobProgress(MigrationJob job,
                                  long succeeded,
                                  long permanentlyFailed,
                                  boolean finalFlush) {
        long processed = succeeded + permanentlyFailed;
        job.setProcessedCount(processed);
        job.setFailedCount(permanentlyFailed);
        if (job.getTotalCount() == null || job.getTotalCount() <= 0) {
            job.setRate(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        } else {
            double percentage = Math.min(100.0, processed * 100.0 / job.getTotalCount());
            job.setRate(BigDecimal.valueOf(percentage).setScale(2, RoundingMode.HALF_UP));
        }
        if (finalFlush && processed >= job.getTotalCount()) {
            job.setRate(BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP));
        }
        migrationJobMapper.update(job);
    }

    private void failJob(MigrationJob job, String message) {
        if (job == null) {
            return;
        }
        job.setStatus("failed");
        job.setErrorMessage(message);
        job.setCompletedAt(new Date());
        migrationJobMapper.update(job);
    }

    private int calculateRetryDelaySeconds(int attempt) {
        long base = Math.max(1, migrationProperties.getRetryBaseSeconds());
        long max = Math.max(base, migrationProperties.getMaxRetryDelaySeconds());
        int exponent = Math.max(0, Math.min(20, attempt - 1));
        long delay = base * (1L << exponent);
        return (int) Math.min(Integer.MAX_VALUE, Math.min(delay, max));
    }

    private void ensureNoActiveBulkJob() {
        MigrationJob active = migrationJobMapper.findLatestActive();
        if (active != null) {
            throw new IllegalStateException("全量迁移任务运行期间不允许手工上传，任务 ID=" + active.getId());
        }
    }

    private String resolveResultUrl(String objectKey, boolean includeSignedUrl) {
        if (!includeSignedUrl) {
            return objectKey;
        }
        try {
            return ossService.generatePresignedUrl(objectKey);
        } catch (Exception e) {
            logger.warn("Object uploaded but signed URL generation failed for key={}", objectKey, e);
            return objectKey;
        }
    }

    private boolean isSuccessful(OssUploadResult result) {
        return result != null
                && ("success".equals(result.getStatus()) || "skipped".equals(result.getStatus()));
    }

    private UploadExecution failedExecution(Integer scanId,
                                            String localPath,
                                            String objectKey,
                                            Long fileSize,
                                            String checksumMd5,
                                            String message,
                                            boolean permanent) {
        return new UploadExecution(
                new OssUploadResult(scanId, "failed", message),
                localPath,
                objectKey,
                fileSize,
                checksumMd5,
                permanent
        );
    }

    private void safeLogMigration(Integer scanId,
                                  String localPath,
                                  String ossUrl,
                                  String status,
                                  String errorMessage,
                                  Long fileSize,
                                  String md5) {
        try {
            insertMigrationLog(scanId, localPath == null ? "unknown" : localPath,
                    ossUrl, status, errorMessage, fileSize, md5);
        } catch (Exception e) {
            logger.error("Failed to insert migration log for scanId={}", scanId, e);
        }
    }

    private void insertMigrationLog(Integer scanId,
                                    String localPath,
                                    String ossUrl,
                                    String status,
                                    String errorMessage,
                                    Long fileSize,
                                    String md5) {
        ImageMigrationLog log = new ImageMigrationLog();
        log.setScanId(scanId);
        log.setLocalPath(localPath == null ? "unknown" : localPath);
        log.setOssUrl(ossUrl);
        log.setMigrationStatus(status);
        log.setErrorMessage(errorMessage);
        log.setFileSize(fileSize);
        log.setChecksumMd5(md5);
        if ("success".equals(status) || "verified".equals(status)) {
            log.setMigratedAt(new Date());
        }
        migrationLogMapper.insert(log);
    }

    private String buildLocalPath(Scan scan) {
        String folder = scan.getFolder();
        String brxh = scan.getBrxh();
        String bah = scan.getBah();
        String filename = scan.getFilename();
        if (folder == null || folder.length() < 5 || brxh == null || bah == null || filename == null) {
            return null;
        }

        String parentFolder = folder.substring(0, 5);
        String folderName = brxh + "-" + bah;
        Path basePath = Paths.get(imageProperties.getBasePath()).toAbsolutePath().normalize();
        Path resolved = basePath.resolve(parentFolder)
                .resolve(folder)
                .resolve(folderName)
                .resolve(filename)
                .normalize();
        if (!resolved.startsWith(basePath)) {
            throw new IllegalArgumentException("路径超出图片根目录");
        }
        return resolved.toString();
    }

    private String buildOssKey(Scan scan) {
        String folder = scan.getFolder();
        if (folder == null || folder.length() < 5) {
            throw new IllegalArgumentException("folder 无效");
        }
        String parentFolder = folder.substring(0, 5);
        String folderName = scan.getBrxh() + "-" + scan.getBah();
        String key = String.format("medical-records/%s/%s/%s/%s",
                parentFolder, folder, folderName, scan.getFilename()).replace('\\', '/');
        if (key.startsWith("/") || key.contains("../") || key.contains("/..")) {
            throw new IllegalArgumentException("OSS Key 包含非法路径片段");
        }
        return key;
    }

    private boolean isPermanentException(Exception exception) {
        Throwable root = exception;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root instanceof IllegalArgumentException
                || root instanceof SecurityException;
    }

    private String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
    }

    private long numberValue(Map<String, Object> values, String key) {
        Object value = values == null ? null : values.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private String buildWorkerId(long jobId) {
        String host = "unknown";
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            // Host name is only diagnostic metadata.
        }
        return host + ":" + jobId + ":" + UUID.randomUUID();
    }

    private void sleep(Duration duration) throws InterruptedException {
        Thread.sleep(duration.toMillis());
    }

    private enum JobItemOutcome {
        SUCCESS,
        RETRY_SCHEDULED,
        PERMANENT_FAILURE
    }

    private record UploadExecution(OssUploadResult result,
                                   String localPath,
                                   String objectKey,
                                   Long fileSize,
                                   String checksumMd5,
                                   boolean permanentFailure) {
    }
}
