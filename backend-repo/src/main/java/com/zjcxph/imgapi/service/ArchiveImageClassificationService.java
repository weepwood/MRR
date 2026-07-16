package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.config.ClassificationProperties;
import com.zjcxph.imgapi.entity.ClassificationJob;
import com.zjcxph.imgapi.entity.ImageClassification;
import com.zjcxph.imgapi.entity.RecordTypeDefinition;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.mapper.ArchiveRecordMapper;
import com.zjcxph.imgapi.mapper.ClassificationJobMapper;
import com.zjcxph.imgapi.mapper.ImageClassificationMapper;
import com.zjcxph.imgapi.mapper.RecordTypeMapper;
import com.zjcxph.imgapi.mapper.ScanMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Service
public class ArchiveImageClassificationService {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveImageClassificationService.class);
    private static final Set<String> ALLOWED_SCOPES = Set.of("UNCLASSIFIED", "LOW_CONFIDENCE", "ALL");

    private final ClassificationProperties properties;
    private final ArchiveRecordMapper archiveRecordMapper;
    private final ScanMapper scanMapper;
    private final RecordTypeMapper recordTypeMapper;
    private final ImageClassificationMapper classificationMapper;
    private final ClassificationJobMapper jobMapper;
    private final CommandOcrService ocrService;
    private final KeywordClassificationEngine classificationEngine;
    private final Executor taskExecutor;

    public ArchiveImageClassificationService(
            ClassificationProperties properties,
            ArchiveRecordMapper archiveRecordMapper,
            ScanMapper scanMapper,
            RecordTypeMapper recordTypeMapper,
            ImageClassificationMapper classificationMapper,
            ClassificationJobMapper jobMapper,
            CommandOcrService ocrService,
            KeywordClassificationEngine classificationEngine,
            @Qualifier("classificationTaskExecutor") Executor taskExecutor
    ) {
        this.properties = properties;
        this.archiveRecordMapper = archiveRecordMapper;
        this.scanMapper = scanMapper;
        this.recordTypeMapper = recordTypeMapper;
        this.classificationMapper = classificationMapper;
        this.jobMapper = jobMapper;
        this.ocrService = ocrService;
        this.classificationEngine = classificationEngine;
        this.taskExecutor = taskExecutor;
    }

    public List<RecordTypeDefinition> findEnabledTypes() {
        return recordTypeMapper.findEnabled();
    }

    public ClassificationJob startJob(Long archiveId, String requestedScope, String createdBy) {
        ensureAvailable();
        if (archiveId == null || archiveId <= 0 || archiveRecordMapper.findById(archiveId) == null) {
            throw new BusinessException(404, "未找到病案");
        }

        String scope = normalizeScope(requestedScope);
        BigDecimal threshold = defaultThreshold();
        long total = scanMapper.countClassificationTargets(archiveId, scope, threshold);

        ClassificationJob job = new ClassificationJob();
        job.setArchiveId(archiveId);
        job.setScopeType(scope);
        job.setStatus("PENDING");
        job.setTotalCount(total);
        job.setModelVersion(properties.getModelVersion());
        job.setCreatedBy(normalizeOperator(createdBy));
        jobMapper.insert(job);

        if (total == 0) {
            jobMapper.markRunning(job.getId());
            jobMapper.markCompleted(job.getId());
            return jobMapper.findById(job.getId());
        }

        try {
            taskExecutor.execute(() -> processJob(job.getId()));
        } catch (RejectedExecutionException exception) {
            jobMapper.markFailed(job.getId(), "智能分类任务队列已满");
            throw new BusinessException("智能分类任务队列已满，请稍后重试");
        }
        return jobMapper.findById(job.getId());
    }

    public ClassificationJob findJob(Long jobId) {
        return jobId == null ? null : jobMapper.findById(jobId);
    }

    public List<ImageClassification> findResults(Long archiveId) {
        return archiveId == null ? List.of() : classificationMapper.findByArchiveId(archiveId);
    }

    public boolean cancelJob(Long jobId) {
        return jobId != null && jobMapper.cancel(jobId) > 0;
    }

    @Transactional(rollbackFor = Exception.class)
    @Caching(evict = {
            @CacheEvict(value = "scanByBah", allEntries = true),
            @CacheEvict(value = "scanById", key = "#scanId")
    })
    public ImageClassification confirmSuggestion(Integer scanId, Integer requestedType, String reviewedBy) {
        Scan scan = requireScan(scanId);
        ImageClassification classification = classificationMapper.findByScanId(scanId);
        if (classification == null || classification.getPredictedBtype() == null) {
            throw new BusinessException("当前图片没有可确认的智能分类建议");
        }

        Integer finalType = requestedType == null ? classification.getPredictedBtype() : requestedType;
        if (!recordTypeMapper.existsEnabled(finalType)) {
            throw new BusinessException("图片类型不存在或已停用");
        }

        Integer previousType = scan.getBtype();
        if (scanMapper.updateImageType(scanId, finalType) != 1) {
            throw new BusinessException("更新图片类型失败");
        }

        String operator = normalizeOperator(reviewedBy);
        boolean accepted = finalType.equals(classification.getPredictedBtype());
        classificationMapper.markReviewed(scanId, finalType, operator, accepted ? "CONFIRMED" : "REJECTED");
        classificationMapper.insertAudit(
                scanId,
                scan.getArchiveId(),
                previousType,
                classification.getPredictedBtype(),
                finalType,
                accepted ? "CONFIRM_SUGGESTION" : "OVERRIDE_SUGGESTION",
                "AI_REVIEW",
                classification.getModelVersion(),
                classification.getConfidence(),
                operator,
                accepted ? "采用 OCR 关键词分类建议" : "人工修改 OCR 分类建议"
        );
        return classificationMapper.findByScanId(scanId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Caching(evict = {
            @CacheEvict(value = "scanByBah", allEntries = true),
            @CacheEvict(value = "scanById", allEntries = true)
    })
    public int confirmHighConfidence(Long archiveId, Double requestedThreshold, String reviewedBy) {
        if (archiveId == null || archiveId <= 0) {
            throw new BusinessException("病案 ID 不能为空");
        }
        BigDecimal threshold = normalizeThreshold(requestedThreshold);
        List<ImageClassification> suggestions = classificationMapper.findHighConfidenceSuggestions(archiveId, threshold);
        int confirmed = 0;
        String operator = normalizeOperator(reviewedBy);
        for (ImageClassification suggestion : suggestions) {
            Scan scan = scanMapper.findById(suggestion.getScanId());
            if (scan == null || suggestion.getPredictedBtype() == null) {
                continue;
            }
            Integer previousType = scan.getBtype();
            Integer finalType = suggestion.getPredictedBtype();
            if (scanMapper.updateImageType(scan.getId(), finalType) != 1) {
                throw new BusinessException("批量更新图片类型失败，scanId=" + scan.getId());
            }
            classificationMapper.markReviewed(scan.getId(), finalType, operator, "CONFIRMED");
            classificationMapper.insertAudit(
                    scan.getId(),
                    scan.getArchiveId(),
                    previousType,
                    finalType,
                    finalType,
                    "BATCH_CONFIRM",
                    "AI_REVIEW",
                    suggestion.getModelVersion(),
                    suggestion.getConfidence(),
                    operator,
                    "批量采用高置信度分类建议"
            );
            confirmed++;
        }
        return confirmed;
    }

    @Transactional(rollbackFor = Exception.class)
    @Caching(evict = {
            @CacheEvict(value = "scanByBah", allEntries = true),
            @CacheEvict(value = "scanById", key = "#scanId")
    })
    public void changeTypeManually(Integer scanId, Integer finalType, String operatedBy) {
        if (finalType == null || finalType < 0 || finalType > 14) {
            throw new BusinessException("图片类型错误");
        }
        Scan scan = requireScan(scanId);
        Integer previousType = scan.getBtype();
        if (scanMapper.updateImageType(scanId, finalType) != 1) {
            throw new BusinessException("更新图片类型失败");
        }

        ImageClassification classification = classificationMapper.findByScanId(scanId);
        String operator = normalizeOperator(operatedBy);
        if (classification != null) {
            boolean accepted = finalType.equals(classification.getPredictedBtype());
            classificationMapper.markReviewed(
                    scanId,
                    finalType,
                    operator,
                    accepted ? "CONFIRMED" : "REJECTED"
            );
        }
        classificationMapper.insertAudit(
                scanId,
                scan.getArchiveId(),
                previousType,
                classification == null ? null : classification.getPredictedBtype(),
                finalType,
                "MANUAL_CHANGE",
                "MANUAL",
                classification == null ? null : classification.getModelVersion(),
                classification == null ? null : classification.getConfidence(),
                operator,
                "人工修改影像类型"
        );
    }

    private void processJob(Long jobId) {
        ClassificationJob job = jobMapper.findById(jobId);
        if (job == null || jobMapper.markRunning(jobId) != 1) {
            return;
        }

        long processed = 0;
        long suggested = 0;
        long noMatch = 0;
        long failed = 0;
        int cursor = 0;

        try {
            List<RecordTypeDefinition> definitions = recordTypeMapper.findEnabled();
            int batchSize = Math.max(10, Math.min(500, properties.getBatchSize()));
            BigDecimal threshold = defaultThreshold();

            while (true) {
                ClassificationJob current = jobMapper.findById(jobId);
                if (current == null || "CANCELLED".equals(current.getStatus())) {
                    return;
                }

                List<Scan> scans = scanMapper.findClassificationTargets(
                        job.getArchiveId(),
                        job.getScopeType(),
                        threshold,
                        cursor,
                        batchSize
                );
                if (scans.isEmpty()) {
                    break;
                }

                for (Scan scan : scans) {
                    ClassificationJob currentState = jobMapper.findById(jobId);
                    if (currentState == null || "CANCELLED".equals(currentState.getStatus())) {
                        return;
                    }
                    try {
                        String text = ocrService.recognize(scan);
                        KeywordClassificationEngine.Decision decision = classificationEngine.classify(text, definitions);
                        ImageClassification result = buildResult(scan, decision);
                        classificationMapper.upsert(result);
                        if ("SUGGESTED".equals(decision.state())) {
                            suggested++;
                        } else {
                            noMatch++;
                        }
                    } catch (Exception exception) {
                        failed++;
                        classificationMapper.upsert(buildFailure(scan, exception));
                        logger.warn("影像识别失败: job={}, scan={}, reason={}", jobId, scan.getId(), exception.getMessage());
                    }
                    processed++;
                    cursor = scan.getId();
                }

                jobMapper.updateProgress(jobId, processed, suggested, noMatch, failed, cursor);
            }
            jobMapper.markCompleted(jobId);
        } catch (Exception exception) {
            logger.error("智能分类任务执行失败: job={}", jobId, exception);
            jobMapper.markFailed(jobId, safeMessage(exception));
        }
    }

    private ImageClassification buildResult(Scan scan, KeywordClassificationEngine.Decision decision) {
        ImageClassification result = new ImageClassification();
        result.setScanId(scan.getId());
        result.setArchiveId(scan.getArchiveId());
        result.setPredictedBtype(decision.predictedBtype());
        result.setConfidence(decision.confidence());
        result.setClassificationState(decision.state());
        result.setEffectiveSource("OCR_KEYWORD");
        result.setModelVersion(properties.getModelVersion());
        result.setRuleVersion(properties.getRuleVersion());
        result.setOcrTitle(decision.title());
        result.setEvidence(decision.evidence());
        result.setImageChecksum(scan.getChecksumMd5());
        result.setErrorMessage(null);
        return result;
    }

    private ImageClassification buildFailure(Scan scan, Exception exception) {
        ImageClassification result = new ImageClassification();
        result.setScanId(scan.getId());
        result.setArchiveId(scan.getArchiveId());
        result.setClassificationState("FAILED");
        result.setEffectiveSource("OCR_COMMAND");
        result.setModelVersion(properties.getModelVersion());
        result.setRuleVersion(properties.getRuleVersion());
        result.setEvidence("{}");
        result.setImageChecksum(scan.getChecksumMd5());
        result.setErrorMessage(safeMessage(exception));
        return result;
    }

    private Scan requireScan(Integer scanId) {
        if (scanId == null || scanId <= 0) {
            throw new BusinessException("扫描记录 ID 不能为空");
        }
        Scan scan = scanMapper.findById(scanId);
        if (scan == null) {
            throw new BusinessException(404, "扫描记录不存在");
        }
        return scan;
    }

    private void ensureAvailable() {
        if (!properties.isEnabled()) {
            throw new BusinessException("智能分类尚未启用，请设置 CLASSIFICATION_ENABLED=true");
        }
        if (!properties.isOcrConfigured()) {
            throw new BusinessException("未配置 OCR 可执行程序，请设置 CLASSIFICATION_OCR_EXECUTABLE");
        }
    }

    private String normalizeScope(String value) {
        String scope = value == null || value.isBlank()
                ? "UNCLASSIFIED"
                : value.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_SCOPES.contains(scope)) {
            throw new BusinessException("不支持的识别范围：" + scope);
        }
        return scope;
    }

    private BigDecimal defaultThreshold() {
        return normalizeThreshold(properties.getHighConfidence());
    }

    private BigDecimal normalizeThreshold(Double value) {
        double threshold = value == null ? properties.getHighConfidence() : value;
        threshold = Math.max(0D, Math.min(1D, threshold));
        return BigDecimal.valueOf(threshold).setScale(5, RoundingMode.HALF_UP);
    }

    private String normalizeOperator(String value) {
        return value == null || value.isBlank() ? "anonymous" : value.trim();
    }

    private String safeMessage(Exception exception) {
        String message = exception == null ? null : exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception == null ? "未知错误" : exception.getClass().getSimpleName();
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
