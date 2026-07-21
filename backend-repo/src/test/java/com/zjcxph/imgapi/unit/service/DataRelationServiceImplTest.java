package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.repository.DataRelationRepository;
import com.zjcxph.imgapi.service.impl.DataRelationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataRelationServiceImplTest {

    @Mock
    private DataRelationRepository repository;

    private DataRelationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DataRelationServiceImpl(repository);
    }

    @Test
    void searchesArchiveByNumericArchiveId() {
        List<Map<String, Object>> rows = List.of(Map.of("id", 42L));
        when(repository.searchByArchiveId(42L)).thenReturn(rows);

        List<Map<String, Object>> result = service.searchArchives("archive_id", "42", 20);

        assertThat(result).isSameAs(rows);
        verify(repository).searchByArchiveId(42L);
    }

    @Test
    void searchesCodeWithClampedLimit() {
        when(repository.searchByCode("sjh", "00000123", 50)).thenReturn(List.of());

        service.searchArchives("SJH", "00000123", 5_000);

        verify(repository).searchByCode("sjh", "00000123", 50);
    }

    @Test
    void rejectsUnsupportedSearchTypeBeforeDatabaseAccess() {
        assertThatThrownBy(() -> service.searchArchives("patient_name", "张三", 20))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ARCHIVE_ID、BAH 或 SJH");
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsBlankSearchValue() {
        assertThatThrownBy(() -> service.searchArchives("BAH", "   ", 20))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("查询值不能为空");
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsNonNumericArchiveId() {
        assertThatThrownBy(() -> service.searchArchives("ARCHIVE_ID", "A-42", 20))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须是整数");
        verifyNoInteractions(repository);
    }
}
