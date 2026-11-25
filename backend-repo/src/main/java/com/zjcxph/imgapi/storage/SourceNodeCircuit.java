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
    private static final int HISTORY_LIMIT = 16;
    private final java.util.ArrayDeque<java.util.Map<String, Object>> events = new java.util.ArrayDeque<>();
    private long opened, recovered, probes, sequence;
    private final java.util.UUID circuitId = java.util.UUID.randomUUID();

    private void event(String type) {
        if (events.size() == HISTORY_LIMIT) events.removeFirst();
        events.addLast(java.util.Map.of("sequence", ++sequence, "type", type,
                "at", java.time.Instant.now().toString()));
    }

    synchronized java.util.Map<String, Object> history() {
        return java.util.Map.of("circuitId", circuitId.toString(), "opened", opened, "recovered", recovered, "probes", probes,
                "events", java.util.List.copyOf(events));
    }

    SourceNodeCircuit(int threshold, Duration cooldown, LongSupplier clock) {
        this.threshold = Math.max(1, threshold);
        this.cooldown = cooldown.compareTo(Duration.ofHours(1)) > 0
                ? Duration.ofHours(1).toNanos() : Math.max(1, cooldown.toNanos());
        this.clock = clock;
    }

    synchronized String state() {
        if (!open) return "CLOSED";
        if (probing) return "HALF_OPEN_ACTIVE";
        return retryAt - clock.getAsLong() > 0 ? "OPEN" : "HALF_OPEN_READY";
    }

    synchronized Attempt begin() throws IOException {
        if (!open) return new Attempt(generation, false);
        if (probing || retryAt - clock.getAsLong() > 0) throw new CircuitOpen();
        probing = true;
        probes++;
        event("PROBE_STARTED");
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
                event("METADATA_ONLY");
            }
            return;
        }
        boolean success = outcome == Outcome.SUCCESS || outcome == Outcome.METADATA_SUCCESS;
        if (attempt.halfOpen()) {
            probing = false;
            if (success) {
                open = false;
                recovered++;
                event("RECOVERED");
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
        opened++;
        event("OPENED");
        probing = false;
        retryAt = clock.getAsLong() + cooldown;
    }

    static final class CircuitOpen extends IOException {
        CircuitOpen() { super("Image source temporarily unavailable"); }
    }
}
