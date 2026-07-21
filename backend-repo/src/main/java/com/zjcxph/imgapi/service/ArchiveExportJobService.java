package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.config.ArchiveExportProperties;
import com.zjcxph.imgapi.dto.req.ArchiveExportJobRequest;
import com.zjcxph.imgapi.dto.resp.ArchiveExportJobResponse;
import com.zjcxph.imgapi.entity.ArchiveExportJob;
import com.zjcxph.imgapi.exception.ArchiveExportCancelledException;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.repository.ArchiveExportJobRepository;
import com.zjcxph.imgapi.service.impl.ArchiveExportTempFileManager;
import com.zjcxph.imgapi.utils.MedicalRecordCodeUtils;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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
    private static final String BAH_REQUIRES_SJH_MESSAGE =
            "病案号大于等于 10000000 时必须使用上架号导出";

    private final ArchiveExportService archiveExportService;
    private final ArchiveExportJobRepository repository;
    private final ArchiveExportTempFileManager tempFileManager;
    private final ArchiveExportProperties properties;
    private final Executor executor;
    private final ConcurrentHashMap<String, AtomicBoolean> cancellationFlags = new ConcurrentHashMap<>();

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
            var existing = repository.findByIdempotency(session.getUsername(), idempotencyKey);
            if (existing.isPresent() && !"EXPIRED".equals(existing.get().getStatus())) {
                if (!matchesRequest(existing.get(), format, scope, bah, sjh, scanIds)) {
                    throw new BusinessException(409, "幂等键已被其他导出请求使用");
                }
                return ArchiveExportJobResponse.from(existing.get());
            }
        }

        ArchiveExportService.BatchZipExport export = ids.isEmpty()
                ? archiveExportService.prepareArchive(bah, sjh)
                : archiveExportService.prepareSelectedArchive(ids);
        if (export.itemCount() == 0) {
            throw new BusinessException(404, "未找到可导出的影像");
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
        job.setPlannedCount(export.itemCount());
        job.setEstimatedBytes(estimateBytes(export));
        job.setSourceSummary(String.join(",", export.sourceSummary()));
        job.setFileName(buildFileName(job));
        job.setIdempotencyKey(idempotencyKey);
        job.setExpiresAt(LocalDateTime.now().plus(properties.getRetention()));

        try {
            repository.insert(job);
        } catch (DuplicateKeyException exception) {
            ArchiveExportJob existing = repository.findByIdempotency(session.getUsername(), idempotencyKey)
                    .orElseThrow(() -> exception);
            if (!matchesRequest(existing, format, scope, bah, sjh, scanIds)) {
                throw new BusinessException(409, "幂等键已被其他导出请求使用");
            }
            return ArchiveExportJobResponse.from(existing);
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
        repository.requestCancel(id);
        cancellationFlags.computeIfAbsent(id, ignored -> new AtomicBoolean()).set(true);
        if ("PENDING".equals(job.getStatus())) {
            repository.markCancelled(id, LocalDateTime.now());
        }
        return ArchiveExportJobResponse.from(requireOwned(session, id));
    }

    public Path requireDownloadFile(AuthSession session, String id) throws IOException {
        ArchiveExportJob job = requireOwned(session, id);
        requirePermission(session, job.getFormat());
        if (job.getExpiresAt() != null && job.getExpiresAt().isBefore(LocalDateTime.now())) {
            tempFileManager.deleteManagedFile(job.getFilePath());
            repository.markExpired(id);
            throw new BusinessException(410, "导出文件已过期");
        }
        if (!"SUCCESS".equals(job.getStatus())) {
            throw new BusinessException(409, "导出任务尚未完成");
        }
        return tempFileManager.requireManagedFile(job.getFilePath());
    }

    public ArchiveExportJob requireOwned(AuthSession session, String id) {
        requireSession(session);
        ArchiveExportJob job = repository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "导出任务不存在"));
        if (!session.isAdmin() && !session.getUsername().equals(job.getOwnerUsername())) {
            throw new BusinessException(403, "无权访问该导出任务");
        }
        return job;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverJobsAfterRestart() {
        for (ArchiveExportJob job : repository.findRecoverable()) {
            if (Boolean.TRUE.equals(job.getCancelRequested())) {
                repository.markCancelled(job.getId(), LocalDateTime.now());
                tempFileManager.deleteManagedFile(job.getFilePath());
                continue;
            }
            repository.resetToPending(job.getId());
            submit(job.getId());
        }
    }

    @Scheduled(fixedDelayString = "${archive.export.cleanup-interval:PT15M}")
    public void cleanupExpiredFiles() {
        for (ArchiveExportJob job : repository.findExpired(LocalDateTime.now())) {
            tempFileManager.deleteManagedFile(job.getFilePath());
            repository.markExpired(job.getId());
        }
    }

    private void submit(String id) {
        try {
            executor.execute(() -> runJob(id));
        } catch (RejectedExecutionException exception) {
            repository.markFailed(id, "异步导出队列已满，请稍后重试", LocalDateTime.now());
        }
    }

    private void runJob(String id) {
        ArchiveExportJob job = repository.findById(id).orElse(null);
        if (job == null || Set.of("SUCCESS", "FAILED", "CANCELLED", "EXPIRED").contains(job.getStatus())) {
            return;
        }
        AtomicBoolean cancellation = cancellationFlags.computeIfAbsent(id, ignored -> new AtomicBoolean());
        boolean success = false;
        String extension = job.getFormat().toLowerCase(Locale.ROOT);
        ArchiveExportTempFileManager.Reservation reservation = null;
        try {
            if (Boolean.TRUE.equals(job.getCancelRequested()) || repository.isCancelRequested(id)) {
                throw new ArchiveExportCancelledException();
            }
            ArchiveExportService.BatchZipExport export = prepare(job);
            reservation = tempFileManager.reserve(id, estimateBytes(export), extension);
            repository.markProcessing(
                    id,
                    job.getFileName(),
                    reservation.path().toString(),
                    LocalDateTime.now().plus(properties.getRetention()));

            ArchiveExportService.ExportProgress progress = new ArchiveExportService.ExportProgress() {
                @Override
                public boolean isCancelled() {
                    return cancellation.get() || repository.isCancelRequested(id);
                }

                @Override
                public void onItemCompleted(int completed, int total, com.zjcxph.imgapi.entity.PathDO item) {
                    repository.updateProgress(id, completed);
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
            String sha256;
            try (InputStream input = Files.newInputStream(reservation.path())) {
                sha256 = DigestUtils.sha256Hex(input);
            }
            repository.markSuccess(id, outputBytes, sha256, LocalDateTime.now());
            success = true;
        } catch (ArchiveExportCancelledException exception) {
            repository.markCancelled(id, LocalDateTime.now());
        } catch (Exception exception) {
            repository.markFailed(id, exception.getMessage(), LocalDateTime.now());
        } finally {
            cancellationFlags.remove(id);
            if (reservation != null) {
                if (!success) {
                    tempFileManager.deleteManagedFile(reservation.path().toString());
                }
                reservation.close();
            }
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
        long known = export.estimatedBytes();
        if (known > 0) {
            return Math.min(known, properties.getMaxFileBytes());
        }
        try {
            return Math.min(
                    Math.multiplyExact(export.itemCount(), properties.getFallbackBytesPerImage()),
                    properties.getMaxFileBytes());
        } catch (ArithmeticException exception) {
            return properties.getMaxFileBytes();
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
        return ids.stream().map(this::normalize).toList();
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
        if (session == null || session.getUsername() == null || session.getUsername().isBlank()) {
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
