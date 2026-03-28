package com.zjcxph.imgapi;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.zjcxph.imgapi.monitoring.PressureTestReport;
import com.zjcxph.imgapi.monitoring.PressureTestRequest;
import com.zjcxph.imgapi.monitoring.PressureTestService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PressureTestServiceTest {

    private static HttpServer httpServer;
    private static String targetUrl;

    @BeforeAll
    static void startServer() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/pressure-target", PressureTestServiceTest::handlePressureTarget);
        httpServer.start();
        targetUrl = "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/pressure-target";
    }

    @AfterAll
    static void stopServer() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    @Test
    void runShouldCollectSummaryMetrics() {
        PressureTestService service = new PressureTestService();
        PressureTestRequest request = new PressureTestRequest(
                "service-test",
                targetUrl,
                "GET",
                4,
                12,
                2000,
                null,
                Map.of("X-Test", "true")
        );

        PressureTestReport report = service.run(request);

        assertNotNull(report);
        assertEquals(12, report.totalRequests());
        assertEquals(12, report.successCount());
        assertEquals(0, report.failureCount());
        assertTrue(report.avgLatencyMs() >= 0);
        assertTrue(report.p95LatencyMs() >= report.minLatencyMs());
        assertTrue(report.maxLatencyMs() >= report.p95LatencyMs());
        assertTrue(report.requestsPerSecond() > 0);
        assertNotNull(report.beforeSnapshot());
        assertNotNull(report.afterSnapshot());
        assertEquals(12, report.samples().size());
        assertEquals(1, service.getHistory().size());
    }

    @Test
    void clearHistoryShouldRemovePreviousReports() {
        PressureTestService service = new PressureTestService();
        PressureTestRequest request = new PressureTestRequest(
                "history-test",
                targetUrl,
                "GET",
                2,
                4,
                2000,
                null,
                Map.of()
        );

        service.run(request);
        assertFalse(service.getHistory().isEmpty());

        service.clearHistory();
        assertTrue(service.getHistory().isEmpty());
    }

    private static void handlePressureTarget(HttpExchange exchange) throws IOException {
        try {
            Thread.sleep(15);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        byte[] bytes = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
