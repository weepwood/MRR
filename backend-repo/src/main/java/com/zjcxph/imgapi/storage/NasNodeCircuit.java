package com.zjcxph.imgapi.storage;

import java.io.IOException;
import java.time.Duration;
import java.util.function.LongSupplier;

/** A narrow circuit for observed native NAS timeouts, not file-specific or permission failures. */
final class NasNodeCircuit {
    enum Outcome { SUCCESS, NATIVE_TIMEOUT, NEUTRAL }
    record Attempt(long generation, boolean halfOpen) {}
    private final int threshold;
    private final long cooldown;
    private final LongSupplier clock;
    private long generation;
    private int timeouts;
    private long retryAt;
    private boolean open;
    private boolean probing;

    NasNodeCircuit(int threshold, Duration cooldown, LongSupplier clock) {
        this.threshold = Math.max(1, threshold);
        this.cooldown = cooldown.compareTo(Duration.ofHours(1)) > 0
                ? Duration.ofHours(1).toNanos() : Math.max(1, cooldown.toNanos());
        this.clock = clock;
    }

    synchronized Attempt begin() throws IOException {
        if (!open) return new Attempt(generation, false);
        if (probing || retryAt - clock.getAsLong() > 0) throw new CircuitOpen();
        probing = true;
        return new Attempt(generation, true);
    }

    synchronized void finish(Attempt attempt, Outcome outcome) {
        if (attempt.generation() != generation) return;
        if (attempt.halfOpen()) {
            probing = false;
            if (outcome == Outcome.SUCCESS) {
                open = false;
                timeouts = 0;
                generation++;
            } else {
                // Even a neutral probe cannot establish recovery. Try again after cooldown.
                trip();
            }
        } else if (outcome == Outcome.SUCCESS) {
            timeouts = 0;
        } else if (outcome == Outcome.NATIVE_TIMEOUT && ++timeouts >= threshold) {
            trip();
        }
    }

    private void trip() {
        generation++;
        open = true;
        probing = false;
        retryAt = clock.getAsLong() + cooldown;
    }

    static final class CircuitOpen extends IOException {
        CircuitOpen() { super("NAS node temporarily unavailable"); }
    }
}
