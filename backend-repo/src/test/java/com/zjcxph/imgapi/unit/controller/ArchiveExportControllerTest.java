package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.controller.ArchiveExportController;
import com.zjcxph.imgapi.dto.req.BatchDownloadRequest;
import com.zjcxph.imgapi.dto.resp.ArchiveExportPlanResponse;
import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.service.ArchiveExportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchiveExportControllerTest {

    @Mock
    private ArchiveExportService archiveExportService;

    @InjectMocks
    private ArchiveExportController controller;

    @Test
    void plansSmallPartialPdfInBrowser() {
        when(archiveExportService.prepareArchive("00789508", ""))
                .thenReturn(export(3));

        Result<ArchiveExportPlanResponse> result = controller.planPdf("789508", null, 2);

        assertThat(result.getData().executionMode()).isEqualTo("CLIENT_PDF");
        assertThat(result.getData().wholeArchive()).isFalse();
    }

    @Test
    void plansWholeArchivePdfOnBackend() {
        when(archiveExportService.prepareArchive("00789508", ""))
                .thenReturn(export(3));

        Result<ArchiveExportPlanResponse> result = controller.planPdf("789508", null, 3);

        assertThat(result.getData().executionMode()).isEqualTo("BACKEND_STREAM");
        assertThat(result.getData().wholeArchive()).isTrue();
    }

    @Test
    void plansLargePartialPdfOnBackend() {
        when(archiveExportService.prepareArchive("00789508", ""))
                .thenReturn(export(30));

        Result<ArchiveExportPlanResponse> result = controller.planPdf("789508", null, 21);

        assertThat(result.getData().executionMode()).isEqualTo("BACKEND_STREAM");
        assertThat(result.getData().wholeArchive()).isFalse();
    }

    @Test
    void streamsWholeArchiveZip() {
        when(archiveExportService.prepareArchive("00789508", ""))
                .thenReturn(export(1));

        ResponseEntity<StreamingResponseBody> response = controller.downloadZip("789508", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("Content-Disposition")).contains("00789508.zip");
    }

    @Test
    void streamsWholeArchivePdf() {
        when(archiveExportService.prepareArchive("00789508", ""))
                .thenReturn(export(1));

        ResponseEntity<StreamingResponseBody> response = controller.downloadPdf("789508", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/pdf");
        assertThat(response.getHeaders().getFirst("Content-Disposition")).contains("00789508.pdf");
    }

    @Test
    void streamsSelectedImagesAsPdf() {
        BatchDownloadRequest request = new BatchDownloadRequest();
        request.setIds(List.of("1", "2"));
        when(archiveExportService.prepareBatch(List.of("1", "2"))).thenReturn(export(2));

        ResponseEntity<StreamingResponseBody> response = controller.downloadSelectedPdf(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/pdf");
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
