package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.entity.Scan;
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
import java.util.Base64;
import java.util.List;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchiveExportServiceImplTest {

    private static final byte[] ONE_PIXEL_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2nWQAAAAASUVORK5CYII="
    );

    @Mock
    private ScanService scanService;

    @Mock
    private ImageStorage imageStorage;

    @Test
    void prepareArchiveConvertsScansToStorageLocations() {
        Scan scan = new Scan();
        scan.setBah("00789508");
        scan.setBrxh("605746");
        scan.setFolder("25.03.15");
        scan.setFilename("page.jpg");
        when(scanService.getImageListByCode("00789508", "789508", "", ""))
                .thenReturn(List.of(scan));
        ArchiveExportServiceImpl service = new ArchiveExportServiceImpl(scanService, imageStorage);

        ArchiveExportService.BatchZipExport export = service.prepareArchive("00789508", "");

        assertThat(export.items()).singleElement().satisfies(item -> {
            assertThat(item.getBah()).isEqualTo("00789508");
            assertThat(item.getBrxh()).isEqualTo("605746");
            assertThat(item.getFolder()).isEqualTo("25.03.15");
            assertThat(item.getFilename()).isEqualTo("page.jpg");
        });
    }

    @Test
    void streamsZipAndAvoidsAllEntryNameCollisions() throws Exception {
        PathDO first = new PathDO("25.03.15", "page.jpg", "605746", "00789508");
        PathDO duplicate = new PathDO("25.03.15", "page.jpg", "605746", "00789508");
        PathDO realSuffixed = new PathDO("25.03.15", "page-2.jpg", "605746", "00789508");
        List<String> ids = List.of("1", "2", "3");
        when(scanService.getImagePathList(ids)).thenReturn(List.of(first, duplicate, realSuffixed));
        when(imageStorage.open(first))
                .thenReturn(new ByteArrayInputStream("one".getBytes(StandardCharsets.UTF_8)))
                .thenReturn(new ByteArrayInputStream("two".getBytes(StandardCharsets.UTF_8)));
        when(imageStorage.open(realSuffixed))
                .thenReturn(new ByteArrayInputStream("three".getBytes(StandardCharsets.UTF_8)));

        ArchiveExportServiceImpl service = new ArchiveExportServiceImpl(scanService, imageStorage);
        ArchiveExportService.BatchZipExport export = service.prepareBatch(ids);
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
                "00789508/page-2.jpg",
                "00789508/page-2-2.jpg"
        );
    }

    @Test
    void streamsPdfWithOnePagePerImage() throws Exception {
        PathDO first = new PathDO("25.03.15", "page-1.png", "605746", "00789508");
        PathDO second = new PathDO("25.03.15", "page-2.png", "605746", "00789508");
        when(imageStorage.open(first)).thenReturn(new ByteArrayInputStream(ONE_PIXEL_PNG));
        when(imageStorage.open(second)).thenReturn(new ByteArrayInputStream(ONE_PIXEL_PNG));

        ArchiveExportServiceImpl service = new ArchiveExportServiceImpl(scanService, imageStorage);
        ArchiveExportService.BatchZipExport export = new ArchiveExportService.BatchZipExport(List.of(first, second));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        service.writeBatchPdf(export, output);

        String header = new String(output.toByteArray(), 0, 5, StandardCharsets.US_ASCII);
        assertThat(header).isEqualTo("%PDF-");
        assertThat(output.size()).isGreaterThan(100);
    }

    @Test
    void prepareBatchReturnsImmutableSnapshot() {
        PathDO item = new PathDO("25.03.15", "page.jpg", "605746", "00789508");
        when(scanService.getImagePathList(List.of("1"))).thenReturn(List.of(item));
        ArchiveExportServiceImpl service = new ArchiveExportServiceImpl(scanService, imageStorage);

        ArchiveExportService.BatchZipExport export = service.prepareBatch(List.of("1"));

        assertThat(export.itemCount()).isEqualTo(1);
        assertThatThrownBy(() -> export.items().add(item))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
