package com.zjcxph.imgapi.storage;

import com.zjcxph.imgapi.config.ArchiveImageSourceProperties;
import com.zjcxph.imgapi.entity.PathDO;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.time.Duration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NasArchiveImageSourceTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsSymlinkThatEscapesTheConfiguredRoot() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("root"));
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Files.writeString(outside.resolve("page.jpg"), "image");
        try {
            Files.createSymbolicLink(root.resolve("linked"), outside);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            Assumptions.assumeTrue(false, "当前运行环境不支持创建符号链接");
        }

        ArchiveImageSourceProperties properties = new ArchiveImageSourceProperties();
        ArchiveImageSourceProperties.NasNode node = new ArchiveImageSourceProperties.NasNode();
        node.setRoot(root.toString());
        properties.setNasNodes(Map.of("archive01", node));
        PathDO image = new PathDO();
        image.setSourceType("NAS");
        image.setSourceNode("archive01");
        image.setSourceRef("linked/page.jpg");
        NasArchiveImageSource source = new NasArchiveImageSource(properties);

        assertThatThrownBy(() -> source.open(image))
                .isInstanceOf(InvalidImagePathException.class)
                .hasMessageContaining("符号链接越过");
    }

    @Test
    void diagnosticClosesNasStreamAndReleasesPermitForNextRead() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("diagnostic-nas"));
        Files.writeString(root.resolve("page.jpg"), "synthetic");
        ArchiveImageSourceProperties properties = new ArchiveImageSourceProperties();
        properties.setNasMaxConcurrency(1);
        properties.setAcquireTimeout(Duration.ofMillis(100));
        var node = new ArchiveImageSourceProperties.NasNode();
        node.setRoot(root.toString());
        properties.setNasNodes(Map.of("synthetic-node", node));
        PathDO image = new PathDO();
        image.setSourceType("NAS");
        image.setSourceNode("synthetic-node");
        image.setSourceRef("page.jpg");
        var source = new NasArchiveImageSource(properties);
        var storage = new ResolvedImageStorage(new ArchiveImageSourceResolver(List.of(source)), new SimpleMeterRegistry());

        var probe = storage.probe(image);
        assertThat(probe.available()).isTrue();
        assertThat(probe.selectedSource()).isEqualTo("NAS");
        assertThat(probe.toString()).doesNotContain(root.toString(), "synthetic-node", "page.jpg");
        try (var stream = source.open(image)) {
            assertThat(stream.readAllBytes()).isEqualTo("synthetic".getBytes(StandardCharsets.UTF_8));
        }
        Files.delete(root.resolve("page.jpg"));
        assertThat(storage.probe(image).attempts()).containsExactly(
                new ResolvedImageStorage.SourceAttempt("NAS", false, "NOT_FOUND"));
    }

    @Test
    void blockedNasOpenReturnsWithinBudgetAndClosesLateResult() throws Exception {
        var properties = new ArchiveImageSourceProperties();
        properties.setNasMaxConcurrency(2);
        properties.setNasOpenTimeout(Duration.ofMillis(150));
        var node = new ArchiveImageSourceProperties.NasNode();
        node.setRoot(tempDir.toString());
        properties.setNasNodes(Map.of("blocked", node, "healthy", node));
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var closed = new java.util.concurrent.CountDownLatch(1);
        var source = new NasArchiveImageSource(properties) {
            @Override java.io.InputStream openFile(PathDO image) throws IOException {
                if ("blocked".equals(image.getSourceNode())) {
                    entered.countDown();
                    // Native filesystem operations may ignore interruption.
                    while (release.getCount() > 0) {
                        try { release.await(); } catch (InterruptedException ignored) {}
                    }
                    return new java.io.ByteArrayInputStream(new byte[]{1}) {
                        @Override public void close() { closed.countDown(); }
                    };
                }
                return new java.io.ByteArrayInputStream(new byte[]{2});
            }
        };
        var blocked = new PathDO();
        blocked.setSourceType("NAS");
        blocked.setSourceNode("blocked");
        var healthy = new PathDO();
        healthy.setSourceType("NAS");
        healthy.setSourceNode("healthy");
        try (var callers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var request = callers.submit(() -> {
                try (var ignored = source.open(blocked)) {
                    return false;
                } catch (IOException failure) {
                    return true;
                }
            });
            try {
                assertThat(entered.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                assertThat(request.get(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                // A second blocked request cannot accumulate another worker for the same node.
                assertThatThrownBy(() -> source.open(blocked)).isInstanceOf(IOException.class);
                try (var stream = source.open(healthy)) { assertThat(stream.read()).isEqualTo(2); }
            } finally {
                release.countDown();
            }
            assertThat(closed.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void blockedBodyReturnsBeforeNativeReadAndKeepsPermitUntilLateClose() throws Exception {
        var properties = new ArchiveImageSourceProperties();
        properties.setNasMaxConcurrency(1);
        properties.setAcquireTimeout(Duration.ofMillis(100));
        properties.setNasOpenTimeout(Duration.ofMillis(200));
        properties.setNasBodyTimeout(Duration.ofMillis(200));
        var node = new ArchiveImageSourceProperties.NasNode();
        node.setRoot(tempDir.toString());
        properties.setNasNodes(Map.of("synthetic", node));
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var closed = new java.util.concurrent.CountDownLatch(1);
        var opens = new java.util.concurrent.atomic.AtomicInteger();
        var source = new NasArchiveImageSource(properties) {
            @Override java.io.InputStream openFile(PathDO image) {
                if (opens.incrementAndGet() > 1) return new java.io.ByteArrayInputStream(new byte[]{7});
                return new java.io.InputStream() {
                    @Override public int read() { throw new UnsupportedOperationException(); }
                    @Override public int read(byte[] bytes, int offset, int count) {
                        entered.countDown();
                        while (release.getCount() > 0) {
                            try { release.await(); } catch (InterruptedException ignored) {}
                        }
                        bytes[offset] = 9;
                        return 1;
                    }
                    @Override public void close() { closed.countDown(); }
                };
            }
        };
        var image = new PathDO(); image.setSourceType("NAS"); image.setSourceNode("synthetic");
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        byte[] buffer = {0};
        try (var input = source.open(image)) {
            var pending = executor.submit(() -> {
                try { input.read(buffer); return false; }
                catch (java.net.SocketTimeoutException expected) { return true; }
            });
            try {
                assertThat(entered.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                assertThat(pending.get(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                input.close();
                assertThatThrownBy(() -> source.open(image)).isInstanceOf(IOException.class);
                assertThat(buffer).containsExactly((byte)0);
            } finally { release.countDown(); }
            assertThat(closed.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(buffer).containsExactly((byte)0);
            try (var next = source.open(image)) { assertThat(next.read()).isEqualTo(7); }
        } finally {
            release.countDown();
            executor.shutdownNow();
            source.shutdown();
        }
    }

    @Test
    void consecutiveBodyFailuresTripNodeDespiteSuccessfulOpensAndPreserveHealthyNode() throws Exception {
        var properties = new ArchiveImageSourceProperties();
        properties.setNasMaxConcurrency(2);
        properties.setAcquireTimeout(Duration.ofMillis(200));
        properties.setNasOpenTimeout(Duration.ofSeconds(1));
        properties.setNasBodyTimeout(Duration.ofMillis(200));
        var node = new ArchiveImageSourceProperties.NasNode();
        node.setRoot(tempDir.toString());
        properties.setNasNodes(Map.of("bad", node, "good", node));
        Files.writeString(tempDir.resolve("probe.jpg"), "synthetic");
        var clock = new java.util.concurrent.atomic.AtomicLong();
        var recovered = new java.util.concurrent.atomic.AtomicBoolean();
        var opens = new java.util.concurrent.atomic.AtomicInteger();
        var closes = new java.util.concurrent.atomic.AtomicInteger();
        var source = new NasArchiveImageSource(properties, clock::get) {
            @Override java.io.InputStream openFile(PathDO image) {
                if ("good".equals(image.getSourceNode())) return new java.io.ByteArrayInputStream(new byte[]{7});
                opens.incrementAndGet();
                if (recovered.get()) return new java.io.ByteArrayInputStream(new byte[]{9});
                return new java.io.InputStream() {
                    @Override public int read() throws IOException {
                        throw new java.net.SocketTimeoutException("synthetic native body timeout");
                    }
                    @Override public void close() { closes.incrementAndGet(); }
                };
            }
        };
        var bad = new PathDO(); bad.setSourceType("NAS"); bad.setSourceNode("bad"); bad.setSourceRef("probe.jpg");
        var good = new PathDO(); good.setSourceType("NAS"); good.setSourceNode("good");
        try {
            for (int i = 0; i < 3; i++) {
                try (var input = source.open(bad)) {
                    assertThatThrownBy(input::read).isInstanceOf(java.net.SocketTimeoutException.class);
                }
                long until = System.nanoTime() + Duration.ofSeconds(2).toNanos();
                while (closes.get() <= i && System.nanoTime() < until) Thread.sleep(5);
                assertThat(closes.get()).isEqualTo(i+1);
                if (i < 2) assertThat(source.size(bad)).isEqualTo(9);
            }
            assertThatThrownBy(() -> source.open(bad)).isInstanceOf(SourceNodeCircuit.CircuitOpen.class);
            assertThat(opens.get()).isEqualTo(3);
            clock.addAndGet(Duration.ofSeconds(30).toNanos());
            // A successful metadata-only half-open probe cannot heal the body failure.
            assertThat(source.size(bad)).isEqualTo(9);
            recovered.set(true);
            try (var probe = source.open(bad)) {
                assertThatThrownBy(() -> source.open(bad)).isInstanceOf(SourceNodeCircuit.CircuitOpen.class);
                assertThat(probe.read()).isEqualTo(9);
                assertThatThrownBy(() -> source.open(bad)).isInstanceOf(SourceNodeCircuit.CircuitOpen.class);
            }
            try (var next = source.open(bad)) { assertThat(next.read()).isEqualTo(9); }
            assertThat(opens.get()).isEqualTo(5);
            try (var input = source.open(good)) { assertThat(input.read()).isEqualTo(7); }
        } finally { source.shutdown(); }
    }

    @Test
    void oneNodeCannotConsumeAllBodyPermitsAndHealthyNodeRemainsAvailable() throws Exception {
        var properties = new ArchiveImageSourceProperties();
        properties.setNasMaxConcurrency(2);
        properties.setAcquireTimeout(Duration.ofMillis(100));
        properties.setNasOpenTimeout(Duration.ofSeconds(1));
        properties.setNasBodyTimeout(Duration.ofMillis(100));
        var node = new ArchiveImageSourceProperties.NasNode();
        node.setRoot(tempDir.toString());
        properties.setNasNodes(Map.of("bad", node, "good", node));
        var opens = new java.util.concurrent.atomic.AtomicInteger();
        var release = new java.util.concurrent.CountDownLatch(1);
        var closed = new java.util.concurrent.CountDownLatch(1);
        var source = new NasArchiveImageSource(properties) {
            @Override InputStream openFile(PathDO image) {
                if ("good".equals(image.getSourceNode())) return new java.io.ByteArrayInputStream(new byte[]{7});
                opens.incrementAndGet();
                return new InputStream() {
                    @Override public int read() {
                        while (release.getCount() > 0) {
                            try { release.await(); } catch (InterruptedException ignored) {}
                        }
                        return 1;
                    }
                    @Override public void close() { closed.countDown(); }
                };
            }
        };
        var bad = new PathDO(); bad.setSourceType("NAS"); bad.setSourceNode("bad");
        var good = new PathDO(); good.setSourceType("NAS"); good.setSourceNode("good");
        try (var stream = source.open(bad)) {
            assertThatThrownBy(stream::read).isInstanceOf(java.net.SocketTimeoutException.class);
            assertThatThrownBy(() -> { try (var unexpected = source.open(bad)) {} })
                    .isInstanceOf(SourcePermitGuard.CapacityException.class);
            assertThat(opens.get()).isEqualTo(1);
            try (var healthy = source.open(good)) { assertThat(healthy.read()).isEqualTo(7); }
            release.countDown();
            assertThat(closed.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            try (var recovered = source.open(bad)) { assertThat(recovered.read()).isEqualTo(1); }
        } finally { release.countDown(); source.shutdown(); }
    }

    @Test
    void blockedNativeCloseRetainsPermitAndRecoversAfterActualCompletion() throws Exception {
        var properties = new ArchiveImageSourceProperties();
        properties.setNasMaxConcurrency(1);
        properties.setAcquireTimeout(Duration.ofMillis(100));
        properties.setNasOpenTimeout(Duration.ofMillis(300));
        properties.setNasBodyTimeout(Duration.ofMillis(150));
        var node = new ArchiveImageSourceProperties.NasNode();
        node.setRoot(tempDir.toString());
        properties.setNasNodes(Map.of("synthetic", node));
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var closed = new java.util.concurrent.CountDownLatch(1);
        var opens = new java.util.concurrent.atomic.AtomicInteger();
        var closes = new java.util.concurrent.atomic.AtomicInteger();
        var source = new NasArchiveImageSource(properties) {
            @Override java.io.InputStream openFile(PathDO image) {
                if (opens.incrementAndGet() > 1) return new java.io.ByteArrayInputStream(new byte[]{7});
                return new java.io.ByteArrayInputStream(new byte[]{1}) {
                    @Override public void close() {
                        closes.incrementAndGet();
                        entered.countDown();
                        while (release.getCount() > 0) {
                            try { release.await(); } catch (InterruptedException ignored) {}
                        }
                        closed.countDown();
                    }
                };
            }
        };
        var image = new PathDO(); image.setSourceType("NAS"); image.setSourceNode("synthetic");
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try (var input = source.open(image)) {
            assertThat(input.read()).isEqualTo(1);
            var pending = executor.submit(() -> {
                try { input.close(); return false; }
                catch (java.net.SocketTimeoutException expected) { return true; }
            });
            assertThat(entered.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(pending.get(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            input.close();
            assertThat(closes.get()).isEqualTo(1);
            assertThatThrownBy(() -> source.open(image)).isInstanceOf(IOException.class);
            release.countDown();
            assertThat(closed.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            try (var next = source.open(image)) { assertThat(next.read()).isEqualTo(7); }
        } finally {
            release.countDown();
            executor.shutdownNow();
            source.shutdown();
        }
    }

    @Test
    void readsHighBahFromSjhDirectoryWhenSourceRefIsAbsent() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("high-bah-root"));
        Path file = Files.createDirectories(
                        root.resolve("24.04")
                                .resolve("24.04.07")
                                .resolve("00789124-10000000"))
                .resolve("0013.jpg");
        Files.writeString(file, "nas-image", StandardCharsets.UTF_8);

        ArchiveImageSourceProperties properties = new ArchiveImageSourceProperties();
        ArchiveImageSourceProperties.NasNode node = new ArchiveImageSourceProperties.NasNode();
        node.setRoot(root.toString());
        properties.setNasNodes(Map.of("archive01", node));
        PathDO image = new PathDO(
                13,
                "24.04.07",
                "0013.jpg",
                "666666",
                "10000000",
                "00789124",
                "NAS",
                "archive01",
                null,
                null,
                null);
        NasArchiveImageSource source = new NasArchiveImageSource(properties);

        try (var input = source.open(image)) {
            assertThat(input.readAllBytes()).isEqualTo("nas-image".getBytes(StandardCharsets.UTF_8));
        }
    }
}
