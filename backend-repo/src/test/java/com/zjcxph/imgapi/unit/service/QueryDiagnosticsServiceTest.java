package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.service.QueryDiagnosticsService;
import com.zjcxph.imgapi.utils.AuthContext;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class QueryDiagnosticsServiceTest {
    @Test
    void nonUniqueBahWithoutShelfNumberNeverQueriesTheDatabase() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var result = new QueryDiagnosticsService(jdbc, new ConcurrentMapCacheManager()).diagnose("10000001", "");
        assertThat(result.toString()).contains("NEEDS_SJH").doesNotContain("10000001");
        verifyNoInteractions(jdbc);
    }

    @Test
    void distinguishesEmptySamplesWrongSchemaAndProbeFailure() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(PreparedStatementSetter.class), org.mockito.ArgumentMatchers.<RowMapper<Map<String, Object>>>any()))
                .thenAnswer(call -> {
                    String sql = call.getArgument(0);
                    if (sql.contains("current_schema")) return List.of(Map.of("expected_schema", false));
                    if (sql.contains("mr_scan")) throw new QueryTimeoutException("synthetic detail");
                    return List.of();
                });

        var report = new QueryDiagnosticsService(jdbc, new ConcurrentMapCacheManager()).diagnose("123", "");

        assertThat(step(report, "DATABASE")).containsEntry("status", "MISMATCH");
        assertThat(step(report, "ARCHIVE_EXACT")).containsEntry("status", "NO_SAMPLE");
        assertThat(step(report, "SCAN_EXACT")).containsEntry("status", "UNKNOWN");
        assertThat(step(report, "REQUEST_RECEIVED")).containsEntry("status", "NOT_CHECKED");
        assertThat(report.toString()).doesNotContain("synthetic detail");
    }

    @Test
    void missingLogSampleDoesNotClaimTheRequestArrived() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(PreparedStatementSetter.class), org.mockito.ArgumentMatchers.<RowMapper<Map<String, Object>>>any()))
                .thenReturn(List.of());
        var user = new AuthSession();
        user.setPermissions(List.of("log:read"));
        AuthContext.setCurrentUser(user);
        try {
            var report = new QueryDiagnosticsService(jdbc, new ConcurrentMapCacheManager())
                    .diagnose("", "0123", "0123456789abcdef");

            assertThat(step(report, "REQUEST_RECEIVED")).containsEntry("status", "NO_SAMPLE");
            assertThat(step(report, "REQUEST_RECEIVED").get("message").toString()).contains("请核对浏览器或 Nginx 请求记录");
            assertThat(report.toString()).doesNotContain("0123456789abcdef");
        } finally {
            AuthContext.clear();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> step(Map<String, Object> report, String code) {
        var steps = (List<Map<String, Object>>) report.get("steps");
        return steps.stream().filter(step -> code.equals(step.get("code"))).findFirst().orElseThrow();
    }
}
