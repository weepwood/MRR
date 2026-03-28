package com.zjcxph.imgapi;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class PressureTestControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private static HttpServer httpServer;
    private static String targetUrl;

    @BeforeAll
    static void startServer() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/pressure-target", PressureTestControllerIntegrationTest::handlePressureTarget);
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
    void runPressureTestShouldReturnReportAndPersistHistory() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "controller-test");
        request.put("targetUrl", targetUrl);
        request.put("method", "GET");
        request.put("concurrency", 3);
        request.put("totalRequests", 9);
        request.put("timeoutMillis", 2000);
        request.put("body", null);
        request.put("headers", Map.of("X-Test", "integration"));

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/v1/monitoring-api/pressure-tests/run",
                request,
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(200, body.get("code"));

        Map<String, Object> report = castMap(body.get("data"));
        assertEquals("controller-test", report.get("name"));
        assertEquals(9, ((Number) report.get("totalRequests")).intValue());
        assertEquals(9, ((Number) report.get("successCount")).intValue());
        assertTrue(((Number) report.get("requestsPerSecond")).doubleValue() > 0);

        ResponseEntity<Map> historyResponse = restTemplate.getForEntity(
                "/v1/monitoring-api/pressure-tests/history",
                Map.class
        );

        assertEquals(HttpStatus.OK, historyResponse.getStatusCode());
        Map<String, Object> historyBody = historyResponse.getBody();
        assertNotNull(historyBody);
        assertEquals(200, historyBody.get("code"));
        assertFalse(((java.util.List<?>) historyBody.get("data")).isEmpty());
    }

    @Test
    void latestEndpointShouldBeAccessible() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/v1/monitoring-api/pressure-tests/latest",
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    private static Map<String, Object> castMap(Object value) {
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) value;
        return result;
    }

    private static void handlePressureTarget(HttpExchange exchange) throws IOException {
        byte[] bytes = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
