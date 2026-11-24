package com.zjcxph.imgapi.storage;

import java.io.IOException;
import java.time.Duration;
import java.util.function.LongSupplier;

/** A node-scoped circuit for observed source failures, not file-specific or permission failures. */
final class SourceNodeCircuit {
    enum Outcome { SUCCESS, METADATA_SUCCESS, SOURCE_FAILURE, BODY_FAILURE, NEUTRAL }
    record Attempt(long generation, boolean halfOpen) {}
    private final int threshold;
    private final long cooldown;
    private final LongSupplier clock;
    private long generation;
    private int timeouts;
    private long retryAt;
    private boolean open;
    private boolean probing;
    private boolean bodyRecoveryRequired;

    SourceNodeCircuit(int threshold, Duration cooldown, LongSupplier clock) {
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
        if (outcome == Outcome.BODY_FAILURE) bodyRecoveryRequired = true;
        if (outcome == Outcome.METADATA_SUCCESS && bodyRecoveryRequired) {
            // Metadata alone cannot clear observed body failures. A successful half-open
            // metadata probe frees the single probe for a real body read immediately.
            if (attempt.halfOpen()) {
                probing = false;
                generation++;
            }
            return;
        }
        boolean success = outcome == Outcome.SUCCESS || outcome == Outcome.METADATA_SUCCESS;
        if (attempt.halfOpen()) {
            probing = false;
            if (success) {
                open = false;
                timeouts = 0;
                bodyRecoveryRequired = false;
                generation++;
            } else {
                trip();
            }
        } else if (success) {
            timeouts = 0;
            bodyRecoveryRequired = false;
        } else if ((outcome == Outcome.SOURCE_FAILURE || outcome == Outcome.BODY_FAILURE)
                && ++timeouts >= threshold) {
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
        CircuitOpen() { super("Image source temporarily unavailable"); }
    }
}
