package com.zjcxph.imgapi.unit.service;

import com.sun.net.httpserver.HttpServer;
import com.zjcxph.imgapi.entity.Scan;
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
            when(scans.findById(1)).thenReturn(scan);
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/image";
            when(urls.buildImageUrl(scan)).thenReturn(url);
            when(urls.buildPreferredImageUrl(scan)).thenReturn(url);
            when(urls.getEffectiveImageSource()).thenReturn("local");
            var service = new ImageSourceDiagnosticsService(scans, urls, mock(OssService.class));
            Map<String, Object> report = service.diagnose(null, null, 1);
            assertThat(redirected.get()).isZero();
            assertThat(step(report, "LOCAL_SOURCE")).containsEntry("success", false);
            assertThat(step(report, "FINAL_SELECTION")).containsEntry("success", false);
            assertThat(report.toString()).doesNotContain(url);
        } finally {
            server.stop(0);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> step(Map<String, Object> report, String code) {
        return ((List<Map<String, Object>>) report.get("steps")).stream()
                .filter(step -> code.equals(step.get("code"))).findFirst().orElseThrow();
    }
}
