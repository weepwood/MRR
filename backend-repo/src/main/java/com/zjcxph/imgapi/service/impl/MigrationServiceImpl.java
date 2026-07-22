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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
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
    private static final String FULL_CONFIRMATION = "确认全量迁移";
    private static final String READINESS_PROBE_KEY = "__mrr_migration_readiness_probe__";

    private final OssService ossService;
    private final ScanMapper scanMapper;
    private final ImageMigrationLogMapper migrationLogMapper;
    private final MigrationJobMapper migrationJobMapper;
    private final MigrationSourceResolver sourceResolver;
    private final Executor taskAsyncExecutor;

    /**
     * 批量迁移通过自身代理调用 uploadLoadedScan，保留每条扫描的事务边界。
     */
    @Lazy
    @Autowired
    private MigrationService self;

    public MigrationServiceImpl(OssService ossService,
                                ScanMapper scanMapper,
                                ImageMigrationLogMapper migrationLogMapper,
                                MigrationJobMapper migrationJobMapper,
                                MigrationSourceResolver sourceResolver,
                                @Qualifier("taskAsyncExecutor") Executor taskAsyncExecutor) {
        this.ossService = ossService;
        this.scanMapper = scanMapper;
        this.migrationLogMapper = migrationLogMapper;
        this.migrationJobMapper = migrationJobMapper;
        this.sourceResolver = sourceResolver;
        this.taskAsyncExecutor = taskAsyncExecutor;
    }

    @PostConstruct
    public void recoverInterruptedMigrationState() {
        try {
            int interruptedJobs = migrationJobMapper.interruptActiveJobs();
            int releasedScans = scanMapper.recoverInterruptedMigrations();
            if (interruptedJobs > 0 || releasedScans > 0) {
                logger.warn("Recovered OSS migration state after restart: jobs={}, scans={}",
                        interruptedJobs, releasedScans);
            }
        } catch (Exception exception) {
            logger.warn("Unable to recover interrupted OSS migration state during startup", exception);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OssUploadResult uploadSingleScan(Integer scanId) {
        assertManualUploadAllowed();
        Scan scan = scanMapper.findById(scanId);
        if (scan == null) {
            return new OssUploadResult(scanId, "failed", "扫描记录不存在: " + scanId);
        }
        return uploadLoadedScan(scanId, scan);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OssUploadResult uploadLoadedScan(Scan scan) {
        if (scan == null || scan.getId() == null) {
            return new OssUploadResult(null, "failed", "扫描记录或 Scan ID 为空");
        }
        return uploadLoadedScan(scan.getId(), scan);
    }

    private OssUploadResult uploadLoadedScan(Integer scanId, Scan scan) {
        if (scan.getOssUrl() != null && !scan.getOssUrl().isBlank()) {
            OssUploadResult result = new OssUploadResult();
            result.setScanId(scanId);
            result.setOssUrl(scan.getOssUrl());
            result.setStatus("skipped");
            result.setErrorMessage("已迁移过");
            return result;
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

        scanMapper.markMigrationStarted(scanId);
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
                ossService.uploadFile(sourcePath, ossKey);
            }

            scanMapper.updateOssInfo(scanId, ossKey, fileSize, md5, "migrated");
            logMigration(scanId, sourceDescription, ossKey, "success", null, fileSize, md5);

            OssUploadResult result = new OssUploadResult();
            result.setScanId(scanId);
            result.setOssUrl(ossService.generatePresignedUrl(ossKey));
            result.setFileSize(fileSize);
            result.setChecksumMd5(md5);
            result.setStatus("success");
            return result;
        } catch (Exception exception) {
            logger.error("Failed to upload scan {} to OSS", scanId, exception);
            if (isPermanentSourceFailure(exception)) {
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
            results.add(self.uploadLoadedScan(scan));
        }
        return results;
    }

    @Override
    public MigrationStatisticsDTO getStatistics() {
        MigrationStatisticsDTO statistics = new MigrationStatisticsDTO();
        Map<String, Object> counts = scanMapper.countMigrationStats();
        long total = number(counts, "total");
        long migrated = number(counts, "migrated");
        long verified = number(counts, "verified");
        long failed = number(counts, "failed");
        long retryWait = number(counts, "retry_wait");
        long migrating = number(counts, "migrating");
        long migratedTotal = migrated + verified;

        statistics.setTotalCount(total);
        statistics.setMigratedCount(migratedTotal);
        statistics.setFailedCount(failed);
        statistics.setRetryWaitCount(retryWait);
        statistics.setMigratingCount(migrating);
        statistics.setPendingCount(Math.max(0, total - migratedTotal - failed));
        if (total > 0) {
            statistics.setPercentage(Math.round(migratedTotal * 10000.0 / total) / 100.0);
        }
        return statistics;
    }

    @Override
    public MigrationReadinessDTO getReadiness(int sampleSize) {
        int safeSampleSize = Math.max(1, Math.min(sampleSize, 200));
        MigrationStatisticsDTO statistics = getStatistics();
        MigrationReadinessDTO readiness = new MigrationReadinessDTO();
        readiness.setPendingCount(statistics.getPendingCount());

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
            readiness.getWarnings().add("本地、NAS、Nginx 或 HTTP 图片源均未通过抽样读取");
        }
        if (readiness.getSampleMissingCount() > 0) {
            readiness.getWarnings().add("抽样中存在缺失或不可读文件，任务会标记失败并继续处理后续记录");
        }
        if (statistics.getPendingCount() == 0) {
            readiness.getWarnings().add("当前没有待迁移记录");
        } else if (sample.isEmpty()) {
            readiness.getWarnings().add("当前只有尚未到重试时间的记录，暂时没有可领取的迁移数据");
        }

        boolean hasReadableCandidate = !sample.isEmpty() && readiness.getSampleReadableCount() > 0;
        readiness.setReady(
                ossConfigured
                        && activeJob == null
                        && statistics.getPendingCount() > 0
                        && hasReadableCandidate
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
        assertMigrationCanStart();

        Integer maxScanId = scanMapper.findMaxPendingMigrationId(folder);
        if (maxScanId == null) {
            return null;
        }
        long eligibleCount = scanMapper.countEligibleMigrations(maxScanId, folder);
        long totalCount = "full".equals(mode)
                ? eligibleCount
                : Math.min(eligibleCount, requestedCount);
        if (totalCount <= 0) {
            return null;
        }

        MigrationJob job = new MigrationJob();
        job.setStatus("pending");
        job.setMode(mode);
        job.setScopeValue(folder);
        job.setRequestedCount("full".equals(mode) ? eligibleCount : requestedCount);
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

        // 当前方法不包裹事务，确保 insert 已提交后才启动异步线程。
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
        return scanMapper.resetMigrationFailures(new ArrayList<>(uniqueIds));
    }

    @Override
    public boolean hasActiveMigrationJob() {
        return migrationJobMapper.findLatestActive() != null;
    }

    private void executeMigrationJobAsync(Long jobId) {
        taskAsyncExecutor.execute(() -> {
            MigrationJob job = migrationJobMapper.findById(jobId);
            if (job == null) {
                logger.error("Migration job {} was not visible after creation", jobId);
                return;
            }
            try {
                job.setStatus("running");
                job.setStartedAt(new Date());
                job.setCancelRequested(false);
                migrationJobMapper.update(job);

                long processed = 0;
                long failed = 0;
                int afterId = 0;
                boolean cancelled = false;

                while (processed < job.getTotalCount()) {
                    if (Boolean.TRUE.equals(migrationJobMapper.isCancelRequested(jobId))) {
                        cancelled = true;
                        break;
                    }

                    int fetchSize = (int) Math.min(JOB_BATCH_SIZE, job.getTotalCount() - processed);
                    List<Scan> batch = scanMapper.findPendingMigrationAfterId(
                            afterId,
                            job.getMaxScanId(),
                            job.getScopeValue(),
                            fetchSize
                    );
                    if (batch.isEmpty()) {
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
                            result = self.uploadLoadedScan(scan);
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
                                || "skipped".equals(result.getStatus()))) {
                            failed++;
                        }
                    }

                    updateJobProgress(job, processed, failed);
                    if (cancelled) {
                        break;
                    }
                }

                job.setProcessedCount(processed);
                job.setFailedCount(failed);
                job.setRate(calculateRate(processed, job.getTotalCount()));
                job.setCompletedAt(new Date());
                job.setCancelRequested(cancelled);
                if (cancelled) {
                    job.setStatus("cancelled");
                    job.setErrorMessage("管理员已请求安全取消，未处理记录仍保持待迁移状态");
                } else if (processed == 0 && job.getTotalCount() > 0) {
                    job.setStatus("failed");
                    job.setErrorMessage("未领取到可处理记录，请刷新迁移前检查后重试");
                } else {
                    job.setStatus(failed > 0 ? "completed_with_errors" : "completed");
                }
                migrationJobMapper.update(job);
                logger.info(
                        "Migration job {} finished: status={}, processed={}, failed={}",
                        jobId,
                        job.getStatus(),
                        processed,
                        failed
                );
            } catch (Exception exception) {
                logger.error("Migration job {} error", jobId, exception);
                MigrationJob latest = migrationJobMapper.findById(jobId);
                if (latest != null) {
                    latest.setStatus("failed");
                    latest.setErrorMessage(safeErrorMessage(exception));
                    latest.setCompletedAt(new Date());
                    migrationJobMapper.update(latest);
                }
            }
        });
    }

    private void updateJobProgress(MigrationJob job, long processed, long failed) {
        job.setProcessedCount(processed);
        job.setFailedCount(failed);
        job.setRate(calculateRate(processed, job.getTotalCount()));
        migrationJobMapper.update(job);
    }

    private BigDecimal calculateRate(long processed, long total) {
        if (total <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(processed * 100.0 / total)
                .setScale(2, RoundingMode.HALF_UP);
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
        logMigration(scanId, sourceDescription, null, "failed", message, null, null);
        if (currentAttempt >= MAX_ATTEMPTS || "OBJECT_CONFLICT".equals(errorCode)) {
            scanMapper.markMigrationFailed(scanId, errorCode);
            return new OssUploadResult(scanId, "failed", message);
        }
        long delaySeconds = Math.min(1800L, 60L * (1L << Math.max(0, currentAttempt - 1)));
        Date nextRetryAt = Date.from(Instant.now().plusSeconds(delaySeconds));
        scanMapper.markMigrationRetryWait(scanId, errorCode, nextRetryAt);
        return new OssUploadResult(scanId, "retry_wait", message);
    }

    private boolean isPermanentSourceFailure(Exception error) {
        String message = safeErrorMessage(error).toLowerCase(Locale.ROOT);
        return message.contains("不存在")
                || message.contains("不可读")
                || message.contains("不能为空")
                || message.contains("非法")
                || message.contains("越出")
                || message.contains("没有可用")
                || message.contains("status 404")
                || message.contains("状态码 404");
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

    private void assertMigrationCanStart() {
        MigrationReadinessDTO readiness = getReadiness(20);
        if (!readiness.isOssConfigured()) {
            throw new IllegalStateException("OSS 尚未完成配置，请先检查 endpoint、bucket 与访问凭据");
        }
        if (!readiness.isSourcePathReadable()) {
            throw new IllegalStateException("本地、NAS、Nginx 或 HTTP 图片源均未通过读取检查");
        }
        if (readiness.getPendingCount() <= 0 || readiness.getSampleSize() <= 0) {
            throw new IllegalStateException("当前没有到期且可领取的待迁移记录");
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
        String folder = safeSegment(scan.getFolder());
        String bah = safeSegment(scan.getBah());
        String filename = safeSegment(scan.getFilename());
        if (folder == null || folder.length() < 5 || bah == null || filename == null) {
            return null;
        }
        String directoryKey = MedicalRecordCodeUtils.requiresSjhForBah(bah)
                ? safeSegment(scan.getSjh())
                : safeSegment(scan.getBrxh());
        if (directoryKey == null) {
            return null;
        }
        return String.format(
                "medical-records/%s/%s/%s-%s/%s",
                folder.substring(0, 5),
                folder,
                directoryKey,
                bah,
                filename
        );
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
