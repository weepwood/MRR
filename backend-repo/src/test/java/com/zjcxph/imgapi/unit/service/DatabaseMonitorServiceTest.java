package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.service.DatabaseMonitorService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseMonitorServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final DatabaseMonitorService service = new DatabaseMonitorService(jdbc, mock(DataSource.class));

    @Test
    void boundedProjectionNeverReturnsRawQueryText() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        when(jdbc.query(anyString(), any(PreparedStatementSetter.class),
                org.mockito.ArgumentMatchers.<RowMapper<Map<String, Object>>>any())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            assertThat(sql).contains("queryid::text AS query_id", "'[SQL 已隐藏]' AS query_template",
                    "ORDER BY total_exec_time DESC", "LIMIT ?")
                    .doesNotContain("REGEXP_REPLACE(query", "LEFT(query");
            call.<PreparedStatementSetter>getArgument(1).setValues(statement);
            return List.of(Map.of("query_id", "123", "query_template", "[SQL 已隐藏]", "calls", 3L));
        });
        assertThat(service.getSlowQueries(1000)).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("query_template", "[SQL 已隐藏]"));
        verify(statement).setInt(1, 100);
        verify(statement).setQueryTimeout(3);
    }

    @Test
    void emptySuccessfulCollectionRemainsEmpty() {
        when(jdbc.query(anyString(), any(PreparedStatementSetter.class),
                org.mockito.ArgumentMatchers.<RowMapper<Map<String, Object>>>any())).thenReturn(List.of());
        assertThat(service.getSlowQueries(20)).isEmpty();
    }

    @Test
    void collectionFailureIsNotAnEmptySuccessOrRawException() {
        when(jdbc.query(anyString(), any(PreparedStatementSetter.class),
                org.mockito.ArgumentMatchers.<RowMapper<Map<String, Object>>>any()))
                .thenThrow(new QueryTimeoutException("synthetic-private-detail"));
        assertThatThrownBy(() -> service.getSlowQueries(20))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(503);
                    assertThat(e.getReasonCode()).isEqualTo("SLOW_QUERY_STATS_UNAVAILABLE");
                    assertThat(e.getMessage()).doesNotContain("synthetic-private-detail");
                });
    }
}
