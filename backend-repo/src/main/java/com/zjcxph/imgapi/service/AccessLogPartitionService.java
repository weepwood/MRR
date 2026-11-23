package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.config.AccessLogPartitionProperties;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.repository.AccessLogPartitionRepository;
import com.zjcxph.imgapi.setting.RuntimeSettingStore;
import com.zjcxph.imgapi.setting.SettingRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * access_log 按月分区的维护、历史复制与切换（ADR-0010）。
 *
 * <ul>
 *   <li>维护（每小时、启动时）：预建分区、把默认分区里的月份迁出、按访问日志保留期删除整月分区；</li>
 *   <li>复制（{@code app.access-log-partition.copy-enabled=true} 时）：按 id 升序分批复制旧表数据，可中断续跑；</li>
 *   <li>切换 / 回退 / 放弃：只由具备 {@code system:manage} 的运维人员手动触发，写入安全审计日志。</li>
 * </ul>
 */
@Service
public class AccessLogPartitionService {

    private static final Logger logger = LoggerFactory.getLogger(AccessLogPartitionService.class);
    private static final Logger securityAudit = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final String LOCK_NOT_AVAILABLE = "55P03";

    private final AccessLogPartitionRepository repository;
    private final AccessLogPartitionProperties properties;
    private final RuntimeSettingStore settingStore;
    private final Clock clock;
    private final AtomicBoolean copying = new AtomicBoolean(false);
    private final AtomicLong copyLag = new AtomicLong(-1);
    private final Counter copiedRows;

    @Autowired
    public AccessLogPartitionService(
            AccessLogPartitionRepository repository,
            AccessLogPartitionProperties properties,
            RuntimeSettingStore settingStore,
            MeterRegistry meterRegistry
    ) {
        this(repository, properties, settingStore, meterRegistry, Clock.systemDefaultZone());
    }

    AccessLogPartitionService(
            AccessLogPartitionRepository repository,
            AccessLogPartitionProperties properties,
            RuntimeSettingStore settingStore,
            MeterRegistry meterRegistry,
            Clock clock
    ) {
        this.repository = repository;
        this.properties = properties;
        this.settingStore = settingStore;
        this.clock = clock;
        this.copiedRows = Counter.builder("mrr.access_log.partition.copied.rows")
                .description("access_log 历史数据已复制到分区表的行数（本进程累计）")
                .register(meterRegistry);
        Gauge.builder("mrr.access_log.partition.copy.lag", copyLag, AtomicLong::get)
                .description("旧 access_log 尚未复制的 id 跨度；-1 表示未知或已切换")
                .register(meterRegistry);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void maintainOnStartup() {
        maintainQuietly();
    }

    @Scheduled(cron = "${app.access-log-partition.maintenance-cron:0 15 * * * ?}")
    public void scheduledMaintenance() {
        maintainQuietly();
    }

    @Scheduled(
            fixedDelayString = "${app.access-log-partition.copy-interval-ms:60000}",
            initialDelayString = "${app.access-log-partition.copy-initial-delay-ms:120000}"
    )
    public void scheduledCopy() {
        if (!properties.isCopyEnabled()) {
            return;
        }
        try {
            copyRound();
        } catch (RuntimeException e) {
            logger.warn("access_log partition copy round failed, will retry next round: {}", e.toString());
        }
    }

    /** 预建分区、迁出默认分区并删除过期整月分区。 */
    public Map<String, Object> maintain() {
        int created = repository.maintainPartitions(Math.max(0, properties.getMonthsAhead()));
        int dropped = 0;
        LocalDateTime cutoff = null;
        int retentionDays = settingStore.intValue(SettingRegistry.ACCESS_LOG_RETENTION_DAYS);
        if (settingStore.booleanValue(SettingRegistry.ACCESS_LOG_RETENTION_ENABLED) && retentionDays > 0) {
            cutoff = LocalDateTime.now(clock).minusDays(retentionDays);
            dropped = repository.dropExpiredPartitions(cutoff);
        }
        if (created > 0 || dropped > 0) {
            logger.info("access_log partition maintenance created={}, dropped={}, cutoff={}", created, dropped, cutoff);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("created", created);
        result.put("dropped", dropped);
        result.put("cutoff", cutoff);
        return result;
    }

    /**
     * 执行一轮复制：最多 {@code copy-max-batches-per-run} 批，追平或无新数据时提前结束。
     * 同一进程内不会并发执行两轮。
     */
    public Map<String, Object> copyRound() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!copying.compareAndSet(false, true)) {
            result.put("skipped", true);
            result.put("message", "上一轮复制仍在进行");
            return result;
        }
        try {
            int batchSize = Math.max(1, properties.getCopyBatchSize());
            int maxBatches = Math.max(1, properties.getCopyMaxBatchesPerRun());
            long copied = 0;
            int batches = 0;
            Map<String, Object> last = Map.of();
            while (batches < maxBatches) {
                last = repository.copyBatch(batchSize);
                batches++;
                long batchCopied = number(last.get("copied"));
                copied += batchCopied;
                copiedRows.increment(batchCopied);
                if (number(last.get("scanned")) == 0 || !"COPYING".equals(last.get("phase"))) {
                    break;
                }
            }
            updateLag(last);
            result.put("skipped", false);
            result.put("phase", last.get("phase"));
            result.put("batches", batches);
            result.put("copied", copied);
            result.put("cursorId", last.get("cursorId"));
            result.put("targetId", last.get("targetId"));
            if (copied > 0) {
                logger.info("access_log partition copy round phase={}, batches={}, copied={}, cursorId={}, targetId={}",
                        last.get("phase"), batches, copied, last.get("cursorId"), last.get("targetId"));
            }
            return result;
        } finally {
            copying.set(false);
        }
    }

    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>(repository.status());
        Object source = result.get("sourceMaxId");
        Object cursor = result.get("copyCursorId");
        Long lag = source instanceof Number s && cursor instanceof Number c ? Math.max(0L, s.longValue() - c.longValue()) : null;
        result.put("copyLag", lag);
        result.put("copyEnabled", properties.isCopyEnabled());
        result.put("copyBatchSize", properties.getCopyBatchSize());
        result.put("copyMaxBatchesPerRun", properties.getCopyMaxBatchesPerRun());
        copyLag.set(lag == null ? -1 : lag);
        return result;
    }

    public Map<String, Object> cutover(String actor) {
        return manualTransition("CUTOVER", actor, repository::cutover);
    }

    public Map<String, Object> rollback(String actor) {
        return manualTransition("ROLLBACK", actor, repository::rollback);
    }

    public Map<String, Object> abort(String actor) {
        return manualTransition("ABORT", actor, repository::abort);
    }

    private Map<String, Object> manualTransition(String action, String actor,
                                                 java.util.function.Supplier<Map<String, Object>> call) {
        try {
            Map<String, Object> result = call.get();
            // 固定操作名和结果留在日志模板中，操作者由 requestId 关联至受控访问日志。
            securityAudit.info("event=ACCESS_LOG_PARTITION_" + action + " result=SUCCESS actor={}", actor);
            if ("CUTOVER".equals(action)) {
                copyLag.set(-1);
            }
            return result;
        } catch (DataAccessException e) {
            securityAudit.warn("event=ACCESS_LOG_PARTITION_" + action + " result=REJECTED actor={}", actor);
            throw translate(e);
        }
    }

    private void maintainQuietly() {
        try {
            maintain();
        } catch (RuntimeException e) {
            logger.warn("access_log partition maintenance failed, will retry next hour: {}", e.toString());
        }
    }

    private void updateLag(Map<String, Object> last) {
        Object cursor = last.get("cursorId");
        Object target = last.get("targetId");
        if (cursor instanceof Number c && target instanceof Number t) {
            copyLag.set(Math.max(0L, t.longValue() - c.longValue()));
        }
    }

    static BusinessException translate(DataAccessException e) {
        String reason = reason(e);
        if (LOCK_NOT_AVAILABLE.equals(sqlState(e))) {
            return new BusinessException(409, "等待 access_log 表锁超时（5 秒），请在业务低峰重试", e);
        }
        if (reason.contains("ACCESS_LOG_PARTITION_NOT_READY")) {
            return new BusinessException(409, "历史数据尚未复制完成，暂不能切换：" + detail(e), e);
        }
        if (reason.contains("ACCESS_LOG_PARTITION_NOT_CUTOVER")) {
            return new BusinessException(409, "当前未处于已切换状态，无需回退：" + detail(e), e);
        }
        if (reason.contains("ACCESS_LOG_PARTITION_NOT_ABORTABLE")) {
            return new BusinessException(409, "只有切换前可以放弃分区迁移：" + detail(e), e);
        }
        return new BusinessException(500, "access_log 分区操作失败，请查看服务日志", e);
    }

    private static String sqlState(DataAccessException e) {
        Throwable cause = e.getMostSpecificCause();
        return cause instanceof SQLException sql ? sql.getSQLState() : null;
    }

    private static String reason(DataAccessException e) {
        String message = e.getMostSpecificCause().getMessage();
        return message == null ? "" : message;
    }

    private static String detail(DataAccessException e) {
        String message = reason(e);
        int index = message.indexOf("phase=");
        if (index < 0) {
            return "未知阶段";
        }
        int end = index + "phase=".length();
        while (end < message.length() && Character.isLetter(message.charAt(end))) {
            end++;
        }
        return message.substring(index, end);
    }

    private static long number(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }
}
