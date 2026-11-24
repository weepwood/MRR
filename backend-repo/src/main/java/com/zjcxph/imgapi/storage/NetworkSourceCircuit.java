package com.zjcxph.imgapi.storage;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** Reuse the same circuit state machine as NAS; account for the actual network body lifecycle. */
final class NetworkSourceCircuit {
    private final SourceNodeCircuit circuit = new SourceNodeCircuit(3, Duration.ofSeconds(30), System::nanoTime);

    InputStream open(SourcePermitGuard.InputStreamSupplier operation) throws IOException {
        var attempt = circuit.begin();
        try {
            InputStream input = operation.get();
            long length = input instanceof ImageReadStream image ? image.length() : -1;
            var finished = new AtomicBoolean();
            var monitored = new FilterInputStream(input) {
                private long consumed;
                private void finish(SourceNodeCircuit.Outcome outcome) {
                    if (finished.compareAndSet(false, true)) circuit.finish(attempt, outcome);
                }
                private void received(int count) {
                    if (count > 0) consumed += count;
                    if (count < 0 || (length >= 0 && consumed >= length)) {
                        finish(length >= 0 && consumed < length
                                ? SourceNodeCircuit.Outcome.SOURCE_FAILURE : SourceNodeCircuit.Outcome.SUCCESS);
                    }
                }
                @Override public int read() throws IOException {
                    try {
                        int value = in.read();
                        received(value < 0 ? -1 : 1);
                        return value;
                    } catch (IOException | RuntimeException failure) {
                        finish(classify(failure));
                        throw failure;
                    }
                }
                @Override public int read(byte[] bytes, int offset, int count) throws IOException {
                    try {
                        int read = in.read(bytes, offset, count);
                        received(read);
                        return read;
                    } catch (IOException | RuntimeException failure) {
                        finish(classify(failure));
                        throw failure;
                    }
                }
                @Override public void close() throws IOException {
                    try { super.close(); }
                    catch (IOException | RuntimeException failure) { finish(classify(failure)); throw failure; }
                    finally { finish(SourceNodeCircuit.Outcome.NEUTRAL); }
                }
            };
            return ImageReadStream.withLength(monitored, length);
        } catch (IOException | RuntimeException failure) {
            circuit.finish(attempt, classify(failure));
            throw failure;
        }
    }

    long size(SourcePermitGuard.IoSupplier<Long> operation) throws IOException {
        var attempt = circuit.begin();
        try {
            long value = operation.get();
            circuit.finish(attempt, value >= 0 ? SourceNodeCircuit.Outcome.SUCCESS : SourceNodeCircuit.Outcome.NEUTRAL);
            return value;
        } catch (IOException | RuntimeException failure) {
            circuit.finish(attempt, classify(failure));
            throw failure;
        }
    }

    private static SourceNodeCircuit.Outcome classify(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean networkFailure = false;
        for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof InterruptedException || cause instanceof SourceOpenBudget.DeadlineExceeded
                    || cause instanceof SourcePermitGuard.CapacityException) return SourceNodeCircuit.Outcome.NEUTRAL;
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException
                    || cause instanceof ApiCallTimeoutException || cause instanceof ApiCallAttemptTimeoutException
                    || cause instanceof java.net.ConnectException) networkFailure = true;
            int status = cause instanceof SourceHttpStatusException http ? http.statusCode()
                    : cause instanceof S3Exception s3 ? s3.statusCode() : -1;
            if (status == 502 || status == 503 || status == 504) networkFailure = true;
            else if (status >= 400 && status < 500) return SourceNodeCircuit.Outcome.NEUTRAL;
        }
        return networkFailure ? SourceNodeCircuit.Outcome.SOURCE_FAILURE : SourceNodeCircuit.Outcome.NEUTRAL;
    }
}
