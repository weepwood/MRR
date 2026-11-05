package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.config.ArchiveExportProperties;
import com.zjcxph.imgapi.dto.req.ArchiveExportJobRequest;
import com.zjcxph.imgapi.dto.resp.ArchiveExportJobResponse;
import com.zjcxph.imgapi.dto.resp.ArchiveExportSummary;
import com.zjcxph.imgapi.entity.ArchiveExportJob;
import com.zjcxph.imgapi.exception.ArchiveExportCancelledException;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.repository.ArchiveExportJobRepository;
import com.zjcxph.imgapi.repository.ArchiveExportJobRepository.RecoveryCursor;
import com.zjcxph.imgapi.service.impl.ArchiveExportTempFileManager;
import com.zjcxph.imgapi.utils.MedicalRecordCodeUtils;
import com.zjcxph.imgapi.utils.ExportSizeLimitOutputStream;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ArchiveExportJobService {

    private static final int MAX_SELECTED_JOB_ITEMS = 10_000;
    private static final Logger logger = LoggerFactory.getLogger(ArchiveExportJobService.class);
    private static final String BAH_REQUIRES_SJH_MESSAGE =
            "病案号大于等于 10000000 时必须使用上架号导出";

    private final ArchiveExportService archiveExportService;
    private final ArchiveExportJobRepository repository;
    private final ArchiveExportTempFileManager tempFileManager;
    private final ArchiveExportProperties properties;
    private final Executor executor;
    private final ConcurrentHashMap<String, AtomicBoolean> cancellationFlags = new ConcurrentHashMap<>();
    private final Set<String> submittedJobIds = ConcurrentHashMap.newKeySet();
    private boolean recoveryRequested;
    private boolean recoveryInitialized;
    private RecoveryCursor recoveryUpperBound;
    private RecoveryCursor recoveryAfter;

    public ArchiveExportJobService(
            ArchiveExportService archiveExportService,
            ArchiveExportJobRepository repository,
            ArchiveExportTempFileManager tempFileManager,
            ArchiveExportProperties properties,
            @Qualifier("archiveExportExecutor") Executor executor) {
        this.archiveExportService = archiveExportService;
        this.repository = repository;
        this.tempFileManager = tempFileManager;
        this.properties = properties;
        this.executor = executor;
    }

    public boolean shouldUseJob(ArchiveExportService.BatchZipExport export) {
        if (export == null) {
            return false;
        }
        long estimated = estimateBytes(export);
        return export.itemCount() >= properties.getAsyncItemThreshold()
                || estimated >= properties.getAsyncEstimatedBytesThreshold()
                || export.sourceSummary().size() >= properties.getAsyncSourceCountThreshold();
    }

    public ArchiveExportJobResponse create(AuthSession session, ArchiveExportJobRequest request) {
        requireSession(session);
        if (request == null) {
            throw new BusinessException(400, "导出任务参数不能为空");
        }
        String format = normalizeFormat(request.getFormat());
        requirePermission(session, format);
        List<String> ids = normalizeIds(request.getIds());
        if (ids.size() > MAX_SELECTED_JOB_ITEMS) {
            throw new BusinessException(400, "单次异步导出最多包含 10000 张影像");
        }

        String bah = MedicalRecordCodeUtils.normalizeOrEmpty(request.getBah());
        String sjh = MedicalRecordCodeUtils.normalizeOrEmpty(request.getSjh());
        String scope = ids.isEmpty() ? "WHOLE_ARCHIVE" : "SELECTED_IMAGES";
        if (ids.isEmpty()) {
            validateWholeArchiveCode(bah, sjh);
        }
        String scanIds = ids.isEmpty() ? null : String.join(",", ids);
        String idempotencyKey = normalizeIdempotencyKey(request.getIdempotencyKey());
        if (idempotencyKey != null) {
            var existing = repository.findByIdempotency(
                    session.getId(), session.getUsername(), idempotencyKey);
            if (existing.isPresent() && !"EXPIRED".equals(existing.get().getStatus())) {
                if (!matchesRequest(existing.get(), format, scope, bah, sjh, scanIds)) {
                    throw new BusinessException(409, "幂等键已被其他导出请求使用");
                }
                return ArchiveExportJobResponse.from(existing.get());
            }
        }

        ArchiveExportService.BatchZipExport selected = ids.isEmpty()
                ? null : archiveExportService.prepareSelectedArchive(ids);
        ArchiveExportSummary summary = ids.isEmpty()
                ? archiveExportService.summarizeArchive(bah, sjh)
                : new ArchiveExportSummary(selected.itemCount(), selected.estimatedBytes());
        if (summary.itemCount() == 0) {
            throw new BusinessException(404, "未找到可导出的影像");
        }
        long estimatedBytes = estimateBytes(summary.itemCount(), summary.estimatedBytes());
        if (estimatedBytes > properties.getMaxFileBytes()) {
            throw new BusinessException(413, "预计导出文件超过单文件配额，请减少选页并分批导出");
        }

        ArchiveExportJob job = new ArchiveExportJob();
        job.setId(UUID.randomUUID().toString());
        job.setOwnerUserId(session.getId());
        job.setOwnerUsername(session.getUsername());
        job.setFormat(format);
        job.setScope(scope);
        job.setStatus("PENDING");
        job.setBah(bah);
        job.setSjh(sjh);
        job.setScanIds(scanIds);
        job.setPlannedCount(summary.itemCount());
        job.setEstimatedBytes(estimatedBytes);
        job.setSourceSummary(ids.isEmpty() ? "" : String.join(",", selected.sourceSummary()));
        job.setFileName(buildFileName(job));
        job.setIdempotencyKey(idempotencyKey);
        job.setExpiresAt(LocalDateTime.now().plus(properties.getRetention()));

        // Register before the row becomes visible to startup recovery, including requests before Ready.
        submittedJobIds.add(job.getId());
        try {
            repository.insert(job);
        } catch (DuplicateKeyException exception) {
            submittedJobIds.remove(job.getId());
            ArchiveExportJob existing = repository.findByIdempotency(
                            session.getId(), session.getUsername(), idempotencyKey)
                    .orElseThrow(() -> exception);
            if (!matchesRequest(existing, format, scope, bah, sjh, scanIds)) {
                throw new BusinessException(409, "幂等键已被其他导出请求使用");
            }
            return ArchiveExportJobResponse.from(existing);
        } catch (RuntimeException exception) {
            submittedJobIds.remove(job.getId());
            throw exception;
        }
        submit(job.getId());
        return ArchiveExportJobResponse.from(repository.findById(job.getId()).orElse(job));
    }

    public ArchiveExportJobResponse get(AuthSession session, String id) {
        return ArchiveExportJobResponse.from(requireOwned(session, id));
    }

    public ArchiveExportJobResponse cancel(AuthSession session, String id) {
        ArchiveExportJob job = requireOwned(session, id);
        requirePermission(session, job.getFormat());
        if (Set.of("SUCCESS", "FAILED", "CANCELLED", "EXPIRED").contains(job.getStatus())) {
            return ArchiveExportJobResponse.from(job);
        }
        if (repository.requestCancel(id) == 0) {
            return ArchiveExportJobResponse.from(requireOwned(session, id));
        }
        // 仅Worker拥有内存标志；尚未领取的取消由数据库状态承载，避免领取失败后遗留条目。
        AtomicBoolean cancellation = cancellationFlags.get(id);
        if (cancellation != null) {
            cancellation.set(true);
        }
        if ("PENDING".equals(job.getStatus())) {
            repository.markCancelled(id, LocalDateTime.now());
        }
        return ArchiveExportJobResponse.from(requireOwned(session, id));
    }

    public Path requireDownloadFile(AuthSession session, String id) throws IOException {
        ArchiveExportJob job = requireOwned(session, id);
        requirePermission(session, job.getFormat());
        if (Set.of("PENDING", "PROCESSING").contains(job.getStatus())) {
            throw new BusinessException(409, "导出任务尚未完成");
        }
        if (job.getExpiresAt() != null && job.getExpiresAt().isBefore(LocalDateTime.now())) {
            expireFile(job);
            throw new BusinessException(410, "导出文件已过期");
        }
        if (!"SUCCESS".equals(job.getStatus())) {
            throw new BusinessException(409, "导出任务尚未完成");
        }
        Path file = tempFileManager.requireManagedFile(job.getFilePath());
        if (Files.size(file) > properties.getMaxFileBytes()) {
            throw new BusinessException(413, "导出文件超过当前单文件配额，请减少选页并分批导出");
        }
        return file;
    }

    public ArchiveExportJob requireOwned(AuthSession session, String id) {
        requireSession(session);
        ArchiveExportJob job = repository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "导出任务不存在"));
        if (!isOwnedBy(session, job)) {
            throw new BusinessException(403, "无权访问该导出任务");
        }
        return job;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void recoverJobsAfterRestart() {
        recoveryRequested = true;
        continueStartupRecovery();
    }

    @Scheduled(fixedDelayString = "${archive.export.recovery-interval:PT1S}")
    public synchronized void continueStartupRecovery() {
        if (!recoveryRequested) return;
        try {
            recoverStartupBatch();
        } catch (DataAccessException exception) {
            // Keep the last accepted cursor and retry; do not abort application startup or log SQL details.
            logger.warn("Archive export startup recovery deferred: database unavailable");
        }
    }

    private void recoverStartupBatch() {
        if (!recoveryInitialized) {
            recoveryUpperBound = repository.findRecoveryUpperBound().orElse(null);
            recoveryInitialized = true;
        }
        if (recoveryUpperBound == null) return;
        int batchSize = Math.max(1, Math.min(200, properties.getWorkerQueueCapacity()));
        List<ArchiveExportJob> page = repository.findRecoveryPage(recoveryAfter, recoveryUpperBound, batchSize);
        if (page.isEmpty()) {
            recoveryUpperBound = null;
            return;
        }
        for (ArchiveExportJob job : page) {
            if (!submittedJobIds.add(job.getId())) {
                recoveryAfter = new RecoveryCursor(job.getCreatedAt(), job.getId());
                continue;
            }
            boolean accepted = false;
            try {
                if (Boolean.TRUE.equals(job.getCancelRequested())) {
                    if (!cancelRecoveredJob(job)) return;
                } else if (repository.resetToPending(job.getId()) != 0) {
                    // A full or shutting-down executor leaves this row pending and this cursor unchanged.
                    if (!executeOwnedJob(job.getId())) return;
                    accepted = true;
                } else if (!reconcileRecoveryCandidate(job.getId())) {
                    return;
                }
                recoveryAfter = new RecoveryCursor(job.getCreatedAt(), job.getId());
            } finally {
                if (!accepted) submittedJobIds.remove(job.getId());
            }
        }
    }

    private boolean reconcileRecoveryCandidate(String id) {
        var current = repository.findById(id);
        if (current.isEmpty() || Set.of("SUCCESS", "FAILED", "CANCELLED", "EXPIRED")
                .contains(current.get().getStatus())) return true;
        return Boolean.TRUE.equals(current.get().getCancelRequested()) && cancelRecoveredJob(current.get());
    }

    private boolean cancelRecoveredJob(ArchiveExportJob job) {
        if (repository.markCancelled(job.getId(), LocalDateTime.now()) == 0) {
            var current = repository.findById(job.getId());
            return current.isEmpty() || Set.of("SUCCESS", "FAILED", "CANCELLED", "EXPIRED")
                    .contains(current.get().getStatus());
        }
        tempFileManager.deleteManagedFile(job.getFilePath());
        return true;
    }

    @Scheduled(fixedDelayString = "${archive.export.cleanup-interval:PT15M}")
    public void cleanupExpiredFiles() {
        for (ArchiveExportJob job : repository.findExpired(LocalDateTime.now())) {
            expireFile(job);
        }
    }

    private void expireFile(ArchiveExportJob job) {
        if (tempFileManager.deleteManagedFile(job.getFilePath())) {
            repository.markExpired(job.getId());
        } else {
            // 失败记录轮转到队尾，避免最旧200条长期占满所有清理批次。
            repository.deferCleanup(job.getId());
        }
    }

    private void submit(String id) {
        boolean accepted = false;
        try {
            accepted = executeOwnedJob(id);
            if (!accepted) {
                repository.markFailed(id, "异步导出队列已满，请稍后重试", LocalDateTime.now());
            }
        } finally {
            // Keep rejected new rows owned until the failure update finishes, including during Ready.
            if (!accepted) submittedJobIds.remove(id);
        }
    }

    private boolean executeOwnedJob(String id) {
        try {
            executor.execute(() -> {
                try {
                    runJob(id);
                } finally {
                    submittedJobIds.remove(id);
                }
            });
            return true;
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }

    private void runJob(String id) {
        ArchiveExportJob job = repository.findById(id).orElse(null);
        if (job == null || Set.of("SUCCESS", "FAILED", "CANCELLED", "EXPIRED").contains(job.getStatus())) {
            cancellationFlags.remove(id);
            return;
        }
        // 在准备、预留或删除文件之前原子领取；重复 Worker 不得操作同一输出。
        if (repository.markProcessing(id, job.getFileName(), job.getFilePath(),
                LocalDateTime.now().plus(properties.getRetention())) == 0) {
            if (repository.isCancelRequested(id)) {
                repository.markCancelled(id, LocalDateTime.now());
            }
            return;
        }
        AtomicBoolean cancellation = cancellationFlags.computeIfAbsent(id, ignored -> new AtomicBoolean());
        String previousRequestId = MDC.get("requestId");
        MDC.put("requestId", id);
        boolean success = false;
        String extension = job.getFormat().toLowerCase(Locale.ROOT);
        ArchiveExportTempFileManager.Reservation reservation = null;
        try {
            if (Boolean.TRUE.equals(job.getCancelRequested()) || repository.isCancelRequested(id)) {
                throw new ArchiveExportCancelledException();
            }
            ArchiveExportService.BatchZipExport export = prepare(job);
            if (export.itemCount() == 0) {
                throw new IOException("病案已没有可导出的影像");
            }
            if (repository.updatePlan(id, export.itemCount(), estimateBytes(export),
                    String.join(",", export.sourceSummary())) == 0) {
                throw new ArchiveExportCancelledException();
            }
            if (!tempFileManager.deleteManagedFile(job.getFilePath())) {
                throw new IOException("Export temporary cleanup unavailable");
            }
            reservation = tempFileManager.reserve(id, estimateBytes(export), extension);
            if (repository.setOutputFile(id, reservation.path().toString()) == 0) {
                throw new ArchiveExportCancelledException();
            }

            ArchiveExportService.ExportProgress progress = new ArchiveExportService.ExportProgress() {
                private int lastCompleted;
                private long lastWrite = System.nanoTime();
                @Override
                public boolean isCancelled() {
                    return cancellation.get() || Thread.currentThread().isInterrupted();
                }

                @Override
                public void onItemCompleted(int completed, int total, com.zjcxph.imgapi.entity.PathDO item) {
                    long now = System.nanoTime();
                    if (completed == total || completed - lastCompleted >= 100
                            || now - lastWrite >= 1_000_000_000L) {
                        repository.updateProgress(id, completed);
                        lastCompleted = completed;
                        lastWrite = System.nanoTime();
                    }
                }
            };

            try (OutputStream output = tempFileManager.openOutput(reservation)) {
                if ("ZIP".equals(job.getFormat())) {
                    archiveExportService.writeBatchZip(export, output, progress);
                } else {
                    archiveExportService.writeBatchPdf(export, output, progress);
                }
            }

            long outputBytes = Files.size(reservation.path());
            if (outputBytes <= 0) {
                throw new IOException("导出文件为空");
            }
            String sha256;
            try (InputStream input = Files.newInputStream(reservation.path())) {
                sha256 = DigestUtils.sha256Hex(input);
            }
            if (cancellation.get() || Thread.currentThread().isInterrupted() || repository.isCancelRequested(id)
                    || repository.markSuccess(id, outputBytes, sha256, LocalDateTime.now()) == 0) {
                throw new ArchiveExportCancelledException();
            }
            success = true;
        } catch (ArchiveExportCancelledException exception) {
            repository.markCancelled(id, LocalDateTime.now());
        } catch (Exception exception) {
            logger.error("Archive export job failed: jobId={}", id, exception);
            if (repository.markFailed(id, publicFailureMessage(exception), LocalDateTime.now()) == 0
                    && repository.isCancelRequested(id)) {
                repository.markCancelled(id, LocalDateTime.now());
            }
        } finally {
            cancellationFlags.remove(id);
            if (reservation != null) {
                if (!success) {
                    cleanupFailedOutput(id, reservation.path());
                }
                reservation.close();
            }
            if (previousRequestId == null) {
                MDC.remove("requestId");
            } else {
                MDC.put("requestId", previousRequestId);
            }
        }
    }

    private String publicFailureMessage(Exception exception) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(exception);
        if (cause instanceof ExportSizeLimitOutputStream.LimitExceededException
                || (cause instanceof BusinessException business && business.getCode() == 413)) {
            return ExportSizeLimitOutputStream.LIMIT_MESSAGE;
        }
        return "病案导出失败，请使用任务编号联系管理员";
    }

    private void cleanupFailedOutput(String id, Path output) {
        try {
            // 成功提交后连接中断可能丢失确认；状态不明时保留文件供重启恢复，不能删除成功文件。
            var current = repository.findById(id);
            if (current.isPresent() && Set.of("FAILED", "CANCELLED").contains(current.get().getStatus())) {
                tempFileManager.deleteManagedFile(output.toString());
            }
        } catch (RuntimeException exception) {
            logger.warn("Export output retained pending state reconciliation");
        }
    }

    private ArchiveExportService.BatchZipExport prepare(ArchiveExportJob job) {
        if ("SELECTED_IMAGES".equals(job.getScope())) {
            return archiveExportService.prepareSelectedArchive(parseIds(job.getScanIds()));
        }
        return archiveExportService.prepareArchive(job.getBah(), job.getSjh());
    }

    private List<String> parseIds(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(",", -1))
                .map(String::trim)
                .toList();
    }

    private long estimateBytes(ArchiveExportService.BatchZipExport export) {
        return estimateBytes(export.itemCount(), export.estimatedBytes());
    }

    private long estimateBytes(int itemCount, long known) {
        if (known > 0) {
            return known;
        }
        try {
            return Math.multiplyExact(itemCount, properties.getFallbackBytesPerImage());
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private String buildFileName(ArchiveExportJob job) {
        String stem;
        if ("WHOLE_ARCHIVE".equals(job.getScope())) {
            stem = job.getBah() == null || job.getBah().isBlank() ? "archive" : job.getBah();
            if (job.getSjh() != null && !job.getSjh().isBlank()) {
                stem += "-" + job.getSjh();
            }
        } else {
            stem = "archive-selected-" + job.getId();
        }
        return stem + "." + job.getFormat().toLowerCase(Locale.ROOT);
    }

    private void validateWholeArchiveCode(String bah, String sjh) {
        if (bah.isEmpty() && sjh.isEmpty()) {
            throw new BusinessException(400, "病案号和上架号不能同时为空");
        }
        if (MedicalRecordCodeUtils.requiresSjhForBah(bah) && sjh.isEmpty()) {
            throw new BusinessException(400, BAH_REQUIRES_SJH_MESSAGE);
        }
    }

    private boolean matchesRequest(
            ArchiveExportJob existing,
            String format,
            String scope,
            String bah,
            String sjh,
            String scanIds) {
        return Objects.equals(existing.getFormat(), format)
                && Objects.equals(existing.getScope(), scope)
                && Objects.equals(normalize(existing.getBah()), normalize(bah))
                && Objects.equals(normalize(existing.getSjh()), normalize(sjh))
                && Objects.equals(normalize(existing.getScanIds()), normalize(scanIds));
    }

    private boolean isOwnedBy(AuthSession session, ArchiveExportJob job) {
        if (session.isAdmin()) {
            return true;
        }
        if (job.getOwnerUserId() != null) {
            return Objects.equals(session.getId(), job.getOwnerUserId());
        }
        // 仅兼容迁移前没有 owner_user_id 的历史任务。
        return Objects.equals(session.getUsername(), job.getOwnerUsername());
    }

    private String normalizeFormat(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("ZIP") && !normalized.equals("PDF")) {
            throw new BusinessException(400, "导出格式仅支持 ZIP 或 PDF");
        }
        return normalized;
    }

    private List<String> normalizeIds(List<String> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream().map((value) -> {
            String normalized = normalize(value);
            if (normalized == null || normalized.isBlank()) {
                throw new BusinessException(400, "影像 ID 不能为空");
            }
            return normalized;
        }).toList();
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeIdempotencyKey(String value) {
        String normalized = normalize(value);
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        if (normalized.length() > 128 || !normalized.matches("[A-Za-z0-9._:-]+")) {
            throw new BusinessException(400, "幂等键格式不正确");
        }
        return normalized;
    }

    private void requireSession(AuthSession session) {
        if (session == null || session.getId() == null
                || session.getUsername() == null || session.getUsername().isBlank()) {
            throw new BusinessException(401, "请先登录");
        }
    }

    private void requirePermission(AuthSession session, String format) {
        String permission = "PDF".equals(format) ? "record:pdf:export" : "record:download";
        if (!session.hasPermission(permission)) {
            throw new BusinessException(403, "没有病案导出权限");
        }
    }
}
