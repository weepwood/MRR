package com.zjcxph.imgapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.zjcxph.imgapi.config.ArchiveImageSourceProperties;
import com.zjcxph.imgapi.config.ImageProperties;
import com.zjcxph.imgapi.dto.resp.NginxBrowserPageDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NginxBrowserServiceTest {

    private HttpServer server;
    private NginxBrowserService service;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/images/", this::handleRequest);
        server.start();

        ImageProperties imageProperties = new ImageProperties();
        imageProperties.setServerUrlDefault(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/images/"
        );
        imageProperties.setUsername("reader");
        imageProperties.setPassword("secret");

        ArchiveImageSourceProperties sourceProperties = new ArchiveImageSourceProperties();
        sourceProperties.setNginxConnectTimeout(Duration.ofSeconds(2));
        sourceProperties.setNginxReadTimeout(Duration.ofSeconds(2));
        service = new NginxBrowserService(
                imageProperties,
                sourceProperties,
                new ObjectMapper()
        );
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldListConfiguredAndUnconfiguredServers() {
        var servers = service.listServers();

        assertEquals(4, servers.size());
        assertTrue(servers.getFirst().configured());
        assertFalse(servers.get(1).configured());
    }

    @Test
    void shouldBrowseJsonAutoindexAndKeepDirectoriesFirst() {
        NginxBrowserPageDTO page = service.browse("default", "", 0, 200);

        assertEquals(2, page.totalEntries());
        assertEquals("folder/", page.entries().getFirst().path());
        assertTrue(page.entries().getFirst().directory());
        assertEquals("0013.jpg", page.entries().get(1).path());
        assertEquals(12, page.entries().get(1).size());
    }

    @Test
    void shouldBrowseDefaultHtmlAutoindexWithoutParentLink() {
        NginxBrowserPageDTO page = service.browse("default", "folder/", 0, 200);

        assertEquals(1, page.totalEntries());
        assertEquals("folder/inside.jpg", page.entries().getFirst().path());
        assertFalse(page.entries().getFirst().directory());
    }

    @Test
    void shouldRejectTraversalOutsideConfiguredRoot() {
        assertThrows(IllegalArgumentException.class,
                () -> service.browse("default", "../private/", 0, 200));
    }

    @Test
    void shouldProxyFileWithBasicAuthentication() throws IOException {
        NginxBrowserService.RemoteFile remoteFile = service.openFile("default", "0013.jpg");
        try (InputStream input = remoteFile.inputStream()) {
            assertArrayEquals("image-data".getBytes(StandardCharsets.UTF_8), input.readAllBytes());
        }
        assertEquals("image/jpeg", remoteFile.contentType());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void stalledUpstreamBodyTimesOutAndNextRequestWorks(boolean directory) throws Exception {
        var release = new java.util.concurrent.CountDownLatch(1);
        var sent = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        server.createContext("/images/stalled/", exchange -> {
            try (exchange) {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, 100);
                exchange.getResponseBody().write('[');
                exchange.getResponseBody().flush();
                sent.countDown();
                try { release.await(8, java.util.concurrent.TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            }
        });
        var image = new ImageProperties();
        image.setServerUrlDefault("http://127.0.0.1:" + server.getAddress().getPort() + "/images/");
        image.setUsername("reader"); image.setPassword("secret");
        var properties = new ArchiveImageSourceProperties();
        properties.setNginxConnectTimeout(Duration.ofSeconds(2));
        properties.setNginxReadTimeout(Duration.ofMillis(500));
        try (var client = java.net.http.HttpClient.newHttpClient()) {
            var browser = new NginxBrowserService(image, properties, new ObjectMapper(), client);
            try {
                var read = executor.submit(() -> {
                    if (directory) { browser.browse("default", "stalled/", 0, 20); }
                    else try (var stream = browser.openFile("default", "stalled/file.jpg").inputStream()) {
                        assertEquals('[', stream.read());
                        stream.read();
                    }
                    return null;
                });
                assertTrue(sent.await(2, java.util.concurrent.TimeUnit.SECONDS));
                var failure = assertThrows(java.util.concurrent.ExecutionException.class,
                        () -> read.get(2, java.util.concurrent.TimeUnit.SECONDS));
                Throwable cause = failure.getCause();
                if (directory) cause = cause.getCause();
                assertTrue(cause instanceof java.net.SocketTimeoutException, "expected body read timeout: " + cause);
            } finally { release.countDown(); executor.shutdownNow(); }
            try (var next = browser.openFile("default", "0013.jpg").inputStream()) {
                assertArrayEquals("image-data".getBytes(StandardCharsets.UTF_8), next.readAllBytes());
            }
        }
    }

    @Test
    void metadataParsingFailureClosesOpenedResponse() throws Exception {
        var client = org.mockito.Mockito.mock(java.net.http.HttpClient.class);
        @SuppressWarnings("unchecked")
        var response = (java.net.http.HttpResponse<InputStream>) org.mockito.Mockito.mock(java.net.http.HttpResponse.class);
        var closed = new java.util.concurrent.atomic.AtomicInteger();
        var input = new java.io.ByteArrayInputStream(new byte[]{1}) {
            @Override public void close() { closed.incrementAndGet(); }
        };
        org.mockito.Mockito.when(response.body()).thenReturn(input);
        org.mockito.Mockito.when(response.statusCode()).thenReturn(200);
        org.mockito.Mockito.when(response.headers()).thenReturn(java.net.http.HttpHeaders.of(
                java.util.Map.of("Content-Length", java.util.List.of("invalid")), (name,value) -> true));
        org.mockito.Mockito.when(client.send(org.mockito.ArgumentMatchers.any(java.net.http.HttpRequest.class),
                org.mockito.ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<InputStream>>any())).thenReturn(response);
        var image = new ImageProperties(); image.setServerUrlDefault("http://127.0.0.1:" + server.getAddress().getPort() + "/images/");
        var browser = new NginxBrowserService(image, new ArchiveImageSourceProperties(), new ObjectMapper(), client);
        assertThrows(RuntimeException.class, () -> browser.openFile("default", "0013.jpg"));
        assertEquals(1, closed.get());
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        if (!"Basic cmVhZGVyOnNlY3JldA==".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
            send(exchange, 401, "text/plain", "unauthorized".getBytes(StandardCharsets.UTF_8));
            return;
        }

        String path = exchange.getRequestURI().getPath();
        if ("/images/".equals(path)) {
            String body = """
                    [
                      {"name":"0013.jpg","type":"file","mtime":"2026-07-22T10:00:00Z","size":12},
                      {"name":"folder","type":"directory","mtime":"2026-07-22T09:00:00Z","size":0}
                    ]
                    """;
            send(exchange, 200, "application/json", body.getBytes(StandardCharsets.UTF_8));
            return;
        }
        if ("/images/folder/".equals(path)) {
            String body = """
                    <html><body><pre>
                    <a href="../">../</a>
                    <a href="inside.jpg">inside.jpg</a>
                    </pre></body></html>
                    """;
            send(exchange, 200, "text/html; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
            return;
        }
        if ("/images/0013.jpg".equals(path)) {
            send(exchange, 200, "image/jpeg", "image-data".getBytes(StandardCharsets.UTF_8));
            return;
        }
        send(exchange, 404, "text/plain", "not found".getBytes(StandardCharsets.UTF_8));
    }

    private void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
