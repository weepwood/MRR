package com.zjcxph.imgapi.pojo;

import java.time.LocalDateTime;

public class LogRetentionCleanupResult {

    private boolean enabled;
    private boolean skipped;
    private boolean success;
    private String message;
    private int retentionDays;
    private int batchSize;
    private int maxBatchesPerRun;
    private LocalDateTime executedAt;
    private LocalDateTime cutoff;
    private int deleted;
    private int remainingOlderThanCutoff;
    private int batches;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isSkipped() {
        return skipped;
    }

    public void setSkipped(boolean skipped) {
        this.skipped = skipped;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxBatchesPerRun() {
        return maxBatchesPerRun;
    }

    public void setMaxBatchesPerRun(int maxBatchesPerRun) {
        this.maxBatchesPerRun = maxBatchesPerRun;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(LocalDateTime executedAt) {
        this.executedAt = executedAt;
    }

    public LocalDateTime getCutoff() {
        return cutoff;
    }

    public void setCutoff(LocalDateTime cutoff) {
        this.cutoff = cutoff;
    }

    public int getDeleted() {
        return deleted;
    }

    public void setDeleted(int deleted) {
        this.deleted = deleted;
    }

    public int getRemainingOlderThanCutoff() {
        return remainingOlderThanCutoff;
    }

    public void setRemainingOlderThanCutoff(int remainingOlderThanCutoff) {
        this.remainingOlderThanCutoff = remainingOlderThanCutoff;
    }

    public int getBatches() {
        return batches;
    }

    public void setBatches(int batches) {
        this.batches = batches;
    }
}
