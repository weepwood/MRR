package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.service.ArchiveExportService;
import com.zjcxph.imgapi.service.ScanService;
import com.zjcxph.imgapi.storage.ImageStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
        Scan scan = scan(1, 1L, "00789508", "", 1, "page.jpg");
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
        List<String> ids = List.of("1", "2", "3");
        when(scanService.findActiveByIds(List.of(1, 2, 3))).thenReturn(List.of(
                scan(1, 1L, "00789508", "", 1, "page.jpg"),
                scan(2, 1L, "00789508", "", 2, "page.jpg"),
                scan(3, 1L, "00789508", "", 3, "page-2.jpg")
        ));
        when(imageStorage.open(any(PathDO.class))).thenAnswer(invocation -> {
            PathDO item = invocation.getArgument(0);
            String value = switch (item.getScanId()) {
                case 1 -> "one";
                case 2 -> "two";
                case 3 -> "three";
                default -> throw new IOException("unexpected scan id");
            };
            return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
        });

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
    void failsZipWhenAnyPlannedImageCannotBeOpened() throws Exception {
        PathDO first = new PathDO("25.03.15", "page-1.jpg", "605746", "00789508");
        PathDO missing = new PathDO("25.03.15", "page-2.jpg", "605746", "00789508");
        when(imageStorage.open(first)).thenReturn(new ByteArrayInputStream("one".getBytes(StandardCharsets.UTF_8)));
        when(imageStorage.open(missing)).thenThrow(new IOException("not found"));
        ArchiveExportServiceImpl service = new ArchiveExportServiceImpl(scanService, imageStorage);
        ArchiveExportService.BatchZipExport export = new ArchiveExportService.BatchZipExport(List.of(first, missing));

        assertThatThrownBy(() -> service.writeBatchZip(export, new ByteArrayOutputStream()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("page-2.jpg")
                .hasMessageContaining("not found");
    }

    @Test
    void failsZipInsteadOfSilentlyWritingAnEmptyImageEntry() throws Exception {
        PathDO empty = new PathDO("25.03.15", "empty.jpg", "605746", "00789508");
        when(imageStorage.open(empty)).thenReturn(new ByteArrayInputStream(new byte[0]));
        ArchiveExportServiceImpl service = new ArchiveExportServiceImpl(scanService, imageStorage);

        assertThatThrownBy(() -> service.writeBatchZip(
                new ArchiveExportService.BatchZipExport(List.of(empty)),
                new ByteArrayOutputStream()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("图片内容为空");
    }

    @Test
    void keepsSanitizedSourceFailuresInTheTaskFacingMessage() throws Exception {
        PathDO missing = new PathDO("25.03.15", "page.jpg", "605746", "00789508");
        when(imageStorage.open(missing)).thenThrow(new IOException(
                "所有受控图片来源均读取失败（NGINX: Nginx 图片服务返回状态码 404；LOCAL: 后端本地文件不存在或不可读）"));
        ArchiveExportServiceImpl service = new ArchiveExportServiceImpl(scanService, imageStorage);

        assertThatThrownBy(() -> service.writeBatchZip(
                new ArchiveExportService.BatchZipExport(List.of(missing)),
                new ByteArrayOutputStream()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("NGINX")
                .hasMessageContaining("404")
                .hasMessageContaining("LOCAL");
    }

    @Test
    void prepareSelectedArchiveRejectsMissingOrInactiveIds() {
        when(scanService.findActiveByIds(List.of(1, 2))).thenReturn(List.of(
                scan(1, 1L, "00789508", "", 1, "page-1.jpg")
        ));
        ArchiveExportServiceImpl service = new ArchiveExportServiceImpl(scanService, imageStorage);

        assertThatThrownBy(() -> service.prepareSelectedArchive(List.of("1", "2")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在或已失效");
    }

    @Test
    void prepareSelectedArchiveRejectsImagesFromDifferentArchives() {
        when(scanService.findActiveByIds(List.of(1, 2))).thenReturn(List.of(
                scan(1, 1L, "00789508", "", 1, "page-1.jpg"),
                scan(2, 2L, "00789509", "", 1, "page-2.jpg")
        ));
        ArchiveExportServiceImpl service = new ArchiveExportServiceImpl(scanService, imageStorage);

        assertThatThrownBy(() -> service.prepareSelectedArchive(List.of("1", "2")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("同一份病案");
    }

    @Test
    void prepareSelectedArchiveSortsPagesDeterministically() {
        when(scanService.findActiveByIds(List.of(3, 1, 2))).thenReturn(List.of(
                scan(3, 1L, "00789508", "", 3, "page-3.jpg"),
                scan(1, 1L, "00789508", "", 1, "page-1.jpg"),
                scan(2, 1L, "00789508", "", 2, "page-2.jpg")
        ));
        ArchiveExportServiceImpl service = new ArchiveExportServiceImpl(scanService, imageStorage);

        ArchiveExportService.BatchZipExport export = service.prepareSelectedArchive(List.of("3", "1", "2"));

        assertThat(export.items())
                .extracting(PathDO::getFilename)
                .containsExactly("page-1.jpg", "page-2.jpg", "page-3.jpg");
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
        Scan item = scan(1, 1L, "00789508", "", 1, "page.jpg");
        when(scanService.findActiveByIds(List.of(1))).thenReturn(List.of(item));
        ArchiveExportServiceImpl service = new ArchiveExportServiceImpl(scanService, imageStorage);

        ArchiveExportService.BatchZipExport export = service.prepareBatch(List.of("1"));

        assertThat(export.itemCount()).isEqualTo(1);
        assertThatThrownBy(() -> export.items().add(new PathDO()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private Scan scan(int id, Long archiveId, String bah, String sjh, int pages, String filename) {
        Scan scan = new Scan();
        scan.setId(id);
        scan.setArchiveId(archiveId);
        scan.setBah(bah);
        scan.setSjh(sjh);
        scan.setBrxh("605746");
        scan.setFolder("25.03.15");
        scan.setFilename(filename);
        scan.setPages(pages);
        scan.setUploadFlag(1);
        return scan;
    }
}
