package com.zjcxph.imgapi.storage;

import java.io.IOException;
import java.time.Duration;

/** Shared monotonic budget for source resolution/opening, not a hard deadline for body reads or native filesystem calls. */
final class SourceOpenBudget implements AutoCloseable {
    private static final ThreadLocal<Long> DEADLINE = new ThreadLocal<>();
    private final Long previous;

    private SourceOpenBudget(Duration duration) {
        previous = DEADLINE.get();
        long now = System.nanoTime();
        Duration safe = duration == null || duration.isNegative() || duration.isZero()
                ? Duration.ofSeconds(120) : duration;
        long nanos = safe.compareTo(Duration.ofHours(1)) > 0 ? Duration.ofHours(1).toNanos() : safe.toNanos();
        long remaining = previous == null ? nanos : Math.min(nanos, previous - now);
        DEADLINE.set(now + remaining);
    }

    static SourceOpenBudget start(Duration duration) {
        return new SourceOpenBudget(duration);
    }

    static void check() throws IOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new IOException("影像来源操作已中断", new InterruptedException());
        }
        Long deadline = DEADLINE.get();
        if (deadline != null && deadline - System.nanoTime() <= 0) {
            throw new DeadlineExceeded();
        }
    }

    static Duration limit(Duration requested) throws IOException {
        check();
        Long deadline = DEADLINE.get();
        if (deadline == null) return requested;
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) throw new DeadlineExceeded();
        Duration budget = Duration.ofNanos(remaining);
        return requested.compareTo(budget) < 0 ? requested : budget;
    }

    static final class DeadlineExceeded extends IOException {
        DeadlineExceeded() { super("影像来源打开预算已耗尽"); }
    }

    @Override
    public void close() {
        if (previous == null) DEADLINE.remove();
        else DEADLINE.set(previous);
    }
}
