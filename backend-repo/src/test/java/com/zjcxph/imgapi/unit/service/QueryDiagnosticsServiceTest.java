package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.service.QueryDiagnosticsService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class QueryDiagnosticsServiceTest {
    @Test void nonUniqueBahWithoutShelfNumberNeverQueriesTheDatabase() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var result = new QueryDiagnosticsService(jdbc, new ConcurrentMapCacheManager()).diagnose("10000001", "");
        assertThat(result.toString()).contains("NEEDS_SJH").doesNotContain("10000001");
        verifyNoInteractions(jdbc);
    }
}
