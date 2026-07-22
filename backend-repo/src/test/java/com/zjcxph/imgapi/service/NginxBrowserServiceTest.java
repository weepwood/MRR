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
        try (remoteFile.inputStream()) {
            assertArrayEquals("image-data".getBytes(StandardCharsets.UTF_8), remoteFile.inputStream().readAllBytes());
        }
        assertEquals("image/jpeg", remoteFile.contentType());
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
