package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.controller.ImageController;
import com.zjcxph.imgapi.service.ArchiveAccessService;
import com.zjcxph.imgapi.service.ArchiveExportService;
import com.zjcxph.imgapi.service.ImageUrlService;
import com.zjcxph.imgapi.service.OssService;
import com.zjcxph.imgapi.service.ScanService;
import com.zjcxph.imgapi.storage.ImageStorage;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageControllerLookupTest {

    @Mock
    private ScanService scanService;
    @Mock
    private ArchiveExportService archiveExportService;
    @Mock
    private ImageStorage imageStorage;
    @Mock
    private OssService ossService;
    @Mock
    private ImageUrlService imageUrlService;
    @Mock
    private ArchiveAccessService archiveAccessService;
    @Mock
    private HttpServletRequest request;

    private ImageController controller;

    @BeforeEach
    void setUp() {
        controller = new ImageController(
                scanService,
                archiveExportService,
                imageStorage,
                ossService,
                imageUrlService,
                archiveAccessService
        );
    }

    @Test
    void usesBahBelowUniqueLimitEvenWhenSjhIsProvided() {
        when(scanService.getImageListByCode("09999999", "9999999", "", ""))
                .thenReturn(List.of());
        when(imageUrlService.toDtoList(anyList())).thenReturn(List.of());

        controller.searchByCode("9999999", "456", null, request);

        verify(archiveAccessService).verifyAndRecord(null, "09999999", "00000456", request);
        verify(scanService).getImageListByCode("09999999", "9999999", "", "");
    }

    @Test
    void usesSjhAtAndAboveUniqueLimit() {
        when(scanService.getImageListByCode("", "", "00000456", "456"))
                .thenReturn(List.of());
        when(imageUrlService.toDtoList(anyList())).thenReturn(List.of());

        controller.searchByCode("10000000", "456", null, request);

        verify(archiveAccessService).verifyAndRecord(null, "10000000", "00000456", request);
        verify(scanService).getImageListByCode("", "", "00000456", "456");
    }

    @Test
    void rejectsNonUniqueBahWithoutSjhBeforeQueryingImages() {
        controller.searchByCode("10000001", null, null, request);

        verify(scanService, never()).getImageListByCode(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
        verify(archiveAccessService, never()).verifyAndRecord(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
    }
}
