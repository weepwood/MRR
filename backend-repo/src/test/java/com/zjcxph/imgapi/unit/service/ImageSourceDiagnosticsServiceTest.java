package com.zjcxph.imgapi.unit.service;

import com.sun.net.httpserver.HttpServer;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.storage.ArchiveImageSource;
import com.zjcxph.imgapi.storage.ArchiveImageSourceResolver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import com.zjcxph.imgapi.mapper.ImageContentMapper;
import com.zjcxph.imgapi.storage.ResolvedImageStorage;
import com.zjcxph.imgapi.service.ImageSourceDiagnosticsService;
import com.zjcxph.imgapi.service.ImageUrlService;
import com.zjcxph.imgapi.service.OssService;
import com.zjcxph.imgapi.service.ScanService;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

class ImageSourceDiagnosticsServiceTest {
    @Test
    void directProbeDoesNotFollowRedirectOrReportRedirectAsAvailable() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger redirected = new AtomicInteger();
        server.createContext("/image", exchange -> {
            exchange.getResponseHeaders().set("Location", "/target");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/target", exchange -> {
            redirected.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            Scan scan = new Scan();
            scan.setId(1);
            scan.setUploadFlag(1);
            ScanService scans = mock(ScanService.class);
            ImageUrlService urls = mock(ImageUrlService.class);
            ImageContentMapper mapper = mock(ImageContentMapper.class);
            when(mapper.findActiveById(1)).thenReturn(scan);
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/image";
            when(urls.buildImageUrl(scan)).thenReturn(url);
            when(urls.buildPreferredImageUrl(scan)).thenReturn(url);
            when(urls.getEffectiveImageSource()).thenReturn("local");
            var service = new ImageSourceDiagnosticsService(scans, urls, mock(OssService.class), mapper, mock(ResolvedImageStorage.class));
            Map<String, Object> report = service.diagnose(null, null, 1);
            assertThat(redirected.get()).isZero();
            assertThat(step(report, "LOCAL_SOURCE")).containsEntry("success", false);
            assertThat(step(report, "FINAL_SELECTION")).containsEntry("success", false);
            assertThat(report.toString()).doesNotContain(url);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void gatewayUsesActualOrderedSourcesWithoutDirectUrlOrSigningAndRedactsFailures() throws Exception {
        Scan scan = new Scan();
        scan.setId(1);
        scan.setSourceType("AUTO");
        scan.setSourceRef("synthetic-private-key");
        scan.setSourceNode("synthetic-private-node");
        scan.setMigrationStatus("synthetic-private-status");
        PathDO path = PathDO.fromScan(scan);
        ImageContentMapper mapper = mock(ImageContentMapper.class);
        when(mapper.findActiveById(1)).thenReturn(scan);
        ImageUrlService urls = mock(ImageUrlService.class);
        when(urls.isGatewayDelivery()).thenReturn(true);
        OssService signing = mock(OssService.class);
        ArchiveImageSource oss = mock(ArchiveImageSource.class);
        ArchiveImageSource nginx = mock(ArchiveImageSource.class);
        when(oss.supports(path)).thenReturn(true);
        when(nginx.supports(path)).thenReturn(true);
        when(oss.describeSource(path)).thenReturn("OSS");
        when(nginx.describeSource(path)).thenReturn("NGINX");
        when(oss.open(path)).thenThrow(new IOException("synthetic-private-error",
                new HttpTimeoutException("synthetic-private-timeout")));
        AtomicBoolean closed = new AtomicBoolean();
        when(nginx.open(path)).thenReturn(new ByteArrayInputStream(new byte[]{1}) {
            @Override public void close() {
                closed.set(true);
            }
        });
        var storage = new ResolvedImageStorage(new ArchiveImageSourceResolver(List.of(oss, nginx)), new SimpleMeterRegistry());
        var service = new ImageSourceDiagnosticsService(mock(ScanService.class), urls, signing, mapper, storage);
        var report = service.diagnose(null, null, 1);

        assertThat(report).containsEntry("selectedSource", "NGINX")
                .containsEntry("fallbackReason", "CONTROLLED_SOURCE_FALLBACK")
                .containsEntry("selectedUrl", "");
        assertThat(step(report, "GATEWAY_SOURCE_OSS").toString()).contains("TIMEOUT");
        assertThat(step(report, "GATEWAY_SOURCE_NGINX")).containsEntry("success", true);
        assertThat(step(report, "FINAL_SELECTION")).containsEntry("success", true);
        assertThat(report.toString()).doesNotContain("synthetic-private");
        assertThat(closed).isTrue();
        verify(urls, never()).buildImageUrl(scan);
        verify(urls, never()).buildPreferredImageUrl(scan);
        verifyNoInteractions(signing);
    }

    @Test
    void deletedOrMissingImageNeverTriggersSourceProbe() {
        ImageContentMapper mapper = mock(ImageContentMapper.class);
        ResolvedImageStorage storage = mock(ResolvedImageStorage.class);
        ImageUrlService urls = mock(ImageUrlService.class);
        OssService signing = mock(OssService.class);
        var service = new ImageSourceDiagnosticsService(mock(ScanService.class), urls, signing, mapper, storage);
        assertThat(service.diagnose(null, null, 1)).containsEntry("found", false);
        verify(mapper).findActiveById(1);
        verifyNoInteractions(storage, urls, signing);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> step(Map<String, Object> report, String code) {
        return ((List<Map<String, Object>>) report.get("steps")).stream()
                .filter(step -> code.equals(step.get("code"))).findFirst().orElseThrow();
    }
}
