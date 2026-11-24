package com.zjcxph.imgapi.storage;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class SourcePermitGuard {

    @FunctionalInterface
    interface InputStreamSupplier {
        InputStream get() throws IOException;
    }

    @FunctionalInterface
    interface IoSupplier<T> {
        T get() throws IOException;
    }

    private final Semaphore semaphore;
    private final int limit;
    private final Duration timeout;

    SourcePermitGuard(int maxConcurrency, Duration timeout) {
        this.limit = Math.max(1, maxConcurrency);
        this.semaphore = new Semaphore(limit, true);
        this.timeout = timeout == null || timeout.isNegative() || timeout.isZero()
                ? Duration.ofSeconds(30)
                : timeout;
    }

    InputStream open(InputStreamSupplier supplier) throws IOException {
        acquire();
        boolean success = false;
        try {
            SourceOpenBudget.check();
            InputStream delegate = supplier.get();
            if (delegate == null) {
                throw new IOException("图片来源返回了空数据流");
            }
            success = true;
            if (delegate instanceof ImageReadStream imageStream) {
                return imageStream.withPermit(semaphore::release);
            }
            return new FilterInputStream(delegate) {
                private final AtomicBoolean closed = new AtomicBoolean();

                @Override
                public void close() throws IOException {
                    // The closing owner keeps the permit until the underlying resource finishes closing.
                    if (!closed.compareAndSet(false, true)) {
                        return;
                    }
                    try {
                        super.close();
                    } finally {
                        semaphore.release();
                    }
                }
            };
        } finally {
            if (!success) {
                semaphore.release();
            }
        }
    }

    <T> T call(IoSupplier<T> supplier) throws IOException {
        acquire();
        try {
            SourceOpenBudget.check();
            return supplier.get();
        } finally {
            semaphore.release();
        }
    }

    int limit() { return limit; }
    int inUse() { return limit - semaphore.availablePermits(); }
    int waiting() { return semaphore.getQueueLength(); }

    static final class CapacityException extends IOException {
        CapacityException() {
            super("图片来源并发已满，请稍后重试");
        }
    }

    private void acquire() throws IOException {
        try {
            if (!semaphore.tryAcquire(SourceOpenBudget.limit(timeout).toNanos(), TimeUnit.NANOSECONDS)) {
                throw new CapacityException();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("等待图片来源时任务被中断", exception);
        }
    }
}
