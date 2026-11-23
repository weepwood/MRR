package com.zjcxph.imgapi.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.zjcxph.imgapi.config.AccessLogPartitionProperties;
import com.zjcxph.imgapi.config.PrivateJsonLogEncoder;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.repository.AccessLogPartitionRepository;
import com.zjcxph.imgapi.setting.RuntimeSettingStore;
import com.zjcxph.imgapi.setting.SettingRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.UncategorizedSQLException;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("access_log 分区迁移服务（ADR-0010）")
class AccessLogPartitionServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 23, 17, 0);

    private AccessLogPartitionRepository repository;
    private AccessLogPartitionProperties properties;
    private RuntimeSettingStore settings;
    private SimpleMeterRegistry meters;
    private AccessLogPartitionService service;

    @BeforeEach
    void setUp() {
        repository = mock(AccessLogPartitionRepository.class);
        properties = new AccessLogPartitionProperties();
        settings = mock(RuntimeSettingStore.class);
        meters = new SimpleMeterRegistry();
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        service = new AccessLogPartitionService(repository, properties, settings, meters, clock);
    }

    @Test
    @DisplayName("复制默认关闭：定时任务不访问数据库")
    void scheduledCopyIsOptIn() {
        service.scheduledCopy();

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("一轮复制在无新数据时提前结束，并累计复制行数指标")
    void copyRoundStopsWhenSourceIsExhausted() {
        properties.setCopyBatchSize(100);
        properties.setCopyMaxBatchesPerRun(10);
        when(repository.copyBatch(100)).thenReturn(
                batch("COPYING", 100, 100, 100, 250),
                batch("COPYING", 100, 98, 200, 250),
                batch("READY", 50, 50, 250, 250));

        Map<String, Object> result = service.copyRound();

        verify(repository, times(3)).copyBatch(100);
        assertThat(result).containsEntry("phase", "READY").containsEntry("batches", 3).containsEntry("copied", 248L);
        assertThat(meters.get("mrr.access_log.partition.copied.rows").counter().count()).isEqualTo(248D);
        assertThat(meters.get("mrr.access_log.partition.copy.lag").gauge().value()).isZero();
    }

    @Test
    @DisplayName("一轮复制不超过单轮最大批次数")
    void copyRoundRespectsMaxBatches() {
        properties.setCopyBatchSize(10);
        properties.setCopyMaxBatchesPerRun(2);
        when(repository.copyBatch(10)).thenReturn(batch("COPYING", 10, 10, 10, 1000), batch("COPYING", 10, 10, 20, 1000));

        Map<String, Object> result = service.copyRound();

        verify(repository, times(2)).copyBatch(10);
        assertThat(result).containsEntry("batches", 2);
        assertThat(meters.get("mrr.access_log.partition.copy.lag").gauge().value()).isEqualTo(980D);
    }

    @Test
    @DisplayName("保留期开启时按保留天数删除整月分区")
    void maintenanceDropsExpiredMonthsWhenRetentionEnabled() {
        when(settings.booleanValue(SettingRegistry.ACCESS_LOG_RETENTION_ENABLED)).thenReturn(true);
        when(settings.intValue(SettingRegistry.ACCESS_LOG_RETENTION_DAYS)).thenReturn(1095);
        when(repository.maintainPartitions(2)).thenReturn(1);
        when(repository.dropExpiredPartitions(NOW.minusDays(1095))).thenReturn(2);

        Map<String, Object> result = service.maintain();

        assertThat(result).containsEntry("created", 1).containsEntry("dropped", 2)
                .containsEntry("cutoff", NOW.minusDays(1095));
    }

    @Test
    @DisplayName("保留期关闭或天数非正时只预建分区，不删除任何分区")
    void maintenanceNeverDropsWhenRetentionDisabled() {
        when(settings.booleanValue(SettingRegistry.ACCESS_LOG_RETENTION_ENABLED)).thenReturn(false);
        when(settings.intValue(SettingRegistry.ACCESS_LOG_RETENTION_DAYS)).thenReturn(1095);
        service.maintain();

        when(settings.booleanValue(SettingRegistry.ACCESS_LOG_RETENTION_ENABLED)).thenReturn(true);
        when(settings.intValue(SettingRegistry.ACCESS_LOG_RETENTION_DAYS)).thenReturn(0);
        service.maintain();

        verify(repository, times(2)).maintainPartitions(anyInt());
        verify(repository, never()).dropExpiredPartitions(any());
    }

    @Test
    @DisplayName("维护失败不向调度线程抛出，下一小时重试")
    void scheduledMaintenanceSwallowsFailures() {
        when(repository.maintainPartitions(anyInt())).thenThrow(new CannotAcquireLockException("synthetic"));

        service.scheduledMaintenance();

        verify(repository).maintainPartitions(2);
    }

    @Test
    @DisplayName("复制未完成时切换返回 409 与当前阶段")
    void cutoverBeforeReadyIsRejected() {
        when(repository.cutover()).thenThrow(sqlFailure("P0001",
                "ERROR: ACCESS_LOG_PARTITION_NOT_READY\n  Detail: phase=COPYING"));

        assertThatThrownBy(() -> service.cutover("admin"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未复制完成")
                .hasMessageContaining("phase=COPYING")
                .extracting("code").isEqualTo(409);
    }

    @Test
    @DisplayName("等待表锁超时返回 409，提示低峰重试")
    void lockTimeoutIsReportedAsConflict() {
        when(repository.rollback()).thenThrow(sqlFailure("55P03", "ERROR: canceling statement due to lock timeout"));

        assertThatThrownBy(() -> service.rollback("admin"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("低峰重试")
                .extracting("code").isEqualTo(409);
    }

    @Test
    void encodedAuditIdentifiesTransitionAndCorrelatesTheRequest() {
        when(repository.cutover()).thenReturn(Map.of("phase", "CUTOVER"));
        Logger audit = (Logger) LoggerFactory.getLogger("SECURITY_AUDIT");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        audit.addAppender(appender);
        try (MDC.MDCCloseable request = MDC.putCloseable("requestId", "synthetic-partition-request")) {
            service.cutover("synthetic-operator");
            assertThat(appender.list).singleElement().satisfies(event -> {
                String encoded = new String(new PrivateJsonLogEncoder().encode(event), StandardCharsets.UTF_8);
                assertThat(encoded).contains("ACCESS_LOG_PARTITION_CUTOVER", "result=SUCCESS",
                        "synthetic-partition-request").doesNotContain("synthetic-operator");
            });
        } finally {
            audit.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    @DisplayName("状态附带复制差距与配置")
    void statusReportsLag() {
        properties.setCopyEnabled(true);
        when(repository.status()).thenReturn(Map.of("phase", "COPYING", "sourceMaxId", 1500, "copyCursorId", 1000));

        Map<String, Object> status = service.status();

        assertThat(status).containsEntry("copyLag", 500L).containsEntry("copyEnabled", true);
    }

    private static Map<String, Object> batch(String phase, int scanned, int copied, long cursor, long target) {
        return Map.of("phase", phase, "scanned", scanned, "copied", copied, "cursorId", cursor, "targetId", target);
    }

    private static UncategorizedSQLException sqlFailure(String sqlState, String message) {
        return new UncategorizedSQLException("access_log partition", "SELECT 1", new SQLException(message, sqlState));
    }
}
