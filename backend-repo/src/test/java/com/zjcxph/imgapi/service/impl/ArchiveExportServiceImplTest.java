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
    void externalArchiveExportUsesFreshExactScopeAndExcludesAliasRows() {
        var allowed=scan(1,1L,"00789508","",1,"allowed.jpg");
        var other=scan(2,2L,"789508","",1,"other.jpg");
        when(scanService.getFreshImageListByCode("00789508","789508","","")).thenReturn(List.of(allowed,other));
        var export=new ArchiveExportServiceImpl(scanService,imageStorage).prepareExternalArchive("00789508","");
        assertThat(export.items()).extracting(PathDO::getScanId).containsExactly(1);
    }

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
    void pdfCancellationStopsBeforeReadingFurtherChunksAndClosesSource() throws Exception {
        var item = new PathDO("25.03.15", "synthetic.png", "456", "123");
        var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        var closed = new java.util.concurrent.atomic.AtomicBoolean();
        var reads = new java.util.concurrent.atomic.AtomicInteger();
        var source = new java.io.InputStream() {
            @Override public int read() { throw new UnsupportedOperationException(); }
            @Override public int read(byte[] buffer, int offset, int count) {
                int call = reads.incrementAndGet();
                if (call > 4) return -1;
                buffer[offset] = 1;
                cancelled.set(true);
                return 1;
            }
            @Override public void close() { closed.set(true); }
        };
        when(imageStorage.open(item)).thenReturn(source);
        var service = new ArchiveExportServiceImpl(scanService, imageStorage);
        var progress = new ArchiveExportService.ExportProgress() {
            @Override public boolean isCancelled() { return cancelled.get(); }
        };
        assertThatThrownBy(() -> service.writeBatchPdf(new ArchiveExportService.BatchZipExport(List.of(item)),
                new ByteArrayOutputStream(), progress))
                .isInstanceOf(com.zjcxph.imgapi.exception.ArchiveExportCancelledException.class);
        assertThat(reads.get()).isEqualTo(1);
        assertThat(closed).isTrue();
    }

    @Test
    void exportsRealNasAndLoopbackHttpSourcesToZipAndPdf(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root) throws Exception {
        java.nio.file.Files.write(root.resolve("nas.png"), ONE_PIXEL_PNG);
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/http.png", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(200, ONE_PIXEL_PNG.length);
                exchange.getResponseBody().write(ONE_PIXEL_PNG);
            }
        });
        server.start();
        try {
            var properties = new com.zjcxph.imgapi.config.ArchiveImageSourceProperties();
            var nas = new com.zjcxph.imgapi.config.ArchiveImageSourceProperties.NasNode();
            nas.setRoot(root.toString());
            properties.setNasNodes(java.util.Map.of("nas", nas));
            var http = new com.zjcxph.imgapi.config.ArchiveImageSourceProperties.HttpNode();
            http.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            properties.setHttpNodes(java.util.Map.of("http", http));
            properties.setNasMaxConcurrency(1);
            properties.setHttpMaxConcurrency(1);
            var storage = new com.zjcxph.imgapi.storage.ResolvedImageStorage(
                    new com.zjcxph.imgapi.storage.ArchiveImageSourceResolver(List.of(
                            new com.zjcxph.imgapi.storage.NasArchiveImageSource(properties),
                            new com.zjcxph.imgapi.storage.HttpArchiveImageSource(properties))),
                    new io.micrometer.core.instrument.simple.SimpleMeterRegistry(), properties);
            var first = new PathDO("25.03.15", "nas.png", "456", "123");
            first.setSourceType("NAS"); first.setSourceNode("nas"); first.setSourceRef("nas.png");
            var second = new PathDO("25.03.15", "http.png", "456", "123");
            second.setSourceType("HTTP"); second.setSourceNode("http"); second.setSourceRef("http.png");
            var export = new ArchiveExportService.BatchZipExport(List.of(first, second));
            var service = new ArchiveExportServiceImpl(scanService, storage);
            var bytes = new ByteArrayOutputStream();
            service.writeBatchZip(export, bytes);
            try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                assertThat(zip.getNextEntry().getName()).endsWith("/nas.png");
                assertThat(zip.readAllBytes()).isEqualTo(ONE_PIXEL_PNG);
                assertThat(zip.getNextEntry().getName()).endsWith("/http.png");
                assertThat(zip.readAllBytes()).isEqualTo(ONE_PIXEL_PNG);
                assertThat(zip.getNextEntry()).isNull();
            }
            bytes.reset();
            service.writeBatchPdf(export, bytes);
            try (var pdf = new com.itextpdf.kernel.pdf.PdfDocument(
                    new com.itextpdf.kernel.pdf.PdfReader(new ByteArrayInputStream(bytes.toByteArray())))) {
                assertThat(pdf.getNumberOfPages()).isEqualTo(2);
            }
        } finally { server.stop(0); }
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
