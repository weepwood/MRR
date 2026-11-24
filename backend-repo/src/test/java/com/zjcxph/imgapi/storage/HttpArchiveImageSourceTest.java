package com.zjcxph.imgapi.storage;

import com.sun.net.httpserver.HttpServer;
import com.zjcxph.imgapi.config.ArchiveImageSourceProperties;
import com.zjcxph.imgapi.entity.PathDO;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class HttpArchiveImageSourceTest {

    @Test
    void stalledBodyTimesOutAndNextRequestCanUseReleasedPermit() throws Exception {
        var release = new java.util.concurrent.CountDownLatch(1);
        var headerSent = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newCachedThreadPool();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/slow", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(200, 100);
                exchange.getResponseBody().write(1);
                exchange.getResponseBody().flush();
                headerSent.countDown();
                try { release.await(5, java.util.concurrent.TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        });
        server.createContext("/fast", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(200, 1);
                exchange.getResponseBody().write(7);
            }
        });
        server.start();
        try {
            var properties = new ArchiveImageSourceProperties();
            properties.setHttpMaxConcurrency(1);
            properties.setAcquireTimeout(java.time.Duration.ofMillis(200));
            var node = new ArchiveImageSourceProperties.HttpNode();
            node.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            node.setReadTimeout(java.time.Duration.ofSeconds(1));
            properties.setHttpNodes(Map.of("node", node));
            var source = new HttpArchiveImageSource(properties);
            PathDO slow = new PathDO();
            slow.setSourceType("HTTP"); slow.setSourceNode("node"); slow.setSourceRef("slow");
            var fast = new PathDO();
            fast.setSourceType("HTTP"); fast.setSourceNode("node"); fast.setSourceRef("fast");
            try (var input = source.open(slow)) {
                assertThat(headerSent.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                assertThat(input.read()).isEqualTo(1);
                var pending = executor.submit(() -> {
                    try { input.read(); return false; }
                    catch (java.net.SocketTimeoutException expected) { return true; }
                });
                assertThat(pending.get(3, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } finally { release.countDown(); }
            try (var input = source.open(fast)) { assertThat(input.readAllBytes()).containsExactly((byte)7); }
        } finally {
            release.countDown();
            server.stop(0);
            executor.shutdownNow();
        }
    }

    @Test
    void requestsHighBahBySjhDirectory() throws Exception {
        AtomicReference<String> requestedPath = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestedPath.set(exchange.getRequestURI().getPath());
            byte[] body = "http-image".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        try {
            ArchiveImageSourceProperties properties = new ArchiveImageSourceProperties();
            ArchiveImageSourceProperties.HttpNode node = new ArchiveImageSourceProperties.HttpNode();
            node.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/images/");
            properties.setHttpNodes(Map.of("archive-http", node));
            HttpArchiveImageSource source = new HttpArchiveImageSource(properties);
            PathDO image = new PathDO(
                    13,
                    "24.04.07",
                    "0013.jpg",
                    "666666",
                    "10000000",
                    "00789124",
                    "HTTP",
                    "archive-http",
                    null,
                    null,
                    null);

            try (var input = source.open(image)) {
                assertThat(input.readAllBytes()).isEqualTo("http-image".getBytes(StandardCharsets.UTF_8));
            }
            assertThat(requestedPath.get()).isEqualTo(
                    "/images/24.04/24.04.07/00789124-10000000/0013.jpg");
        } finally {
            server.stop(0);
        }
    }
}
