package com.zjcxph.imgapi.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

@Data
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

}
