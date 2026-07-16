package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.controller.ImageController;
import com.zjcxph.imgapi.dto.req.ImageRequest;
import com.zjcxph.imgapi.service.ArchiveAccessService;
import com.zjcxph.imgapi.service.ArchiveExportService;
import com.zjcxph.imgapi.service.ImageUrlService;
import com.zjcxph.imgapi.service.OssService;
import com.zjcxph.imgapi.service.ScanService;
import com.zjcxph.imgapi.storage.ImageStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageControllerMedicalRecordTypeTest {

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

    @InjectMocks
    private ImageController imageController;

    @Test
    void acceptsNewType15() {
        ImageRequest request = requestWithType(15);
        when(scanService.updateImageType(1, 15)).thenReturn(1);

        Result<Void> result = imageController.updateImageType(1, request);

        assertThat(result.getCode()).isEqualTo(200);
        verify(scanService).updateImageType(1, 15);
    }

    @Test
    void rejectsTypesOutsideOneToFifteen() {
        Result<Void> zeroResult = imageController.updateImageType(1, requestWithType(0));
        Result<Void> sixteenResult = imageController.updateImageType(1, requestWithType(16));

        assertThat(zeroResult.getCode()).isEqualTo(400);
        assertThat(sixteenResult.getCode()).isEqualTo(400);
        assertThat(zeroResult.getMessage()).contains("1-15");
        assertThat(sixteenResult.getMessage()).contains("1-15");
    }

    private ImageRequest requestWithType(int type) {
        ImageRequest request = new ImageRequest();
        request.setBtype(type);
        return request;
    }
}
