package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.service.impl.DataQualityServiceImpl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataQualityServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PlatformTransactionManager transactionManager;

    private SimpleMeterRegistry meterRegistry;
    private DataQualityServiceImpl service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new DataQualityServiceImpl(jdbcTemplate, meterRegistry, transactionManager);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "sampleLimit", 200);
        ReflectionTestUtils.setField(service, "retentionDays", 90);
        ReflectionTestUtils.setField(service, "statementTimeoutSeconds", 120);
    }

    @Test
    void configuredChecksUseBusinessRulesInsteadOfFixedEightDigitValidation() {
        Object checks = ReflectionTestUtils.getField(service, "checks");

        assertThat(String.valueOf(checks))
                .contains("SCAN_CODE_BLANK")
                .contains("HIGH_BAH_WITHOUT_SJH")
                .contains("SCAN_ARCHIVE_LINK_MISSING_ESTIMATED")
                .contains("STATISTICS_ARCHIVE_LINK_MISMATCH")
                .doesNotContain("SCAN_CODE_FORMAT_INVALID");
    }

    @Test
    void summaryWithoutCompletedRunReturnsStableEmptyShape() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());

        Map<String, Object> result = service.getSummary();

        assertThat(result)
                .containsEntry("running", false)
                .containsEntry("enabled", true)
                .containsEntry("latestRun", null);
        assertThat(result.get("checks")).isEqualTo(List.of());
    }

    @Test
    void summaryLoadsChecksForLatestRun() {
        Map<String, Object> latestRun = Map.of("id", 42L, "status", "SUCCESS", "total_issues", 3L);
        List<Map<String, Object>> checks = List.of(
                Map.of("check_code", "SCAN_CODE_BLANK", "severity", "CRITICAL", "issue_count", 3L)
        );
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(latestRun));
        when(jdbcTemplate.queryForList(anyString(), eq(42L))).thenReturn(checks);

        Map<String, Object> result = service.getSummary();

        assertThat(result.get("latestRun")).isSameAs(latestRun);
        assertThat(result.get("checks")).isSameAs(checks);
    }

    @Test
    void issuesReturnsEmptyListWhenNoRunExists() {
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Long>>any()))
                .thenReturn(List.of());

        List<Map<String, Object>> result = service.getIssues(100);

        assertThat(result).isEmpty();
        verify(jdbcTemplate, never()).queryForList(anyString(), any(Object[].class));
    }

    @Test
    void issuesClampsRequestedLimitToFiveHundred() {
        List<Map<String, Object>> issues = List.of(Map.of("id", 1L));
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Long>>any()))
                .thenReturn(List.of(7L));
        when(jdbcTemplate.queryForList(anyString(), eq(7L), eq(500))).thenReturn(issues);

        List<Map<String, Object>> result = service.getIssues(5_000);

        assertThat(result).isSameAs(issues);
        verify(jdbcTemplate).queryForList(anyString(), eq(7L), eq(500));
    }

    @Test
    void issuesClampsNonPositiveLimitToOne() {
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Long>>any()))
                .thenReturn(List.of(8L));
        when(jdbcTemplate.queryForList(anyString(), eq(8L), eq(1))).thenReturn(List.of());

        service.getIssues(0);

        verify(jdbcTemplate).queryForList(anyString(), eq(8L), eq(1));
    }

    @Test
    void issueLookupReturnsSelectedIssue() {
        Map<String, Object> issue = Map.of("id", 9L, "checkCode", "BOX_ARCHIVE_LINK_MISSING");
        when(jdbcTemplate.queryForList(anyString(), eq(9L))).thenReturn(List.of(issue));

        assertThat(service.getIssue(9L)).isSameAs(issue);
    }

    @Test
    void missingIssueReturnsNotFoundBusinessError() {
        when(jdbcTemplate.queryForList(anyString(), eq(9L))).thenReturn(List.of());

        assertThatThrownBy(() -> service.getIssue(9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("异常不存在");
    }

    @Test
    void scheduledRunDoesNothingWhenFeatureIsDisabled() {
        ReflectionTestUtils.setField(service, "enabled", false);

        service.scheduledRun();

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void scheduledRunUsesScheduledTriggerWhenEnabled() {
        DataQualityServiceImpl spyService = spy(service);
        doReturn(Map.of()).when(spyService).runChecks("scheduled");

        spyService.scheduledRun();

        verify(spyService).runChecks("scheduled");
    }

    @Test
    void concurrentRunIsRejectedBeforeDatabaseAccess() {
        AtomicBoolean running = (AtomicBoolean) ReflectionTestUtils.getField(service, "running");
        assertThat(running).isNotNull();
        running.set(true);

        assertThatThrownBy(() -> service.runChecks("manual"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("数据质量检查正在运行，请稍后重试");
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void failedRunIsRecordedAndAlwaysResetsRunningGauge() {
        when(jdbcTemplate.queryForObject(
                contains("INSERT INTO app.mrr_data_quality_run"), eq(Long.class), eq("manual")))
                .thenReturn(77L);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class)))
                .thenThrow(new DataAccessResourceFailureException("db offline"));

        assertThatThrownBy(() -> service.runChecks("manual"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("数据质量检查失败: db offline")
                .hasCauseInstanceOf(DataAccessResourceFailureException.class);

        verify(jdbcTemplate).update(
                contains("SET status = 'FAILED'"), eq("db offline"), eq(77L));
        assertThat(meterRegistry.get("mrr.data.quality.running").gauge().value()).isZero();
        AtomicBoolean running = (AtomicBoolean) ReflectionTestUtils.getField(service, "running");
        assertThat(running).isNotNull();
        assertThat(running.get()).isFalse();
    }

    @Test
    void metricInitializationDoesNotPreventApplicationStartupWhenDatabaseIsUnavailable() {
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Long>>any()))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        assertThatCode(service::initializeMetrics).doesNotThrowAnyException();
        assertThat(meterRegistry.get("mrr.data.quality.total.issues").gauge().value()).isZero();
    }

    @Test
    void everyCheckRunsWithDatabaseSideStatementTimeout() {
        stubRunId(101L);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);

        service.runChecks("manual");

        verify(jdbcTemplate, atLeastOnce()).execute(contains("SET LOCAL statement_timeout"));
    }

    @Test
    void statementTimeoutIsRecordedAsSkippedInsteadOfFailingWholeRun() {
        stubRunId(102L);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class)))
                .thenThrow(statementTimeout());

        Map<String, Object> result = service.runChecks("manual");

        // runChecks 返回的是执行中的快照（running 仍为 true），这里只断言不抛异常。
        assertThat(result).isNotNull();
        // 18 个检查项全部超时，每一项都应单独记为 SKIPPED，而不是让整轮失败。
        verify(jdbcTemplate, atLeastOnce()).update(
                contains("INSERT INTO app.mrr_data_quality_check_result"),
                eq(102L),
                anyString(),
                anyString(),
                eq("SKIPPED"),
                eq(0L),
                eq(0)
        );
        // 超时项在批次上留痕，避免被误读为"全部通过"。
        verify(jdbcTemplate).update(
                contains("SET status = 'SUCCESS'"),
                anyInt(),
                eq(0L),
                eq(0L),
                eq(0L),
                contains("超时未完成"),
                eq(102L)
        );
        assertThat(meterRegistry.get("mrr.data.quality.running").gauge().value()).isZero();
    }

    @Test
    void ordinaryCheckFailureStillFailsTheRun() {
        stubRunId(103L);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class)))
                .thenThrow(new UncategorizedSQLException(
                        "count",
                        "SELECT COUNT(*)",
                        new SQLException("relation does not exist", "42P01")
                ));

        assertThatThrownBy(() -> service.runChecks("manual"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("数据质量检查失败");

        verify(jdbcTemplate).update(contains("SET status = 'FAILED'"), anyString(), eq(103L));
    }

    @Test
    void successfulCheckKeepsDefinitionSeverity() {
        stubRunId(104L);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(2L);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        service.runChecks("manual");

        verify(jdbcTemplate, atLeastOnce()).update(
                contains("INSERT INTO app.mrr_data_quality_check_result"),
                eq(104L),
                anyString(),
                anyString(),
                eq("CRITICAL"),
                eq(2L),
                anyInt()
        );
    }

    private void stubRunId(long runId) {
        when(jdbcTemplate.queryForObject(
                contains("INSERT INTO app.mrr_data_quality_run"), eq(Long.class), eq("manual")))
                .thenReturn(runId);
    }

    private UncategorizedSQLException statementTimeout() {
        return new UncategorizedSQLException(
                "count",
                "SELECT COUNT(*)",
                new SQLException("canceling statement due to statement timeout", "57014")
        );
    }
}
