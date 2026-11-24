package com.zjcxph.imgapi.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/** A single native owner per permitted stream. A timed-out read never writes into caller memory. */
final class NasBodyStream extends InputStream {
    private static final int CHUNK = 16 * 1024;
    private final InputStream delegate;
    private final ExecutorService owner;
    private final long timeoutNanos;
    private final byte[] scratch = new byte[CHUNK];
    private boolean closed;
    private boolean readObserved;
    private boolean outcomeReported;
    private final Consumer<SourceNodeCircuit.Outcome> finish;

    private NasBodyStream(InputStream delegate, Duration timeout, Consumer<SourceNodeCircuit.Outcome> finish) {
        this.delegate = delegate;
        this.finish = finish;
        Duration safe = timeout == null || timeout.isZero() || timeout.isNegative()
                ? Duration.ofSeconds(30) : timeout;
        timeoutNanos = safe.compareTo(Duration.ofHours(1)) > 0
                ? Duration.ofHours(1).toNanos() : safe.toNanos();
        // At most one owner per actual source permit; no per-read thread or accumulating work.
        owner = Executors.newSingleThreadExecutor(runnable -> {
            var thread = new Thread(runnable, "mrr-nas-body");
            thread.setDaemon(true);
            return thread;
        });
    }

    static InputStream wrap(InputStream delegate, Duration timeout, Consumer<SourceNodeCircuit.Outcome> finish) {
        var stream = new NasBodyStream(delegate, timeout, finish);
        if (delegate instanceof ImageReadStream image) {
            return ImageReadStream.withSeek(stream, image.length(),
                    image.seekable() ? offset -> stream.seek(image, offset) : null);
        }
        return stream;
    }

    @Override public synchronized int read() throws IOException {
        int value = invoke(delegate::read);
        readObserved = true;
        return value;
    }

    @Override public synchronized int read(byte[] buffer, int offset, int count) throws IOException {
        Objects.checkFromIndexSize(offset, count, buffer.length);
        if (count == 0) return 0;
        int read = invoke(() -> delegate.read(scratch, 0, Math.min(count, CHUNK)));
        if (read != 0) readObserved = true;
        if (read > 0) System.arraycopy(scratch, 0, buffer, offset, read);
        return read;
    }

    private synchronized void seek(ImageReadStream image, long offset) throws IOException {
        invoke(() -> { image.seek(offset); return null; });
    }

    private <T> T invoke(Callable<T> task) throws IOException {
        if (closed) throw new IOException("NAS body closed");
        Future<T> result = owner.submit(task);
        try {
            return await(result);
        } catch (IOException | RuntimeException failure) {
            // Do not cancel the native operation or release its permit early. Queue exactly one close
            // behind it, and let the caller return even if the filesystem ignores interruption.
            report(failure instanceof SocketTimeoutException
                    ? SourceNodeCircuit.Outcome.BODY_FAILURE : SourceNodeCircuit.Outcome.NEUTRAL);
            queueClose();
            throw failure;
        }
    }

    private <T> T await(Future<T> result) throws IOException {
        try {
            return result.get(timeoutNanos, TimeUnit.NANOSECONDS);
        } catch (TimeoutException failure) {
            throw new SocketTimeoutException("NAS body operation timed out");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("NAS body operation interrupted", interrupted);
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof IOException io) throw io;
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new IOException("NAS body operation failed", failure.getCause());
        }
    }

    private Future<?> queueClose() {
        if (closed) return null;
        closed = true;
        Future<?> result = owner.submit(() -> {
            try { delegate.close(); }
            catch (IOException | RuntimeException failure) {
                org.slf4j.LoggerFactory.getLogger(NasBodyStream.class)
                        .warn("NAS body close failed: errorType={}", failure.getClass().getSimpleName());
                throw failure;
            }
            return null;
        });
        owner.shutdown();
        return result;
    }

    private void report(SourceNodeCircuit.Outcome outcome) {
        if (!outcomeReported) {
            outcomeReported = true;
            finish.accept(outcome);
        }
    }

    @Override public synchronized void close() throws IOException {
        Future<?> result = queueClose();
        if (result == null) return;
        try {
            await(result);
            // A valid range read is useful evidence too; merely opening or seeking is not.
            report(readObserved ? SourceNodeCircuit.Outcome.SUCCESS : SourceNodeCircuit.Outcome.NEUTRAL);
        } catch (IOException | RuntimeException failure) {
            report(failure instanceof SocketTimeoutException
                    ? SourceNodeCircuit.Outcome.BODY_FAILURE : SourceNodeCircuit.Outcome.NEUTRAL);
            throw failure;
        }
    }
}
