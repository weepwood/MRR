package com.zjcxph.imgapi.storage;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Bound caller waiting without pretending a native filesystem call can be forcibly stopped. */
final class NasOperationIsolation implements AutoCloseable {
    private final ThreadPoolExecutor workers;
    private final Map<String, Semaphore> nodeSlots;
    private final Map<String, NasNodeCircuit> circuits;

    @FunctionalInterface
    interface Operation<T> {
        T run(Runnable markNativeStarted) throws IOException;
    }

    NasOperationIsolation(int concurrency, java.util.Set<String> nodes) {
        int limit = Math.max(1, concurrency);
        int nodeLimit = Math.max(1, limit / Math.max(1, nodes.size()));
        nodeSlots = nodes.stream().collect(Collectors.toUnmodifiableMap(
                node -> node, node -> new Semaphore(nodeLimit)));
        circuits = nodes.stream().collect(Collectors.toUnmodifiableMap(
                node -> node, node -> new NasNodeCircuit(3, Duration.ofSeconds(30), System::nanoTime)));
        workers = new ThreadPoolExecutor(limit, limit, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(limit), runnable -> {
                    Thread thread = new Thread(runnable, "mrr-nas-open");
                    thread.setDaemon(true);
                    return thread;
                });
        workers.allowCoreThreadTimeOut(true);
    }

    <T> T call(String node, Duration configuredTimeout, Operation<T> operation,
               Consumer<T> discard) throws IOException {
        Duration timeout = SourceOpenBudget.limit(configuredTimeout == null || configuredTimeout.isNegative()
                || configuredTimeout.isZero() ? Duration.ofSeconds(30) : configuredTimeout);
        long nanos = timeout.compareTo(Duration.ofHours(1)) > 0 ? Duration.ofHours(1).toNanos() : timeout.toNanos();
        long deadline = System.nanoTime() + nanos;
        Semaphore slots = nodeSlots.get(node);
        if (slots == null || !slots.tryAcquire()) throw new SourcePermitGuard.CapacityException();
        NasNodeCircuit circuit = circuits.get(node);
        NasNodeCircuit.Attempt attempt;
        try {
            attempt = circuit.begin();
        } catch (IOException rejected) {
            slots.release();
            throw rejected;
        }
        var nativeStarted = new java.util.concurrent.atomic.AtomicBoolean();
        var slotReleased = new java.util.concurrent.atomic.AtomicBoolean();
        Runnable releaseSlot = () -> {
            if (slotReleased.compareAndSet(false, true)) slots.release();
        };
        var result = new CompletableFuture<T>();
        var claimed = new CompletableFuture<Boolean>();
        try {
            workers.execute(() -> {
                try {
                    long remaining = deadline - System.nanoTime();
                    if (claimed.isDone() || remaining <= 0) {
                        releaseSlot.run();
                        result.completeExceptionally(new SocketTimeoutException("NAS operation timed out"));
                        return;
                    }
                    try (var budget = SourceOpenBudget.start(Duration.ofNanos(remaining))) {
                        T value = operation.run(() -> {
                            if (claimed.isDone()) throw new java.util.concurrent.CancellationException("NAS caller departed");
                            nativeStarted.set(true);
                        });
                        result.complete(value);
                        // Only the worker discards a late result; the caller never blocks on its close.
                        if (!claimed.join()) discard.accept(value);
                    }
                } catch (IOException | RuntimeException failure) {
                    releaseSlot.run();
                    result.completeExceptionally(failure);
                } finally {
                    releaseSlot.run();
                }
            });
        } catch (RejectedExecutionException rejected) {
            releaseSlot.run();
            circuit.finish(attempt, NasNodeCircuit.Outcome.NEUTRAL);
            throw new SourcePermitGuard.CapacityException();
        }
        NasNodeCircuit.Outcome outcome = NasNodeCircuit.Outcome.NEUTRAL;
        try {
            T value = result.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            if (deadline - System.nanoTime() <= 0) {
                if (nativeStarted.get()) outcome = NasNodeCircuit.Outcome.NATIVE_TIMEOUT;
                throw new SocketTimeoutException("NAS operation timed out");
            }
            claimed.complete(true);
            outcome = NasNodeCircuit.Outcome.SUCCESS;
            return value;
        } catch (TimeoutException timeoutFailure) {
            if (nativeStarted.get()) outcome = NasNodeCircuit.Outcome.NATIVE_TIMEOUT;
            throw new SocketTimeoutException("NAS operation timed out");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("NAS operation interrupted", interrupted);
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof SocketTimeoutException && nativeStarted.get()) {
                outcome = NasNodeCircuit.Outcome.NATIVE_TIMEOUT;
            }
            if (failure.getCause() instanceof IOException io) throw io;
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new IOException("NAS operation failed", failure.getCause());
        } finally {
            claimed.complete(false);
            circuit.finish(attempt, outcome);
            // A successful caller owns the already-opened stream; its source permit
            // remains held by the stream, not by the now-finished NAS open operation.
            if (outcome == NasNodeCircuit.Outcome.SUCCESS) releaseSlot.run();
        }
    }

    @Override public void close() {
        // Do not cancel/release native operations that may still hold actual source permits.
        workers.shutdown();
    }
}
