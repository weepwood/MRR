package com.zjcxph.imgapi.storage;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NetworkSourceCircuitTest {
    @Test
    void actualBodyTimeoutsTripCircuitAndCompleteHalfOpenResponseRestoresNode() throws Exception {
        var clock = new AtomicLong();
        var circuit = new NetworkSourceCircuit(new SourceNodeCircuit(3, Duration.ofSeconds(30), clock::get));
        var permits = new SourcePermitGuard(1, Duration.ofMillis(300));
        var healthy = new AtomicBoolean();
        var requests = new AtomicInteger();
        var release = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(4);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(workers);
        server.createContext("/synthetic", exchange -> {
            requests.incrementAndGet();
            try (exchange) {
                if (healthy.get()) {
                    exchange.sendResponseHeaders(200, 2);
                    exchange.getResponseBody().write(new byte[]{7, 8});
                } else {
                    exchange.sendResponseHeaders(200, 100);
                    exchange.getResponseBody().write(1);
                    exchange.getResponseBody().flush();
                    try { release.await(10, TimeUnit.SECONDS); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
            }
        });
        server.start();
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/synthetic"))
                .timeout(Duration.ofSeconds(5)).build();
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()) {
            SourcePermitGuard.InputStreamSupplier operation = () -> permits.open(() -> {
                try {
                    var response = client.send(request, TimedHttpBody.handler(Duration.ofMillis(500)));
                    return ImageReadStream.withLength(response.body(), ImageReadStream.httpLength(response.headers()));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("test request interrupted", e);
                }
            });
            for (int i = 0; i < 3; i++) {
                try (InputStream input = circuit.open(operation)) {
                    assertThat(input.read()).isEqualTo(1);
                    assertThatThrownBy(input::read).isInstanceOf(SocketTimeoutException.class);
                }
            }
            assertThatThrownBy(() -> circuit.open(operation)).isInstanceOf(SourceNodeCircuit.CircuitOpen.class);
            assertThat(requests.get()).isEqualTo(3);
            healthy.set(true);
            release.countDown();
            clock.addAndGet(Duration.ofSeconds(30).toNanos());
            try (var probe = circuit.open(operation)) {
                assertThatThrownBy(() -> circuit.open(operation)).isInstanceOf(SourceNodeCircuit.CircuitOpen.class);
                assertThat(probe.read()).isEqualTo(7);
                // Receiving headers or a partial body is not proof of node recovery.
                assertThatThrownBy(() -> circuit.open(operation)).isInstanceOf(SourceNodeCircuit.CircuitOpen.class);
                assertThat(probe.read()).isEqualTo(8);
            }
            try (var input = circuit.open(operation)) {
                assertThat(input.readAllBytes()).containsExactly((byte)7, (byte)8);
            }
            assertThat(requests.get()).isEqualTo(5);
        } finally {
            release.countDown();
            server.stop(0);
            workers.shutdownNow();
        }
    }
}
