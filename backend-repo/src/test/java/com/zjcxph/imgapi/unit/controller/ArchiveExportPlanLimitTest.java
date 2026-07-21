package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.controller.ArchiveExportController;
import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.service.ArchiveExportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchiveExportPlanLimitTest {

    @Mock
    private ArchiveExportService archiveExportService;

    @InjectMocks
    private ArchiveExportController controller;

    @Test
    void rejectsPartialSelectionBeyondCurrentBackendLimit() {
        when(archiveExportService.prepareArchive("00789508", ""))
                .thenReturn(export(300));

        assertThatThrownBy(() -> controller.planPdf("789508", null, 201))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("部分选择最多导出 200 张");
    }

    @Test
    void allowsWholeArchiveBeyondSelectionLimit() {
        when(archiveExportService.prepareArchive("00789508", ""))
                .thenReturn(export(300));

        var result = controller.planPdf("789508", null, 300);

        org.assertj.core.api.Assertions.assertThat(result.getData().wholeArchive()).isTrue();
        org.assertj.core.api.Assertions.assertThat(result.getData().executionMode()).isEqualTo("BACKEND_STREAM");
    }

    private ArchiveExportService.BatchZipExport export(int count) {
        return new ArchiveExportService.BatchZipExport(
                IntStream.rangeClosed(1, count)
                        .mapToObj(index -> new PathDO(
                                "25.03.15",
                                "page-" + index + ".png",
                                "605746",
                                "00789508"
                        ))
                        .toList()
        );
    }
}
