package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.controller.DataRelationController;
import com.zjcxph.imgapi.service.DataRelationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataRelationControllerTest {

    @Mock
    private DataRelationService dataRelationService;

    @InjectMocks
    private DataRelationController controller;

    @Test
    void overviewReturnsServicePayload() {
        Map<String, Object> overview = Map.of("healthScore", 99.5);
        when(dataRelationService.getOverview()).thenReturn(overview);

        Result<Map<String, Object>> result = controller.overview();

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isSameAs(overview);
        verify(dataRelationService).getOverview();
    }

    @Test
    void searchForwardsTypeValueAndLimit() {
        List<Map<String, Object>> rows = List.of(Map.of("id", 42L));
        when(dataRelationService.searchArchives("SJH", "00000123", 10)).thenReturn(rows);

        Result<List<Map<String, Object>>> result = controller.search("SJH", "00000123", 10);

        assertThat(result.getData()).isSameAs(rows);
        verify(dataRelationService).searchArchives("SJH", "00000123", 10);
    }

    @Test
    void archiveRelationReturnsCrossTableDetails() {
        Map<String, Object> detail = Map.of("readOnly", true);
        when(dataRelationService.getArchiveRelation(42L)).thenReturn(detail);

        Result<Map<String, Object>> result = controller.archiveRelation(42L);

        assertThat(result.getData()).isSameAs(detail);
        verify(dataRelationService).getArchiveRelation(42L);
    }
}
