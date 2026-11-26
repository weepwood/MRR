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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.dao.QueryTimeoutException;
import java.sql.PreparedStatement;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

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
    void repairPreviewRejectsDeletedAndChangedRowsBeforeCandidateLookup() {
        var issue = Map.<String,Object>of("checkCode", "SCAN_ARCHIVE_LINK_MISSING_ESTIMATED",
                "entityType", "mr_scan", "entityId", "9", "bah", "1234");
        when(jdbcTemplate.queryForList(contains("JOIN app.mrr_data_quality_run"), eq(1L))).thenReturn(List.of(issue));
        when(jdbcTemplate.queryForList(contains("FROM app.mr_scan WHERE id"), eq(9L))).thenReturn(List.of());
        var missing = service.previewRepair(1L);
        assertThat(missing).containsEntry("deterministic", false).containsEntry("canApply", false);
        verify(jdbcTemplate, never()).queryForList(contains("WITH input AS"), (Object) any(), (Object) any());
        var changed = new java.util.HashMap<String,Object>();
        changed.put("entityId", 9L);
        changed.put("bah", "1235");
        changed.put("uploadFlag", 1);
        when(jdbcTemplate.queryForList(contains("FROM app.mr_scan WHERE id"), eq(9L))).thenReturn(List.of(changed));
        assertThat(service.previewRepair(1L)).containsEntry("deterministic", false).containsEntry("canApply", false);
        verify(jdbcTemplate, never()).queryForList(contains("WITH input AS"), (Object) any(), (Object) any());
    }

    @Test
    void highBahWithoutSjhAndInactiveScanCannotBeDeterministic() {
        var issue = Map.<String,Object>of("checkCode", "SCAN_ARCHIVE_LINK_MISSING_ESTIMATED",
                "entityType", "mr_scan", "entityId", "9", "bah", "10000000");
        when(jdbcTemplate.queryForList(contains("JOIN app.mrr_data_quality_run"), eq(1L))).thenReturn(List.of(issue));
        when(jdbcTemplate.queryForList(contains("FROM app.mr_scan WHERE id"), eq(9L)))
                .thenReturn(List.of(Map.of("entityId", 9L, "bah", "10000000", "uploadFlag", 1)));
        assertThat(service.previewRepair(1L)).containsEntry("deterministic", false);
        verify(jdbcTemplate, never()).queryForList(contains("WITH input AS"), (Object) any(), (Object) any());
    }

    @Test
    void currentUniqueCandidateRemainsReadOnlyAndConflictingBahIsNotDeterministic() {
        var issue = Map.<String,Object>of("checkCode", "SCAN_ARCHIVE_LINK_MISSING_ESTIMATED",
                "entityType", "mr_scan", "entityId", "9", "bah", "10000000", "sjh", "1234");
        when(jdbcTemplate.queryForList(contains("JOIN app.mrr_data_quality_run"), eq(1L))).thenReturn(List.of(issue));
        when(jdbcTemplate.queryForList(contains("FROM app.mr_scan WHERE id"), eq(9L)))
                .thenReturn(List.of(Map.of("entityId", 9L, "bah", "10000000", "sjh", "1234", "uploadFlag", 1)));
        when(jdbcTemplate.queryForList(contains("WITH input AS"), eq("10000000"), eq("1234")))
                .thenReturn(List.of(Map.of("id", 7L, "bah", "10000000", "sjh", "1234")))
                .thenReturn(List.of(Map.of("id", 7L, "bah", "10000001", "sjh", "1234")));
        assertThat(service.previewRepair(1L)).containsEntry("deterministic", true).containsEntry("canApply", false);
        assertThat(service.previewRepair(1L)).containsEntry("deterministic", false).containsEntry("canApply", false);
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
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
        when(jdbcTemplate.queryForList(contains("SELECT id FROM app.mrr_data_quality_run"), eq(100)))
                .thenReturn(List.of());
        assertThat(service.getIssues(100)).isEmpty();
        verify(jdbcTemplate).queryForList(contains("SELECT id FROM app.mrr_data_quality_run"), eq(100));
        verify(jdbcTemplate, never()).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Long>>any());
    }

    @Test
    void issuesClampsRequestedLimitToFiveHundred() {
        List<Map<String, Object>> issues = List.of(Map.of("id", 1L));
        when(jdbcTemplate.queryForList(contains("SELECT id FROM app.mrr_data_quality_run"), eq(500)))
                .thenReturn(issues);
        assertThat(service.getIssues(5_000)).isSameAs(issues);
        verify(jdbcTemplate).queryForList(contains("SELECT id FROM app.mrr_data_quality_run"), eq(500));
    }

    @Test
    void issuesClampsNonPositiveLimitToOne() {
        when(jdbcTemplate.queryForList(contains("SELECT id FROM app.mrr_data_quality_run"), eq(1)))
                .thenReturn(List.of());
        service.getIssues(0);
        verify(jdbcTemplate).queryForList(contains("SELECT id FROM app.mrr_data_quality_run"), eq(1));
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
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.PreparedStatementSetter>any(),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.ResultSetExtractor<Long>>any()))
                .thenThrow(new DataAccessResourceFailureException("db offline"));

        assertThatThrownBy(() -> service.runChecks("manual"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("数据质量检查失败").hasMessageNotContaining("db offline")
                .hasCauseInstanceOf(DataAccessResourceFailureException.class);

        verify(jdbcTemplate).update(
                contains("SET status = 'FAILED'"), eq("DataAccessResourceFailureException"), eq(77L));
        assertThat(meterRegistry.get("mrr.data.quality.running").gauge().value()).isZero();
        AtomicBoolean running = (AtomicBoolean) ReflectionTestUtils.getField(service, "running");
        assertThat(running).isNotNull();
        assertThat(running.get()).isFalse();
    }

    @Test
    void sampleUsesBoundedStatementAndLeavesSharedTemplateUnchanged() throws Exception {
        ReflectionTestUtils.setField(service, "sampleLimit", 5000);
        when(jdbcTemplate.queryForObject(contains("INSERT INTO app.mrr_data_quality_run"), eq(Long.class), eq("manual")))
                .thenReturn(77L);
        when(jdbcTemplate.query(anyString(), any(PreparedStatementSetter.class),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.ResultSetExtractor<Long>>any())).thenReturn(1L);
        doAnswer(invocation -> {
            PreparedStatement statement = mock(PreparedStatement.class);
            invocation.<PreparedStatementSetter>getArgument(1).setValues(statement);
            verify(statement).setQueryTimeout(10);
            verify(statement).setInt(1, 1000);
            throw new QueryTimeoutException("synthetic timeout");
        }).when(jdbcTemplate).query(contains(" LIMIT ?"), any(PreparedStatementSetter.class),
                org.mockito.ArgumentMatchers.<RowMapper<Map<String, Object>>>any());

        assertThatThrownBy(() -> service.runChecks("manual")).hasCauseInstanceOf(QueryTimeoutException.class);

        verify(jdbcTemplate, never()).setQueryTimeout(org.mockito.ArgumentMatchers.anyInt());
        verify(jdbcTemplate).update(contains("SET status = 'FAILED'"), eq("QueryTimeoutException"), eq(77L));
        assertThat(meterRegistry.get("mrr.data.quality.running").gauge().value()).isZero();
    }

    @Test
    void retentionFailureMustNotRewriteSuccessfulRun() {
        ReflectionTestUtils.setField(service, "checks", List.of());
        when(jdbcTemplate.queryForObject(contains("INSERT INTO app.mrr_data_quality_run"), eq(Long.class), eq("manual")))
                .thenReturn(77L);
        when(jdbcTemplate.update(contains("SET status = 'SUCCESS'"), eq(0), eq(0L), eq(0L), eq(0L), eq(77L))).thenReturn(1);
        when(jdbcTemplate.update(contains("DELETE FROM app.mrr_data_quality_run"), eq(90)))
                .thenThrow(new DataAccessResourceFailureException("synthetic retention failure"));
        assertThatCode(() -> service.runChecks("manual")).doesNotThrowAnyException();
        verify(jdbcTemplate, never()).update(contains("SET status = 'FAILED'"), anyString(), eq(77L));
        assertThat(meterRegistry.get("mrr.data.quality.running").gauge().value()).isZero();
    }

    @Test
    void metricInitializationDoesNotPreventApplicationStartupWhenDatabaseIsUnavailable() {
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Long>>any()))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        assertThatCode(service::initializeMetrics).doesNotThrowAnyException();
        assertThat(meterRegistry.get("mrr.data.quality.total.issues").gauge().value()).isZero();
    }
}
