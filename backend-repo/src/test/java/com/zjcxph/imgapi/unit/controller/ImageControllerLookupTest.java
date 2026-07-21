package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.controller.ImageController;
import com.zjcxph.imgapi.dto.resp.ArchiveLookupResult;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.service.ArchiveAccessService;
import com.zjcxph.imgapi.service.ArchiveExportService;
import com.zjcxph.imgapi.service.ImageUrlService;
import com.zjcxph.imgapi.service.OssService;
import com.zjcxph.imgapi.service.ScanService;
import com.zjcxph.imgapi.storage.ImageStorage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.zjcxph.imgapi.dto.resp.ArchiveLookupResult.FallbackReason.ARCHIVE_NOT_FOUND;
import static com.zjcxph.imgapi.dto.resp.ArchiveLookupResult.Strategy.ARCHIVE_ID_EXACT;
import static com.zjcxph.imgapi.dto.resp.ArchiveLookupResult.Strategy.MR_SCAN_FALLBACK;
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
    @Mock
    private HttpServletResponse response;

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
        ArchiveLookupResult lookupResult = ArchiveLookupResult.notFound(
                ARCHIVE_NOT_FOUND,
                null
        );
        when(scanService.getImageLookupByCode("09999999", "9999999", "", ""))
                .thenReturn(lookupResult);
        when(imageUrlService.toDtoList(anyList())).thenReturn(List.of());

        controller.searchByCode("9999999", "456", null, request, response);

        verify(archiveAccessService).verifyAndRecord(null, "09999999", "00000456", request);
        verify(scanService).getImageLookupByCode("09999999", "9999999", "", "");
        verify(response).setHeader("X-MRR-Lookup-Strategy", "NOT_FOUND");
        verify(response).setHeader("X-MRR-Fallback-Reason", "ARCHIVE_NOT_FOUND");
        verify(response).setHeader("X-MRR-Image-Count", "0");
    }

    @Test
    void usesSjhAtAndAboveUniqueLimit() {
        Scan scan = new Scan();
        scan.setArchiveId(42L);
        ArchiveLookupResult lookupResult = ArchiveLookupResult.fastPath(
                List.of(scan),
                ARCHIVE_ID_EXACT,
                42L
        );
        when(scanService.getImageLookupByCode("", "", "00000456", "456"))
                .thenReturn(lookupResult);
        when(imageUrlService.toDtoList(anyList())).thenReturn(List.of());

        controller.searchByCode("10000000", "456", null, request, response);

        verify(archiveAccessService).verifyAndRecord(null, "10000000", "00000456", request);
        verify(scanService).getImageLookupByCode("", "", "00000456", "456");
        verify(response).setHeader("X-MRR-Lookup-Strategy", "ARCHIVE_ID_EXACT");
        verify(response).setHeader("X-MRR-Fallback-Reason", "NONE");
        verify(response).setHeader("X-MRR-Archive-Id", "42");
        verify(response).setHeader("X-MRR-Image-Count", "1");
    }

    @Test
    void exposesFallbackMetadataForLegacyBahEndpoint() {
        Scan scan = new Scan();
        ArchiveLookupResult lookupResult = ArchiveLookupResult.fallback(
                List.of(scan),
                ARCHIVE_NOT_FOUND,
                null
        );
        when(scanService.getImageLookupByBAH("00789508", "789508"))
                .thenReturn(lookupResult);
        when(imageUrlService.toDtoList(anyList())).thenReturn(List.of());

        controller.getDataByBAH("789508", response);

        verify(response).setHeader("X-MRR-Lookup-Strategy", MR_SCAN_FALLBACK.name());
        verify(response).setHeader("X-MRR-Fallback-Reason", "ARCHIVE_NOT_FOUND");
        verify(response).setHeader("X-MRR-Image-Count", "1");
    }

    @Test
    void rejectsNonUniqueBahWithoutSjhBeforeQueryingImages() {
        controller.searchByCode("10000001", null, null, request, response);

        verify(scanService, never()).getImageLookupByCode(
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
