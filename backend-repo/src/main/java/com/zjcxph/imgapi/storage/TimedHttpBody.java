package com.zjcxph.imgapi.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** One outstanding JDK HTTP body demand; wait for bytes without occupying an extra I/O worker. */
final class TimedHttpBody extends InputStream implements HttpResponse.BodySubscriber<InputStream> {
    private record Event(List<ByteBuffer> bytes, Throwable failure, boolean end) {}
    private static final Event END = new Event(List.of(), null, true);
    private final CompletableFuture<InputStream> body = new CompletableFuture<>();
    private final ArrayBlockingQueue<Event> events = new ArrayBlockingQueue<>(2);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final long timeoutNanos;
    private volatile Flow.Subscription subscription;
    private List<ByteBuffer> current = List.of();
    private int index;
    private boolean demandConsumed;
    private boolean eof;

    private TimedHttpBody(Duration timeout) {
        Duration safe = timeout == null || timeout.isNegative() || timeout.isZero()
                ? Duration.ofSeconds(60) : timeout;
        timeoutNanos = safe.compareTo(Duration.ofHours(1)) > 0
                ? Duration.ofHours(1).toNanos() : safe.toNanos();
    }

    static HttpResponse.BodyHandler<InputStream> handler(Duration timeout) {
        return info -> new TimedHttpBody(timeout);
    }

    @Override public CompletionStage<InputStream> getBody() { return body; }

    @Override public void onSubscribe(Flow.Subscription value) {
        if (subscription != null || closed.get()) { value.cancel(); return; }
        subscription = value;
        if (closed.get()) { value.cancel(); return; }
        body.complete(this);
        value.request(1);
    }

    @Override public void onNext(List<ByteBuffer> buffers) {
        if (!closed.get()) offer(new Event(buffers, null, false));
    }

    @Override public void onError(Throwable failure) {
        body.completeExceptionally(failure);
        if (!closed.get()) offer(new Event(List.of(), failure, true));
    }

    @Override public void onComplete() {
        if (!closed.get()) offer(END);
    }

    private void offer(Event event) {
        // Reactive Streams delivers serially and only one data item is outstanding.
        if (!events.offer(event)) {
            close();
            body.completeExceptionally(new IOException("HTTP body backpressure violated"));
        }
    }

    @Override public int read() throws IOException {
        byte[] one = new byte[1];
        int count = read(one, 0, 1);
        return count < 0 ? -1 : one[0] & 0xff;
    }

    @Override public int read(byte[] target, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, target.length);
        if (length == 0) return 0;
        if (closed.get()) throw new IOException("HTTP body closed");
        if (eof) return -1;
        long deadline = System.nanoTime() + timeoutNanos;
        while (true) {
            if (closed.get()) throw new IOException("HTTP body closed");
            while (index < current.size()) {
                ByteBuffer buffer = current.get(index);
                if (!buffer.hasRemaining()) { index++; continue; }
                int count = Math.min(length, buffer.remaining());
                buffer.get(target, offset, count);
                return count;
            }
            current = List.of();
            index = 0;
            if (demandConsumed) {
                demandConsumed = false;
                subscription.request(1);
            }
            Event event;
            try {
                event = events.poll(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            } catch (InterruptedException interrupted) {
                close();
                Thread.currentThread().interrupt();
                throw new IOException("HTTP body interrupted", interrupted);
            }
            if (event == null) {
                close();
                throw new SocketTimeoutException("HTTP body read timed out");
            }
            if (closed.get()) throw new IOException("HTTP body closed");
            if (event.failure() != null) {
                close();
                throw new IOException("HTTP body read failed", event.failure());
            }
            if (event.end()) {
                eof = true;
                return -1;
            }
            current = event.bytes();
            demandConsumed = true;
        }
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        Flow.Subscription active = subscription;
        if (active != null) active.cancel();
        events.clear();
        events.offer(END);
    }
}
