package com.zjcxph.imgapi.scheduler;

import com.zjcxph.imgapi.config.LogRetentionProperties;
import com.zjcxph.imgapi.mapper.LogMapper;
import com.zjcxph.imgapi.pojo.LogRetentionCleanupResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class LogRetentionCleaner {

    private static final Logger logger = LoggerFactory.getLogger(LogRetentionCleaner.class);

    private final LogMapper logMapper;
    private final LogRetentionProperties properties;

    public LogRetentionCleaner(LogMapper logMapper, LogRetentionProperties properties) {
        this.logMapper = logMapper;
        this.properties = properties;
    }

    @Scheduled(cron = "${app.log-retention.cron:0 30 2 * * ?}")
    public void cleanExpiredLogs() {
        performCleanup();
    }

    public LogRetentionCleanupResult cleanupNow() {
        return performCleanup();
    }

    private LogRetentionCleanupResult performCleanup() {
        LogRetentionCleanupResult result = new LogRetentionCleanupResult();
        result.setEnabled(properties.isEnabled());

        int retentionDays = properties.getRetentionDays();
        int batchSize = Math.max(1, properties.getBatchSize());
        int maxBatches = Math.max(1, properties.getMaxBatchesPerRun());
        result.setRetentionDays(retentionDays);
        result.setBatchSize(batchSize);
        result.setMaxBatchesPerRun(maxBatches);
        result.setExecutedAt(LocalDateTime.now());

        if (!properties.isEnabled()) {
            result.setSkipped(true);
            result.setSuccess(true);
            result.setMessage("log retention cleanup is disabled");
            return result;
        }

        if (retentionDays <= 0) {
            result.setSkipped(true);
            result.setSuccess(true);
            result.setMessage("retentionDays <= 0, cleanup skipped");
            logger.warn("access_log retention cleanup skipped because retentionDays <= 0");
            return result;
        }

        LocalDateTime cutoff = result.getExecutedAt().minusDays(retentionDays);
        result.setCutoff(cutoff);
        int totalDeleted = 0;
        int batches = 0;

        try {
            while (batches < maxBatches) {
                int deleted = logMapper.deleteOlderThan(cutoff, batchSize);
                if (deleted <= 0) {
                    break;
                }
                totalDeleted += deleted;
                batches++;
            }

            int remaining = logMapper.countOlderThan(cutoff);
            result.setDeleted(totalDeleted);
            result.setRemainingOlderThanCutoff(remaining);
            result.setBatches(batches);
            result.setSuccess(true);
            if (totalDeleted > 0) {
                result.setMessage("log retention cleanup finished, deleted " + totalDeleted + " rows");
                logger.info(
                        "access_log retention cleanup finished, cutoff={}, deleted={}, remainingOlderThanCutoff={}, batches={}",
                        cutoff,
                        totalDeleted,
                        remaining,
                        batches
                );
            } else {
                result.setMessage("no expired logs found");
                logger.debug("access_log retention cleanup found no expired logs, cutoff={}", cutoff);
            }

            if (remaining > 0 && batches >= maxBatches) {
                result.setMessage(result.getMessage() + ", more expired logs remain for the next run");
                logger.warn(
                        "access_log retention cleanup reached max batches, cutoff={}, remainingOlderThanCutoff={}, batchSize={}, maxBatches={}",
                        cutoff,
                        remaining,
                        batchSize,
                        maxBatches
                );
            }
        } catch (Exception ex) {
            result.setSuccess(false);
            result.setMessage("log retention cleanup failed: " + ex.getMessage());
            logger.error("access_log retention cleanup failed, cutoff={}", cutoff, ex);
        }

        return result;
    }
}
