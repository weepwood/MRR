package com.zjcxph.imgapi.scheduler;

import com.zjcxph.imgapi.config.LogRetentionProperties;
import com.zjcxph.imgapi.mapper.LogMapper;
import com.zjcxph.imgapi.dto.resp.LogRetentionCleanupResult;
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

    /**
     * 定时清理过期日志的调度任务。
     * <p>
     * 该方法由 Spring 调度器按照配置的 cron 表达式自动执行（默认每天凌晨 2:30 执行）。
     * 它会根据配置的保留天数自动删除超过保留期限的访问日志记录。
     * </p>
     *
     * @see #performCleanup(LocalDateTime, boolean) 实际执行清理逻辑的方法
     */
    @Scheduled(cron = "${app.log-retention.cron:0 30 2 * * ?}")
    public void cleanExpiredLogs() {
        performCleanup(null, false);
    }

    public LogRetentionCleanupResult cleanupNow() {
        return performCleanup(null, true);
    }

    public LogRetentionCleanupResult cleanupNow(LocalDateTime cutoff) {
        return performCleanup(cutoff, true);
    }

    /**
     * 执行日志清理的核心方法。
     * <p>
     * 该方法根据配置的保留天数，分批删除超过保留期限的访问日志记录。
     * 支持手动触发（forceWhenDisabled=true）即使在功能禁用状态下也能执行。
     * 采用分批删除策略以避免长时间锁定数据库表，每批删除的数量和最大批次数均可配置。
     * </p>
     *
     * @param providedCutoff 自定义的截止时间点。如果为 null，则根据当前时间和保留天数自动计算；
     *                       如果不为 null，则删除该时间点之前的所有日志
     * @param forceWhenDisabled 是否在功能禁用时强制执行。true 表示即使配置中禁用了清理功能也会执行；
     *                          false 表示遵循配置中的启用/禁用状态
     * @return LogRetentionCleanupResult 包含清理执行结果的详细信息，包括：
     *         - enabled: 功能是否启用
     *         - skipped: 是否被跳过执行
     *         - success: 执行是否成功
     *         - message: 执行结果消息
     *         - retentionDays: 配置的保留天数
     *         - batchSize: 每批删除的记录数
     *         - maxBatchesPerRun: 单次执行的最大批次数
     *         - executedAt: 执行时间
     *         - cutoff: 用于判断日志过期的截止时间点
     *         - deleted: 实际删除的记录总数
     *         - remainingOlderThanCutoff: 截止时间点之前仍剩余的记录数
     *         - batches: 实际执行的批次数量
     */
    private LogRetentionCleanupResult performCleanup(LocalDateTime providedCutoff, boolean forceWhenDisabled) {
        // 初始化结果对象并填充基本配置信息
        LogRetentionCleanupResult result = new LogRetentionCleanupResult();
        result.setEnabled(properties.isEnabled());

        int retentionDays = properties.getRetentionDays();
        int batchSize = Math.max(1, properties.getBatchSize());
        int maxBatches = Math.max(1, properties.getMaxBatchesPerRun());
        result.setRetentionDays(retentionDays);
        result.setBatchSize(batchSize);
        result.setMaxBatchesPerRun(maxBatches);
        result.setExecutedAt(LocalDateTime.now());

        // 检查功能是否被禁用且未设置强制执行
        if (!properties.isEnabled() && !forceWhenDisabled) {
            result.setSkipped(true);
            result.setSuccess(true);
            result.setMessage("log retention cleanup is disabled");
            return result;
        }

        // 验证保留天数的有效性
        if (retentionDays <= 0) {
            result.setSkipped(true);
            result.setSuccess(true);
            result.setMessage("retentionDays <= 0, cleanup skipped");
            logger.warn("access_log retention cleanup skipped because retentionDays <= 0");
            return result;
        }

        // 计算清理截止时间点
        LocalDateTime cutoff = providedCutoff != null ? providedCutoff : result.getExecutedAt().minusDays(retentionDays);
        result.setCutoff(cutoff);
        int totalDeleted = 0;
        int batches = 0;

        try {
            // 分批删除过期日志，避免长时间锁表
            while (batches < maxBatches) {
                int deleted = logMapper.deleteOlderThan(cutoff, batchSize);
                if (deleted <= 0) {
                    break;
                }
                totalDeleted += deleted;
                batches++;
            }

            // 统计剩余过期记录数并填充执行结果
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

            // 如果还有剩余过期记录但已达到最大批次数，记录警告信息
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
            // 捕获异常并记录失败信息
            result.setSuccess(false);
            result.setMessage("log retention cleanup failed: " + ex.getMessage());
            logger.error("access_log retention cleanup failed, cutoff={}", cutoff, ex);
        }

        return result;
    }
}
