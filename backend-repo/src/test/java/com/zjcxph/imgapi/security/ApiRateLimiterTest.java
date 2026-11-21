package com.zjcxph.imgapi.security;

import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class ApiRateLimiterTest {

    private final AtomicLong clockMillis = new AtomicLong();
    private final AtomicLong tickerNanos = new AtomicLong();

    private ApiRateLimiter limiter() {
        Ticker ticker = tickerNanos::get;
        return new ApiRateLimiter(null, ticker, clockMillis::get);
    }

    @Test
    void shouldKeepSlidingWindowLimitAndReleaseAfterWindow() {
        ApiRateLimiter limiter = limiter();

        for (int i = 0; i < 30; i++) {
            assertThat(limiter.tryAcquire("192.0.2.10")).isTrue();
        }
        assertThat(limiter.isBlocked("192.0.2.10")).isTrue();
        assertThat(limiter.tryAcquire("192.0.2.10")).isFalse();

        clockMillis.addAndGet(60_001);
        tickerNanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(60_001));
        assertThat(limiter.isBlocked("192.0.2.10")).isFalse();
        assertThat(limiter.tryAcquire("192.0.2.10")).isTrue();
    }

    @Test
    void shouldCountConcurrentRequestsAtomicallyForOneIp() throws Exception {
        ApiRateLimiter limiter = limiter();
        ExecutorService executor = Executors.newFixedThreadPool(12);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 60; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return limiter.tryAcquire("192.0.2.11");
                }));
            }
            start.countDown();

            int allowed = 0;
            for (Future<Boolean> result : results) {
                if (result.get(10, TimeUnit.SECONDS)) {
                    allowed++;
                }
            }
            assertThat(allowed).isEqualTo(30);
            assertThat(limiter.isBlocked("192.0.2.11")).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldExpireDormantIpKeysRatherThanKeepingThemForever() {
        ApiRateLimiter limiter = limiter();
        assertThat(limiter.tryAcquire("192.0.2.12")).isTrue();
        assertThat(limiter.trackedClientCount()).isEqualTo(1);

        // 生产运行设置允许的最大窗口为 600 秒，空闲过期时间需长于它。
        clockMillis.addAndGet(TimeUnit.MINUTES.toMillis(21));
        tickerNanos.addAndGet(TimeUnit.MINUTES.toNanos(21));

        assertThat(limiter.trackedClientCount()).isZero();
        assertThat(limiter.tryAcquire("192.0.2.12")).isTrue();
    }

    @Test
    void shouldBoundNumberOfTrackedIpKeys() {
        ApiRateLimiter limiter = limiter();
        for (int i = 0; i < 10_500; i++) {
            assertThat(limiter.tryAcquire("198.51." + (i / 256) + "." + (i % 256))).isTrue();
        }
        assertThat(limiter.trackedClientCount()).isLessThanOrEqualTo(10_000);
    }
}
