package com.zjcxph.imgapi.storage;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

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
    private final Duration timeout;

    SourcePermitGuard(int maxConcurrency, Duration timeout) {
        this.semaphore = new Semaphore(Math.max(1, maxConcurrency), true);
        this.timeout = timeout == null || timeout.isNegative() || timeout.isZero()
                ? Duration.ofSeconds(30)
                : timeout;
    }

    InputStream open(InputStreamSupplier supplier) throws IOException {
        acquire();
        boolean success = false;
        try {
            InputStream delegate = supplier.get();
            if (delegate == null) {
                throw new IOException("图片来源返回了空数据流");
            }
            success = true;
            return new FilterInputStream(delegate) {
                private boolean released;

                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        if (!released) {
                            released = true;
                            semaphore.release();
                        }
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
            return supplier.get();
        } finally {
            semaphore.release();
        }
    }

    private void acquire() throws IOException {
        try {
            if (!semaphore.tryAcquire(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IOException("图片来源并发已满，请稍后重试");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("等待图片来源时任务被中断", exception);
        }
    }
}
