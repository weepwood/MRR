package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.service.impl.DataRelationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataRelationServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private DataRelationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DataRelationServiceImpl(jdbcTemplate);
    }

    @Test
    void searchesArchiveByNumericArchiveId() {
        List<Map<String, Object>> rows = List.of(Map.of("id", 42L));
        when(jdbcTemplate.queryForList(contains("WHERE id = ?"), eq(42L))).thenReturn(rows);

        List<Map<String, Object>> result = service.searchArchives("archive_id", "42", 20);

        assertThat(result).isSameAs(rows);
        verify(jdbcTemplate).queryForList(contains("WHERE id = ?"), eq(42L));
    }

    @Test
    void rejectsUnsupportedSearchTypeBeforeDatabaseAccess() {
        assertThatThrownBy(() -> service.searchArchives("patient_name", "张三", 20))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ARCHIVE_ID、BAH 或 SJH");
    }

    @Test
    void rejectsBlankSearchValue() {
        assertThatThrownBy(() -> service.searchArchives("BAH", "   ", 20))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("查询值不能为空");
    }

    @Test
    void rejectsNonNumericArchiveId() {
        assertThatThrownBy(() -> service.searchArchives("ARCHIVE_ID", "A-42", 20))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须是整数");
    }
}
