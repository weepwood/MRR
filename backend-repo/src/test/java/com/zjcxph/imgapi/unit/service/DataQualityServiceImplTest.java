package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.service.impl.DataQualityServiceImpl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataQualityServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private SimpleMeterRegistry meterRegistry;
    private DataQualityServiceImpl service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new DataQualityServiceImpl(jdbcTemplate, meterRegistry);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "sampleLimit", 200);
        ReflectionTestUtils.setField(service, "retentionDays", 90);
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
                Map.of("check_code", "SCAN_BAH_MISSING", "severity", "CRITICAL", "issue_count", 3L)
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
                contains("INSERT INTO mrr_data_quality_run"), eq(Long.class), eq("manual")))
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
}
