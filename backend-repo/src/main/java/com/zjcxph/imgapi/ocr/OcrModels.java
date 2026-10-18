package com.zjcxph.imgapi.ocr;

import lombok.Data;
import java.time.LocalDateTime;

public final class OcrModels {
    private OcrModels() {}
    public record Config(String profile, String fingerprint, int timeout, int maxOutput, double lowConfidence) {}
    public record Create(String idempotencyKey, Integer scanId) {}
    public record JobView(String id, Long archiveId, String status, int plannedCount, int processedCount,
                          int ocrSuccessCount, int failedCount, boolean cancelRequested) {
        public static JobView from(Job job) {
            return new JobView(job.id, job.archiveId, job.status, job.plannedCount, job.processedCount,
                    job.ocrSuccessCount, job.failedCount, job.cancelRequested);
        }
    }
    public record FactView(Integer scanId, Long archiveId, String status, Double confidence, String resultVersion,
                           String contentHash, String engineVersion, LocalDateTime processedAt, String text) {}
    @Data public static class Job {
        private String id; private Long archiveId; private Long ownerUserId; private String createdBy;
        private String status; private String idempotencyKey; private Integer selectedScanId;
        private String configSnapshot; private String leaseToken; private LocalDateTime leaseUntil;
        private String ruleSetVersion; private String lexiconVersion;
        private int plannedCount; private int processedCount; private int ocrSuccessCount; private int failedCount;
        private boolean cancelRequested;
    }
    @Data public static class Item {
        private String jobId; private Integer scanId; private int originalType;
        private String status; private int attempts; private String failureCode;
        private String ocrResultVersion; private Integer suggestedType; private Double ruleScore;
        private Double ocrConfidence;
    }
    @Data public static class Fact {
        private Integer scanId; private Long archiveId; private String status; private String engineVersion;
        private String resultVersion; private String modelChecksum; private String preprocessingVersion;
        private String ocrText; private String linesJson; private Double confidence; private String contentHash;
        private Integer imageWidth; private Integer imageHeight; private LocalDateTime processedAt;
        public FactView view(boolean full) {
            return new FactView(scanId, archiveId, status, confidence, resultVersion, contentHash,
                    engineVersion, processedAt, full ? ocrText : null);
        }
    }
}
