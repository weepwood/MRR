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
