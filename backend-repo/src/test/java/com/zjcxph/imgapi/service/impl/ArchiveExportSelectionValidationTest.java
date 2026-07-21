package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.service.ScanService;
import com.zjcxph.imgapi.storage.ImageStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchiveExportSelectionValidationTest {

    @Mock
    private ScanService scanService;

    @Mock
    private ImageStorage imageStorage;

    @Test
    void rejectsDuplicateScanIdsBeforeQueryingDatabase() {
        ArchiveExportServiceImpl service = new ArchiveExportServiceImpl(scanService, imageStorage);

        assertThatThrownBy(() -> service.prepareSelectedArchive(List.of("1", "1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("重复影像 ID");
        verifyNoInteractions(scanService);
    }

    @Test
    void separatesHighBahArchivesBySjh() {
        when(scanService.findActiveByIds(List.of(1, 2))).thenReturn(List.of(
                scan(1, 11L, "10000000", "00000001"),
                scan(2, 12L, "10000000", "00000002")
        ));
        ArchiveExportServiceImpl service = new ArchiveExportServiceImpl(scanService, imageStorage);

        assertThatThrownBy(() -> service.prepareSelectedArchive(List.of("1", "2")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("同一份病案");
    }

    private Scan scan(int id, Long archiveId, String bah, String sjh) {
        Scan scan = new Scan();
        scan.setId(id);
        scan.setArchiveId(archiveId);
        scan.setBah(bah);
        scan.setSjh(sjh);
        scan.setBrxh("605746");
        scan.setFolder("25.03.15");
        scan.setFilename("page-" + id + ".jpg");
        scan.setPages(id);
        scan.setUploadFlag(1);
        return scan;
    }
}
