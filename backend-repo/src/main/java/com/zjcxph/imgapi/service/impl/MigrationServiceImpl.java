package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.dto.req.MigrationJobRequest;
import com.zjcxph.imgapi.dto.resp.MigrationReadinessDTO;
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
import com.zjcxph.imgapi.utils.MedicalRecordCodeUtils;
import com.zjcxph.imgapi.utils.PaginationUtils;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Service
public class MigrationServiceImpl implements MigrationService {

    private static final Logger logger = LoggerFactory.getLogger(MigrationServiceImpl.class);
    private static final int JOB_BATCH_SIZE = 100;
    private static final int PILOT_DEFAULT_LIMIT = 500;
    private static final int PILOT_MAX_LIMIT = 1_000;
    private static final int BATCH_DEFAULT_LIMIT = 10_000;
    private static final int BATCH_MAX_LIMIT = 100_000;
    private static final int MAX_MANUAL_RETRY_IDS = 1_000;
    private static final int MAX_MANUAL_BAH_FILES = 2_000;
    private static final int MAX_ATTEMPTS = 3;
    private static final long STATISTICS_CACHE_MILLIS = 15_000L;
    private static final long FOLDER_CACHE_MILLIS = 60_000L;
    private static final String FULL_CONFIRMATION = "确认全量迁移";
    private static final String READINESS_PROBE_KEY = "__mrr_migration_readiness_probe__";

    private final OssService ossService;
    private final ScanMapper scanMapper;
    private final ImageMigrationLogMapper migrationLogMapper;
    private final MigrationJobMapper migrationJobMapper;
    private final MigrationSourceResolver sourceResolver;
    private final Executor migrationTaskExecutor;

    private final Object statisticsCacheLock = new Object();
    private final Object folderCacheLock = new Object();
    private volatile MigrationStatisticsDTO cachedStatistics;
    private volatile long statisticsCachedAt;
    private volatile List<Map<String, Object>> cachedPendingFolders;
    private volatile long foldersCachedAt;

    public MigrationServiceImpl(OssService ossService,
                                ScanMapper scanMapper,
                                ImageMigrationLogMapper migrationLogMapper,
                                MigrationJobMapper migrationJobMapper,
                                MigrationSourceResolver sourceResolver,
                                @Qualifier("migrationTaskExecutor") Executor migrationTaskExecutor) {
        this.ossService = ossService;
        this.scanMapper = scanMapper;
        this.migrationLogMapper = migrationLogMapper;
        this.migrationJobMapper = migrationJobMapper;
        this.sourceResolver = sourceResolver;
        this.migrationTaskExecutor = migrationTaskExecutor;
    }

    @PostConstruct
    public void recoverInterruptedMigrationState() {
        try {
            int interruptedJobs = migrationJobMapper.interruptActiveJobs();
            int releasedScans = scanMapper.recoverInterruptedMigrations();
            if (interruptedJobs > 0 || releasedScans > 0) {
                logger.warn("Recovered OSS migration state after restart: jobs={}, scans={}",
                        interruptedJobs, releasedScans);
                invalidateSummaryCaches();
            }
        } catch (Exception exception) {
            logger.warn("Unable to recover interrupted OSS migration state during startup", exception);
        }
    }

    @Override
    public OssUploadResult uploadSingleScan(Integer scanId) {
        assertManualUploadAllowed();
        Scan scan = scanMapper.findById(scanId);
        if (scan == null) {
            return new OssUploadResult(scanId, "failed", "扫描记录不存在: " + scanId);
        }
        OssUploadResult result = uploadLoadedScanInternal(scanId, scan, true);
        invalidateSummaryCaches();
        return result;
    }

    @Override
    public OssUploadResult uploadLoadedScan(Scan scan) {
        if (scan == null || scan.getId() == null) {
            return new OssUploadResult(null, "failed", "扫描记录或 Scan ID 为空");
        }
        OssUploadResult result = uploadLoadedScanInternal(scan.getId(), scan, true);
        invalidateSummaryCaches();
        return result;
    }

    private OssUploadResult uploadLoadedScanForJob(Scan scan) {
        if (scan == null || scan.getId() == null) {
            return new OssUploadResult(null, "failed", "扫描记录或 Scan ID 为空");
        }
        return uploadLoadedScanInternal(scan.getId(), scan, false);
    }

    private OssUploadResult uploadLoadedScanInternal(Integer scanId, Scan scan, boolean includeSignedUrl) {
        if (scan.getOssUrl() != null && !scan.getOssUrl().isBlank()) {
            return skippedResult(scanId, scan.getOssUrl(), "已迁移过");
        }

        if (!OssMigrationRoutePolicy.hasValidSjh(scan)) {
            return waitForSjh(scanId, scan);
        }

        String ossKey = buildOssKey(scan);
        if (ossKey == null) {
            return failPermanently(
                    scanId,
                    sourceResolver.describe(scan),
                    "INVALID_OSS_KEY",
                    "无法构建 OSS Object Key: " + scanId
            );
        }

        int claimed = scanMapper.markMigrationStarted(scanId);
        if (claimed == 0) {
            Scan latest = scanMapper.findById(scanId);
            String existingOssKey = latest == null ? null : latest.getOssUrl();
            return skippedResult(
                    scanId,
                    existingOssKey,
                    existingOssKey == null || existingOssKey.isBlank()
                            ? "记录已被其他处理流程领取，或尚未到下一次重试时间"
                            : "记录已由其他处理流程迁移完成"
            );
        }

        int currentAttempt = (scan.getMigrationAttempts() == null ? 0 : scan.getMigrationAttempts()) + 1;
        String sourceDescription = sourceResolver.describe(scan);

        try (MigrationSourceResolver.ResolvedSource source = sourceResolver.resolve(scan)) {
            sourceDescription = source.description();
            String sourcePath = source.path().toString();
            String md5 = ossService.calculateMd5(sourcePath);
            long fileSize = ossService.getFileSize(sourcePath);

            if (ossService.doesObjectExist(ossKey)) {
                if (!ossService.verifyUploadIntegrity(ossKey, md5)) {
                    return failPermanently(
                            scanId,
                            sourceDescription,
                            "OBJECT_CONFLICT",
                            "OSS 已存在同名对象，但内容校验不一致: " + ossKey
                    );
                }
                logger.info("OSS object already exists and matches source, updating database only: {}", ossKey);
            } else {
                ossService.uploadFile(sourcePath, ossKey, md5);
            }

            scanMapper.updateOssInfo(scanId, ossKey, fileSize, md5, "migrated");
            logMigration(scanId, sourceDescription, ossKey, "success", null, fileSize, md5);

            OssUploadResult result = new OssUploadResult();
            result.setScanId(scanId);
            result.setFileSize(fileSize);
            result.setChecksumMd5(md5);
            result.setStatus("success");
            if (includeSignedUrl) {
                try {
                    result.setOssUrl(ossService.generatePresignedUrl(ossKey));
                } catch (Exception exception) {
                    logger.warn(
                            "Scan {} migrated successfully, but presigned URL generation failed",
                            scanId,
                            exception
                    );
                }
            }
            return result;
        } catch (MigrationSourceResolver.SourceResolutionException exception) {
            logger.error("Failed to resolve source for scan {}", scanId, exception);
            if (exception.isPermanent()) {
                return failPermanently(
                        scanId,
                        sourceDescription,
                        "SOURCE_FILE_UNAVAILABLE",
                        safeErrorMessage(exception)
                );
            }
            return failTemporarilyOrPermanently(
                    scanId,
                    sourceDescription,
                    currentAttempt,
                    exception
            );
        } catch (Exception exception) {
            logger.error("Failed to upload scan {} to OSS", scanId, exception);
            return failTemporarilyOrPermanently(
                    scanId,
                    sourceDescription,
                    currentAttempt,
                    exception
            );
        }
    }

    @Override
    public List<OssUploadResult> uploadByBah(String bah) {
        assertManualUploadAllowed();
        List<Scan> scans = scanMapper.findByBah(bah);
        List<OssUploadResult> results = new ArrayList<>();

        if (scans == null || scans.isEmpty()) {
            results.add(new OssUploadResult(null, "failed", "未找到该病案号的扫描记录: " + bah));
            return results;
        }
        if (scans.size() > MAX_MANUAL_BAH_FILES) {
            throw new IllegalArgumentException("该病案包含图片过多，请使用试迁移或批次迁移任务处理");
        }

        for (Scan scan : scans) {
            if (scan.getUploadFlag() == null || scan.getUploadFlag() == 0) {
                continue;
            }
            results.add(uploadLoadedScanInternal(scan.getId(), scan, true));
        }
        invalidateSummaryCaches();
        return results;
    }

    @Override
    public MigrationStatisticsDTO getStatistics() {
        long now = System.currentTimeMillis();
        MigrationStatisticsDTO cached = cachedStatistics;
        if (cached != null && now - statisticsCachedAt < STATISTICS_CACHE_MILLIS) {
            return cached;
        }

        synchronized (statisticsCacheLock) {
            now = System.currentTimeMillis();
            cached = cachedStatistics;
            if (cached != null && now - statisticsCachedAt < STATISTICS_CACHE_MILLIS) {
                return cached;
            }

            Map<String, Object> counts = scanMapper.countMigrationStats();
            long total = number(counts, "total");
            long migrated = number(counts, "migrated");
            long verified = number(counts, "verified");
            long failed = number(counts, "failed");
            long retryWait = number(counts, "retry_wait");
            long migrating = number(counts, "migrating");
            long waitingSjh = number(counts, "waiting_sjh");
            long pending = number(counts, "pending");
            long migratedTotal = migrated + verified;

            MigrationStatisticsDTO statistics = new MigrationStatisticsDTO();
            statistics.setTotalCount(total);
            statistics.setMigratedCount(migratedTotal);
            statistics.setFailedCount(failed);
            statistics.setRetryWaitCount(retryWait);
            statistics.setMigratingCount(migrating);
            statistics.setWaitingSjhCount(waitingSjh);
            statistics.setPendingCount(pending);
            if (total > 0) {
                statistics.setPercentage(Math.round(migratedTotal * 10000.0 / total) / 100.0);
            }

            cachedStatistics = statistics;
            statisticsCachedAt = now;
            return statistics;
        }
    }

    @Override
    public MigrationReadinessDTO getReadiness(int sampleSize) {
        int safeSampleSize = Math.max(1, Math.min(sampleSize, 200));
        MigrationStatisticsDTO statistics = getStatistics();
        MigrationReadinessDTO readiness = new MigrationReadinessDTO();
        readiness.setPendingCount(statistics.getPendingCount());
        if (statistics.getWaitingSjhCount() > 0) {
            readiness.getWarnings().add("有 " + statistics.getWaitingSjhCount()
                    + " 张图片缺少合法上架号，已保留并等待补齐，不会计入失败或重试");
        }

        MigrationJob activeJob = migrationJobMapper.findLatestActive();
        readiness.setActiveJob(activeJob);
        readiness.setNoActiveJob(activeJob == null);
        if (activeJob != null) {
            readiness.getWarnings().add("已有迁移任务正在运行，请等待完成或安全取消后再创建新任务");
        }

        boolean ossConfigured = canCreatePresignedUrl();
        readiness.setOssConfigured(ossConfigured);
        if (!ossConfigured) {
            readiness.getWarnings().add("OSS 客户端未完成配置，无法创建签名 URL");
        }

        List<Scan> sample = statistics.getPendingCount() > 0
                ? getPendingMigrations(safeSampleSize)
                : List.of();
        readiness.setSampleSize(sample.size());
        for (Scan scan : sample) {
            if (sourceResolver.canRead(scan)) {
                readiness.setSampleReadableCount(readiness.getSampleReadableCount() + 1);
            } else {
                readiness.setSampleMissingCount(readiness.getSampleMissingCount() + 1);
            }
        }

        boolean localConfigured = sourceResolver.isLocalBasePathConfigured();
        boolean sourceReadable = sourceResolver.isLocalBasePathReadable()
                || readiness.getSampleReadableCount() > 0;
        readiness.setSourcePathConfigured(localConfigured || readiness.getSampleReadableCount() > 0);
        readiness.setSourcePathReadable(sourceReadable);

        if (!sourceReadable) {
            readiness.getWarnings().add("当前抽样未找到可读 Nginx 图片；请先核对图片服务器地址、路由和权限");
        }
        if (readiness.getSampleMissingCount() > 0) {
            readiness.getWarnings().add("抽样中存在 Nginx 缺失或不可读文件；404 会标记永久失败，超时和连接异常会等待重试");
        }
        if (statistics.getPendingCount() == 0) {
            readiness.getWarnings().add("当前没有待迁移记录");
        } else if (sample.isEmpty()) {
            readiness.getWarnings().add("当前只有尚未到重试时间的记录，暂时没有可领取的迁移数据");
        }

        readiness.setReady(
                ossConfigured
                        && activeJob == null
                        && statistics.getPendingCount() > 0
                        && !sample.isEmpty()
        );
        if (statistics.getMigratedCount() == 0) {
            readiness.setRecommendedMode("pilot");
            readiness.setRecommendedAction("先迁移 100 至 500 张真实图片，核对访问、下载与日志后再扩大批次");
        } else {
            readiness.setRecommendedMode("batch");
            readiness.setRecommendedAction("继续使用限定数量的批次迁移，稳定后再人工确认全量迁移");
        }
        return readiness;
    }

    @Override
    public List<Scan> getPendingMigrations(int limit) {
        return scanMapper.findPendingMigration(safePendingLimit(limit));
    }

    @Override
    public List<Scan> getPendingMigrations(int limit, String folder) {
        int safeLimit = safePendingLimit(limit);
        if (folder != null && !folder.isBlank()) {
            return scanMapper.findPendingByFolder(folder.trim(), safeLimit);
        }
        return getPendingMigrations(safeLimit);
    }

    @Override
    public List<Map<String, Object>> getPendingFolders() {
        long now = System.currentTimeMillis();
        List<Map<String, Object>> cached = cachedPendingFolders;
        if (cached != null && now - foldersCachedAt < FOLDER_CACHE_MILLIS) {
            return cached;
        }
        synchronized (folderCacheLock) {
            now = System.currentTimeMillis();
            cached = cachedPendingFolders;
            if (cached != null && now - foldersCachedAt < FOLDER_CACHE_MILLIS) {
                return cached;
            }
            List<Map<String, Object>> folders = scanMapper.findPendingFolders();
            cachedPendingFolders = folders == null ? List.of() : List.copyOf(folders);
            foldersCachedAt = now;
            return cachedPendingFolders;
        }
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
        if ("success".equals(log.getMigrationStatus())
                && log.getOssUrl() != null
                && !log.getOssUrl().isBlank()) {
            try {
                log.setOssUrl(ossService.generatePresignedUrl(log.getOssUrl()));
            } catch (Exception exception) {
                logger.warn("Failed to generate presigned URL for log id={}", log.getId(), exception);
            }
        }
    }

    @Override
    public MigrationJob createMigrationJob(MigrationJobRequest request) {
        MigrationJob active = migrationJobMapper.findLatestActive();
        if (active != null) {
            active.setReused(true);
            return active;
        }

        String mode = normalizeMode(request == null ? null : request.getMode());
        String folder = normalizeFolder(request == null ? null : request.getFolder());
        long requestedCount = resolveRequestedCount(
                mode,
                request == null ? null : request.getLimit(),
                request == null ? null : request.getConfirmation()
        );
        assertMigrationCanStart(mode, folder);

        Integer maxScanId = scanMapper.findMaxPendingMigrationId(folder);
        if (maxScanId == null) {
            return null;
        }

        long totalCount;
        long persistedRequestedCount;
        if ("full".equals(mode)) {
            long eligibleCount = scanMapper.countEligibleMigrations(maxScanId, folder);
            if (eligibleCount <= 0) {
                return null;
            }
            totalCount = eligibleCount;
            persistedRequestedCount = eligibleCount;
        } else {
            totalCount = requestedCount;
            persistedRequestedCount = requestedCount;
        }

        MigrationJob job = new MigrationJob();
        job.setStatus("pending");
        job.setMode(mode);
        job.setScopeValue(folder);
        job.setRequestedCount(persistedRequestedCount);
        job.setMaxScanId(maxScanId);
        job.setCancelRequested(false);
        job.setTotalCount(totalCount);
        job.setProcessedCount(0L);
        job.setFailedCount(0L);
        job.setRate(BigDecimal.ZERO);
        job.setCreatedBy(AuthContext.getCurrentUser() != null
                ? AuthContext.getCurrentUser().getUsername()
                : "system");

        try {
            migrationJobMapper.insert(job);
        } catch (DataIntegrityViolationException exception) {
            MigrationJob existing = migrationJobMapper.findLatestActive();
            if (existing != null) {
                existing.setReused(true);
                return existing;
            }
            throw exception;
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
        int offset = (page - 1) * size;
        List<MigrationJob> jobs = migrationJobMapper.findAllPaginated(offset, size);
        int total = migrationJobMapper.countAll();
        return PageResult.of(jobs, total, page, size);
    }

    @Override
    public MigrationJob cancelMigrationJob(Long id) {
        MigrationJob job = migrationJobMapper.findById(id);
        if (job == null) {
            return null;
        }
        migrationJobMapper.requestCancel(id);
        return migrationJobMapper.findById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int retryFailedScans(List<Integer> scanIds) {
        if (hasActiveMigrationJob()) {
            throw new IllegalStateException("迁移任务运行期间不能重置失败记录");
        }
        if (scanIds == null || scanIds.isEmpty()) {
            throw new IllegalArgumentException("scanIds 不能为空");
        }
        Set<Integer> uniqueIds = new LinkedHashSet<>(scanIds);
        uniqueIds.remove(null);
        if (uniqueIds.isEmpty()) {
            throw new IllegalArgumentException("scanIds 不能为空");
        }
        if (uniqueIds.size() > MAX_MANUAL_RETRY_IDS) {
            throw new IllegalArgumentException("单次最多重置 " + MAX_MANUAL_RETRY_IDS + " 条记录");
        }
        int updated = scanMapper.resetMigrationFailures(new ArrayList<>(uniqueIds));
        invalidateSummaryCaches();
        return updated;
    }

    @Override
    public boolean hasActiveMigrationJob() {
        return migrationJobMapper.findLatestActive() != null;
    }

    private void executeMigrationJobAsync(Long jobId) {
        try {
            migrationTaskExecutor.execute(() -> runMigrationJob(jobId));
        } catch (RejectedExecutionException exception) {
            logger.error("Migration executor rejected job {}", jobId, exception);
            migrationJobMapper.complete(
                    jobId,
                    "failed",
                    0,
                    0,
                    0,
                    BigDecimal.ZERO,
                    "迁移执行器暂不可用，任务未启动，请稍后重新创建",
                    new Date()
            );
            invalidateSummaryCaches();
            throw new IllegalStateException("迁移执行器暂不可用，任务未启动，请稍后重试", exception);
        }
    }

    private void runMigrationJob(Long jobId) {
        MigrationJob job = migrationJobMapper.findById(jobId);
        if (job == null) {
            logger.error("Migration job {} was not visible after creation", jobId);
            return;
        }

        long plannedTotal = job.getTotalCount() == null ? 0 : job.getTotalCount();
        long processed = 0;
        long failed = 0;

        try {
            if (Boolean.TRUE.equals(migrationJobMapper.isCancelRequested(jobId))) {
                completeCancelledJob(job, 0, 0);
                return;
            }

            Date startedAt = new Date();
            if (migrationJobMapper.markRunning(jobId, startedAt) == 0) {
                if (Boolean.TRUE.equals(migrationJobMapper.isCancelRequested(jobId))) {
                    completeCancelledJob(job, 0, 0);
                } else {
                    logger.warn("Migration job {} could not transition from pending to running", jobId);
                }
                return;
            }

            int afterId = 0;
            boolean cancelled = false;
            boolean exhausted = false;

            while (processed < plannedTotal) {
                if (Boolean.TRUE.equals(migrationJobMapper.isCancelRequested(jobId))) {
                    cancelled = true;
                    break;
                }

                int fetchSize = (int) Math.min(JOB_BATCH_SIZE, plannedTotal - processed);
                List<Scan> batch = scanMapper.findPendingMigrationAfterId(
                        afterId,
                        job.getMaxScanId(),
                        job.getScopeValue(),
                        fetchSize
                );
                if (batch.isEmpty()) {
                    exhausted = true;
                    break;
                }

                for (Scan scan : batch) {
                    afterId = Math.max(afterId, scan.getId());
                    if (Boolean.TRUE.equals(migrationJobMapper.isCancelRequested(jobId))) {
                        cancelled = true;
                        break;
                    }

                    OssUploadResult result;
                    try {
                        result = uploadLoadedScanForJob(scan);
                    } catch (Exception exception) {
                        logger.error("Migration failed for scan {}", scan.getId(), exception);
                        result = new OssUploadResult(
                                scan.getId(),
                                "failed",
                                safeErrorMessage(exception)
                        );
                    }
                    processed++;
                    if (!("success".equals(result.getStatus())
                            || "skipped".equals(result.getStatus())
                            || "waiting_sjh".equals(result.getStatus()))) {
                        failed++;
                    }
                }

                migrationJobMapper.updateProgress(
                        jobId,
                        processed,
                        failed,
                        calculateRate(processed, plannedTotal)
                );
                if (cancelled) {
                    break;
                }
            }

            if (!cancelled && Boolean.TRUE.equals(migrationJobMapper.isCancelRequested(jobId))) {
                cancelled = true;
            }

            long finalTotal = exhausted && processed > 0 && processed < plannedTotal
                    ? processed
                    : plannedTotal;
            String status;
            String errorMessage = null;
            if (cancelled) {
                status = "cancelled";
                errorMessage = "管理员已请求安全取消，未处理记录仍保持待迁移状态";
            } else if (processed == 0 && plannedTotal > 0) {
                status = "failed";
                errorMessage = "未领取到可处理记录，请刷新迁移前检查后重试";
            } else {
                status = failed > 0 ? "completed_with_errors" : "completed";
            }

            migrationJobMapper.complete(
                    jobId,
                    status,
                    finalTotal,
                    processed,
                    failed,
                    calculateRate(processed, finalTotal),
                    errorMessage,
                    new Date()
            );
            invalidateSummaryCaches();
            logger.info(
                    "Migration job {} finished: status={}, processed={}, failed={}",
                    jobId,
                    status,
                    processed,
                    failed
            );
        } catch (Exception exception) {
            logger.error("Migration job {} error", jobId, exception);
            boolean cancelled = Boolean.TRUE.equals(migrationJobMapper.isCancelRequested(jobId));
            migrationJobMapper.complete(
                    jobId,
                    cancelled ? "cancelled" : "failed",
                    plannedTotal,
                    processed,
                    failed,
                    calculateRate(processed, plannedTotal),
                    cancelled
                            ? "管理员已请求安全取消，任务异常退出后已停止"
                            : safeErrorMessage(exception),
                    new Date()
            );
            invalidateSummaryCaches();
        }
    }

    private void completeCancelledJob(MigrationJob job, long processed, long failed) {
        long total = job.getTotalCount() == null ? 0 : job.getTotalCount();
        migrationJobMapper.complete(
                job.getId(),
                "cancelled",
                total,
                processed,
                failed,
                calculateRate(processed, total),
                "管理员已请求安全取消，任务尚未开始或已停止领取新记录",
                new Date()
        );
        invalidateSummaryCaches();
    }

    private BigDecimal calculateRate(long processed, long total) {
        if (total <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(processed * 100.0 / total)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private OssUploadResult waitForSjh(Integer scanId, Scan scan) {
        String message = "上架号为空、少于 4 位或包含非数字字符，已保留原始图片并等待补齐";
        scanMapper.markMigrationWaitingSjh(scanId);
        logMigration(scanId, sourceResolver.describe(scan), null, "waiting_sjh", message, null, null);
        return new OssUploadResult(scanId, "waiting_sjh", message);
    }

    private OssUploadResult skippedResult(Integer scanId, String ossUrl, String message) {
        OssUploadResult result = new OssUploadResult();
        result.setScanId(scanId);
        result.setOssUrl(ossUrl);
        result.setStatus("skipped");
        result.setErrorMessage(message);
        return result;
    }

    private OssUploadResult failPermanently(Integer scanId,
                                             String sourceDescription,
                                             String errorCode,
                                             String message) {
        scanMapper.markMigrationFailed(scanId, errorCode);
        logMigration(scanId, sourceDescription, null, "failed", message, null, null);
        return new OssUploadResult(scanId, "failed", message);
    }

    private OssUploadResult failTemporarilyOrPermanently(Integer scanId,
                                                          String sourceDescription,
                                                          int currentAttempt,
                                                          Exception error) {
        String message = safeErrorMessage(error);
        String errorCode = classifyUploadError(error);
        if (currentAttempt >= MAX_ATTEMPTS || "OBJECT_CONFLICT".equals(errorCode)) {
            scanMapper.markMigrationFailed(scanId, errorCode);
            logMigration(scanId, sourceDescription, null, "failed", message, null, null);
            return new OssUploadResult(scanId, "failed", message);
        }

        long delaySeconds = Math.min(1800L, 60L * (1L << Math.max(0, currentAttempt - 1)));
        Date nextRetryAt = Date.from(Instant.now().plusSeconds(delaySeconds));
        scanMapper.markMigrationRetryWait(scanId, errorCode, nextRetryAt);
        logMigration(scanId, sourceDescription, null, "retry_wait", message, null, null);
        return new OssUploadResult(scanId, "retry_wait", message);
    }

    private String classifyUploadError(Exception error) {
        String message = safeErrorMessage(error).toLowerCase(Locale.ROOT);
        if (message.contains("同名对象") || message.contains("校验不一致")) {
            return "OBJECT_CONFLICT";
        }
        if (message.contains("timeout") || message.contains("timed out") || message.contains("超时")) {
            return "SOURCE_OR_OSS_TIMEOUT";
        }
        if (message.contains("connection") || message.contains("connect") || message.contains("连接")) {
            return "SOURCE_OR_OSS_CONNECTION";
        }
        if (message.contains("403")
                || message.contains("access denied")
                || message.contains("forbidden")
                || message.contains("无权限")) {
            return "SOURCE_OR_OSS_PERMISSION";
        }
        return "SOURCE_OR_OSS_IO_FAILED";
    }

    private void assertMigrationCanStart(String mode, String folder) {
        if (!canCreatePresignedUrl()) {
            throw new IllegalStateException("OSS 尚未完成配置，请先检查 endpoint、bucket 与访问凭据");
        }

        List<Scan> sample = getPendingMigrations(20, folder);
        if (sample.isEmpty()) {
            throw new IllegalStateException("当前范围没有到期且可领取的待迁移记录");
        }

        boolean anyReadable = sourceResolver.isLocalBasePathReadable()
                || sample.stream().anyMatch(sourceResolver::canRead);
        if (!anyReadable && !"pilot".equals(mode)) {
            throw new IllegalStateException("当前范围抽样未找到可读图片，请先限定真实目录执行试迁移");
        }
    }

    private void assertManualUploadAllowed() {
        if (hasActiveMigrationJob()) {
            throw new IllegalStateException("迁移任务运行期间不能执行手工上传，请先等待或安全取消任务");
        }
    }

    private boolean canCreatePresignedUrl() {
        try {
            String probe = ossService.generatePresignedUrl(READINESS_PROBE_KEY);
            return probe != null && !probe.isBlank();
        } catch (Exception exception) {
            logger.debug("OSS readiness probe failed: {}", exception.getMessage());
            return false;
        }
    }

    private long resolveRequestedCount(String mode, Integer requestedLimit, String confirmation) {
        return switch (mode) {
            case "pilot" -> normalizeLimit(requestedLimit, PILOT_DEFAULT_LIMIT, PILOT_MAX_LIMIT);
            case "batch" -> normalizeLimit(requestedLimit, BATCH_DEFAULT_LIMIT, BATCH_MAX_LIMIT);
            case "full" -> {
                if (!FULL_CONFIRMATION.equals(confirmation)) {
                    throw new IllegalArgumentException("全量迁移必须输入确认短语：" + FULL_CONFIRMATION);
                }
                yield Long.MAX_VALUE;
            }
            default -> throw new IllegalArgumentException("不支持的迁移模式: " + mode);
        };
    }

    private long normalizeLimit(Integer requestedLimit, int defaultLimit, int maxLimit) {
        int value = requestedLimit == null ? defaultLimit : requestedLimit;
        if (value <= 0 || value > maxLimit) {
            throw new IllegalArgumentException("迁移数量必须在 1 到 " + maxLimit + " 之间");
        }
        return value;
    }

    private String normalizeMode(String mode) {
        String normalized = trimToNull(mode);
        return normalized == null ? "pilot" : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeFolder(String folder) {
        return trimToNull(folder);
    }

    private int safePendingLimit(int limit) {
        return Math.max(1, Math.min(limit, 1_000));
    }

    private long number(Map<String, Object> values, String key) {
        Object value = values == null ? null : values.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private String buildOssKey(Scan scan) {
        return OssMigrationRoutePolicy.buildObjectKey(scan);
    }

    private String safeSegment(String value) {
        String normalized = trimToNull(value);
        if (normalized == null
                || normalized.equals(".")
                || normalized.equals("..")
                || normalized.contains("/")
                || normalized.contains("\\")
                || normalized.indexOf('\0') >= 0
                || normalized.contains(":")) {
            return null;
        }
        return normalized;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String safeErrorMessage(Throwable error) {
        if (error == null || error.getMessage() == null || error.getMessage().isBlank()) {
            return error == null ? "未知错误" : error.getClass().getSimpleName();
        }
        String message = error.getMessage().trim();
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private void invalidateSummaryCaches() {
        cachedStatistics = null;
        statisticsCachedAt = 0;
        cachedPendingFolders = null;
        foldersCachedAt = 0;
    }

    private void logMigration(Integer scanId,
                              String sourceDescription,
                              String ossUrl,
                              String status,
                              String errorMessage,
                              Long fileSize,
                              String md5) {
        try {
            ImageMigrationLog log = new ImageMigrationLog();
            log.setScanId(scanId);
            log.setLocalPath(sourceDescription == null ? "" : sourceDescription);
            log.setOssUrl(ossUrl);
            log.setMigrationStatus(status);
            log.setErrorMessage(errorMessage);
            log.setFileSize(fileSize);
            log.setChecksumMd5(md5);
            if ("success".equals(status)) {
                log.setMigratedAt(new Date());
            }
            migrationLogMapper.insert(log);
        } catch (Exception exception) {
            logger.error("Failed to insert migration log for scanId={}", scanId, exception);
        }
    }
}
