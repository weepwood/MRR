package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.service.ArchiveExportService;
import com.zjcxph.imgapi.service.ScanService;
import com.zjcxph.imgapi.storage.ImageStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchiveExportServiceImplTest {

    @Mock
    private ScanService scanService;

    @Mock
    private ImageStorage imageStorage;

    @Test
    void streamsZipAndRenamesDuplicateEntries() throws Exception {
        PathDO first = new PathDO("25.03.15", "page.jpg", "605746", "00789508");
        PathDO second = new PathDO("25.03.15", "page.jpg", "605746", "00789508");
        when(scanService.getImagePathList(List.of("1", "2"))).thenReturn(List.of(first, second));
        when(imageStorage.open(first))
                .thenReturn(new ByteArrayInputStream("one".getBytes(StandardCharsets.UTF_8)))
                .thenReturn(new ByteArrayInputStream("two".getBytes(StandardCharsets.UTF_8)));

        ArchiveExportServiceImpl service = new ArchiveExportServiceImpl(scanService, imageStorage);
        ArchiveExportService.BatchZipExport export = service.prepareBatch(List.of("1", "2"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        service.writeBatchZip(export, output);

        List<String> names = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(output.toByteArray()))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                names.add(entry.getName());
            }
        }
        assertThat(names).containsExactly(
                "00789508/page.jpg",
                "00789508/page-2.jpg"
        );
    }

    @Test
    void prepareBatchReturnsImmutableSnapshot() {
        PathDO item = new PathDO("25.03.15", "page.jpg", "605746", "00789508");
        when(scanService.getImagePathList(List.of("1"))).thenReturn(List.of(item));
        ArchiveExportServiceImpl service = new ArchiveExportServiceImpl(scanService, imageStorage);

        ArchiveExportService.BatchZipExport export = service.prepareBatch(List.of("1"));

        assertThat(export.itemCount()).isEqualTo(1);
        assertThat(export.items()).isUnmodifiable();
    }
}
