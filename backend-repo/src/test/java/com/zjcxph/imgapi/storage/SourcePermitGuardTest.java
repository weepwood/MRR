package com.zjcxph.imgapi.storage;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourcePermitGuardTest {

    @Test
    void repeatedCloseClosesTheUnderlyingResourceOnlyOnce() throws Exception {
        SourcePermitGuard guard = guard();
        AtomicInteger closes = new AtomicInteger();
        InputStream input = guard.open(() -> new ByteArrayInputStream(new byte[0]) {
            @Override public void close() {
                closes.incrementAndGet();
            }
        });
        input.close();
        input.close();
        assertThat(closes).hasValue(1);
        assertExactlyOnePermitAvailable(guard);
    }

    @Test
    void anotherCloseCannotReleaseThePermitWhileTheFirstCloseIsStillRunning() throws Exception {
        SourcePermitGuard guard = guard();
        CountDownLatch closing = new CountDownLatch(1);
        CountDownLatch finishClose = new CountDownLatch(1);
        AtomicInteger closes = new AtomicInteger();
        InputStream input = guard.open(() -> new ByteArrayInputStream(new byte[0]) {
            @Override public void close() throws IOException {
                if (closes.incrementAndGet() == 1) {
                    closing.countDown();
                    try {
                        if (!finishClose.await(5, TimeUnit.SECONDS)) {
                            throw new IOException("synthetic-close-timeout");
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IOException("synthetic-close-interrupted");
                    }
                }
            }
        });
        var executor = Executors.newSingleThreadExecutor();
        var firstClose = executor.submit(() -> { input.close(); return null; });
        try {
            assertThat(closing.await(5, TimeUnit.SECONDS)).isTrue();
            input.close();
            assertThatThrownBy(() -> guard.call(() -> "unexpected"))
                    .isInstanceOf(IOException.class).hasMessageContaining("并发已满");
            assertThat(closes).hasValue(1);
        } finally {
            finishClose.countDown();
            try {
                firstClose.get(5, TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
                assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            }
        }
        assertExactlyOnePermitAvailable(guard);
    }

    @Test
    void aFailingCloseStillReleasesExactlyOnePermitAndCannotBeRepeated() throws Exception {
        SourcePermitGuard guard = guard();
        AtomicInteger closes = new AtomicInteger();
        InputStream input = guard.open(() -> new ByteArrayInputStream(new byte[0]) {
            @Override public void close() throws IOException {
                closes.incrementAndGet();
                throw new IOException("synthetic-close-failure");
            }
        });
        assertThatThrownBy(input::close).isInstanceOf(IOException.class).hasMessage("synthetic-close-failure");
        input.close();
        assertThat(closes).hasValue(1);
        assertExactlyOnePermitAvailable(guard);
    }

    @Test
    void aFailingOpenReturnsItsPermit() throws Exception {
        SourcePermitGuard guard = guard();
        assertThatThrownBy(() -> guard.open(() -> { throw new IOException("synthetic-open-failure"); }))
                .isInstanceOf(IOException.class).hasMessage("synthetic-open-failure");
        assertExactlyOnePermitAvailable(guard);
    }

    @Test
    void aNullStreamReturnsItsPermit() throws Exception {
        SourcePermitGuard guard = guard();
        assertThatThrownBy(() -> guard.open(() -> null))
                .isInstanceOf(IOException.class).hasMessageContaining("空数据流");
        assertExactlyOnePermitAvailable(guard);
    }

    @Test
    void aFailingMetadataCallReturnsItsPermit() throws Exception {
        SourcePermitGuard guard = guard();
        assertThatThrownBy(() -> guard.call(() -> { throw new IOException("synthetic-metadata-failure"); }))
                .isInstanceOf(IOException.class).hasMessage("synthetic-metadata-failure");
        assertExactlyOnePermitAvailable(guard);
    }

    @Test
    void interruptedAcquisitionPreservesTheInterruptAndDoesNotCreateAPermit() throws Exception {
        SourcePermitGuard guard = guard();
        try (var input = guard.open(InputStream::nullInputStream)) {
            Thread.currentThread().interrupt();
            try {
                assertThatThrownBy(() -> guard.call(() -> "unexpected"))
                        .isInstanceOf(IOException.class).hasMessageContaining("中断");
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
            } finally {
                Thread.interrupted();
            }
            assertThatThrownBy(() -> guard.call(() -> "unexpected"))
                    .isInstanceOf(IOException.class).hasMessageContaining("并发已满");
        }
        assertExactlyOnePermitAvailable(guard);
    }

    @Test
    void queuedWaitersAcquireInOrderAndCancelledWaiterDoesNotConsumePermit() throws Exception {
        var guard = new SourcePermitGuard(1, Duration.ofSeconds(5));
        var initial = guard.open(InputStream::nullInputStream);
        var order = new java.util.concurrent.CopyOnWriteArrayList<Integer>();
        var executor = Executors.newFixedThreadPool(3);
        try {
            var first = executor.submit(() -> guard.call(() -> { order.add(1); return 1; }));
            awaitWaiters(guard, 1);
            var cancelled = executor.submit(() -> guard.call(() -> { order.add(2); return 2; }));
            awaitWaiters(guard, 2);
            var last = executor.submit(() -> guard.call(() -> { order.add(3); return 3; }));
            awaitWaiters(guard, 3);
            cancelled.cancel(true);
            awaitWaiters(guard, 2);
            assertThat(guard.inUse()).isEqualTo(1);
            initial.close();
            assertThat(first.get(3, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(last.get(3, TimeUnit.SECONDS)).isEqualTo(3);
            assertThat(order).containsExactly(1, 3);
            assertThat(guard.inUse()).isZero();
        } finally { initial.close(); executor.shutdownNow(); }
    }

    private void awaitWaiters(SourcePermitGuard guard, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (guard.waiting() != expected && System.nanoTime() < deadline) Thread.sleep(5);
        assertThat(guard.waiting()).isEqualTo(expected);
    }

    private SourcePermitGuard guard() {
        return new SourcePermitGuard(1, Duration.ofMillis(30));
    }

    private void assertExactlyOnePermitAvailable(SourcePermitGuard guard) throws Exception {
        try (var input = guard.open(InputStream::nullInputStream)) {
            assertThatThrownBy(() -> guard.call(() -> "unexpected"))
                    .isInstanceOf(IOException.class).hasMessageContaining("并发已满");
        }
        assertThat(guard.call(() -> "available")).isEqualTo("available");
    }
}
