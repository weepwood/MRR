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
