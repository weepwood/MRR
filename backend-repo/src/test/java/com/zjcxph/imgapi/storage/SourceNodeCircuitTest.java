package com.zjcxph.imgapi.storage;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceNodeCircuitTest {
    @Test
    void opensOnNativeTimeoutsAndAllowsOnlyOneHalfOpenProbe() throws Exception {
        var now = new AtomicLong();
        var circuit = new SourceNodeCircuit(3, Duration.ofSeconds(30), now::get);
        var old = circuit.begin();
        for (int i = 0; i < 3; i++) {
            var attempt = circuit.begin();
            circuit.finish(attempt, SourceNodeCircuit.Outcome.SOURCE_FAILURE);
        }
        circuit.finish(old, SourceNodeCircuit.Outcome.SUCCESS); // stale result cannot close a newer circuit
        assertThatThrownBy(circuit::begin).isInstanceOf(SourceNodeCircuit.CircuitOpen.class);
        now.addAndGet(Duration.ofSeconds(30).toNanos());
        var probe = circuit.begin();
        assertThatThrownBy(circuit::begin).isInstanceOf(SourceNodeCircuit.CircuitOpen.class);
        circuit.finish(probe, SourceNodeCircuit.Outcome.NEUTRAL);
        assertThatThrownBy(circuit::begin).isInstanceOf(SourceNodeCircuit.CircuitOpen.class);
        now.addAndGet(Duration.ofSeconds(30).toNanos());
        circuit.finish(circuit.begin(), SourceNodeCircuit.Outcome.SUCCESS);
        circuit.begin();
        circuit.begin();
    }

    @Test
    void simultaneousHalfOpenCompetitorsHaveExactlyOneWinner() throws Exception {
        var now = new AtomicLong();
        var circuit = new SourceNodeCircuit(3, Duration.ofSeconds(30), now::get);
        for (int i = 0; i < 3; i++) circuit.finish(circuit.begin(), SourceNodeCircuit.Outcome.BODY_FAILURE);
        now.addAndGet(Duration.ofSeconds(30).toNanos());
        var start = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        var futures = new java.util.ArrayList<java.util.concurrent.Future<SourceNodeCircuit.Attempt>>();
        try {
            for (int i = 0; i < 8; i++) futures.add(pool.submit(() -> {
                start.await();
                try { return circuit.begin(); } catch (SourceNodeCircuit.CircuitOpen rejected) { return null; }
            }));
            start.countDown();
            var winners = new java.util.ArrayList<SourceNodeCircuit.Attempt>();
            for (var future : futures) {
                var attempt = future.get(3, java.util.concurrent.TimeUnit.SECONDS);
                if (attempt != null) winners.add(attempt);
            }
            org.assertj.core.api.Assertions.assertThat(winners).hasSize(1);
            org.assertj.core.api.Assertions.assertThat(circuit.state()).isEqualTo("HALF_OPEN_ACTIVE");
            circuit.finish(winners.getFirst(), SourceNodeCircuit.Outcome.SUCCESS);
            org.assertj.core.api.Assertions.assertThat(circuit.state()).isEqualTo("CLOSED");
        } finally { pool.shutdownNow(); }
    }

    @Test
    void historyIsBoundedAndReportsActualTransitionsNotReadPolling() throws Exception {
        var now = new AtomicLong();
        var circuit = new SourceNodeCircuit(1, Duration.ofSeconds(30), now::get);
        for (int i = 0; i < 20; i++) {
            circuit.finish(circuit.begin(), SourceNodeCircuit.Outcome.BODY_FAILURE);
            now.addAndGet(Duration.ofSeconds(30).toNanos());
            circuit.state(); circuit.state();
            circuit.finish(circuit.begin(), SourceNodeCircuit.Outcome.SUCCESS);
        }
        // Existing circuit can recover, but had no transition evidence available before this change.
        var snapshot = circuit.history();
        org.assertj.core.api.Assertions.assertThat(snapshot.get("opened")).isEqualTo(20L);
        org.assertj.core.api.Assertions.assertThat(snapshot.get("recovered")).isEqualTo(20L);
        var events = (java.util.List<?>) snapshot.get("events");
        org.assertj.core.api.Assertions.assertThat(events).hasSize(16);
        org.assertj.core.api.Assertions.assertThat(events.getLast().toString()).contains("RECOVERED");
        org.assertj.core.api.Assertions.assertThat(circuit.history()).isEqualTo(snapshot);
    }

    @Test
    void neutralFailuresDoNotTripAndSuccessResetsTimeoutCount() throws Exception {
        var now = new AtomicLong();
        var circuit = new SourceNodeCircuit(3, Duration.ofSeconds(30), now::get);
        for (int i = 0; i < 20; i++) circuit.finish(circuit.begin(), SourceNodeCircuit.Outcome.NEUTRAL);
        for (int i = 0; i < 2; i++) circuit.finish(circuit.begin(), SourceNodeCircuit.Outcome.SOURCE_FAILURE);
        circuit.finish(circuit.begin(), SourceNodeCircuit.Outcome.SUCCESS);
        for (int i = 0; i < 2; i++) circuit.finish(circuit.begin(), SourceNodeCircuit.Outcome.SOURCE_FAILURE);
        circuit.begin();
    }
}
