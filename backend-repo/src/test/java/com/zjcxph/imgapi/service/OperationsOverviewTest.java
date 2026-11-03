package com.zjcxph.imgapi.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OperationsOverviewTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final DeploymentReadinessService readiness = mock(DeploymentReadinessService.class);
    private final MaintenanceModeService maintenance = mock(MaintenanceModeService.class);
    private final OperationsCenterService service = new OperationsCenterService(
            jdbc, readiness, maintenance, mock(IntegrityDiagnosticsService.class),
            mock(OperationsDiagnosticsService.class));

    @BeforeEach
    void setUp() {
        when(readiness.getSnapshot()).thenReturn(Map.of(
                "ready", false, "readOnly", true, "checkedAt", "2026-11-02T01:00:00Z",
                "checks", List.of(Map.of("code", "database", "name", "数据库连接", "severity", "CRITICAL",
                        "passed", false, "message", "synthetic raw exception",
                        "details", Map.of("path", "synthetic private path")))));
        when(maintenance.getStatus()).thenReturn(Map.of("enabled", true, "reason", "synthetic private reason"));
        when(maintenance.isEnabled()).thenReturn(true);
    }

    @Test
    void overviewReturnsSafeSummaryWithBothReadOnlyReasons() {
        when(jdbc.queryForList(anyString(), eq(1))).thenReturn(List.of(
                Map.of("error_message", "synthetic raw audit", "request_uri", "/private")));
        when(jdbc.queryForList(anyString())).thenReturn(List.of());
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);

        var result = service.overview();

        assertThat(result.toString()).contains("database", "automaticReadOnly=true", "maintenanceReadOnly=true")
                .doesNotContain("synthetic", "/private", "error_message");
        assertThat(result.get("recentServerErrorsAvailable")).isEqualTo(true);
        assertThat(((Map<?, ?>) result.get("taskSummary")).get("available")).isEqualTo(true);
        verify(readiness, never()).refreshSnapshot();
    }

    @Test
    void unavailableCountsAreDistinguishedFromSuccessfulZero() {
        var failure = new DataAccessResourceFailureException("synthetic database failure");
        when(jdbc.queryForList(anyString())).thenThrow(failure);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenThrow(failure);

        var result = service.overview();

        assertThat(result.get("recentServerErrors")).isEqualTo(0L);
        assertThat(result.get("recentServerErrorsAvailable")).isEqualTo(false);
        assertThat(((Map<?, ?>) result.get("taskSummary")).get("available")).isEqualTo(false);
        assertThat(result.toString()).doesNotContain(failure.getMessage());
    }
}
