package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.config.ArchiveExportProperties;
import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.service.ArchiveExportService;
import com.zjcxph.imgapi.service.ScanService;
import com.zjcxph.imgapi.storage.ImageStorage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ArchiveExportOutputBudgetTest {
    @Test
    void firstReleaseDefaultsTo128MiB() {
        assertThat(new ArchiveExportProperties().getMaxFileBytes()).isEqualTo(128L * 1024 * 1024);
    }

    @Test
    void legacyZipAndPdfWritersCannotBypassTheConfiguredOutputLimit() {
        var properties = new ArchiveExportProperties();
        properties.setMaxFileBytes(64);
        ImageStorage storage = mock(ImageStorage.class);
        byte[] png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2nWQAAAAASUVORK5CYII=");
        try {
            when(storage.open(any())).thenAnswer(call -> new ByteArrayInputStream(png));
        } catch (IOException failure) {
            throw new AssertionError(failure);
        }
        new ApplicationContextRunner()
                .withBean(ArchiveExportProperties.class, () -> properties)
                .withBean(ScanService.class, () -> mock(ScanService.class))
                .withBean(ImageStorage.class, () -> storage)
                .withBean(ArchiveExportServiceImpl.class)
                .run(context -> {
                    var service = context.getBean(ArchiveExportServiceImpl.class);
                    var export = new ArchiveExportService.BatchZipExport(List.of(
                            new PathDO(1, "synthetic", "page.png", "1", "123", "456", null, null, null, null, 1L)));
                    var zip = new ByteArrayOutputStream();
                    assertThatThrownBy(() -> service.writeBatchZip(export, zip)).isInstanceOf(IOException.class);
                    assertThat(zip.size()).isLessThanOrEqualTo(64);
                    var pdf = new ByteArrayOutputStream();
                    assertThatThrownBy(() -> service.writeBatchPdf(export, pdf)).isInstanceOf(IOException.class);
                    assertThat(pdf.size()).isLessThanOrEqualTo(64);
                });
    }
}
