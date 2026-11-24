package com.zjcxph.imgapi.storage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Passive, redacted snapshot; never initializes a client or a circuit. */
final class NetworkRuntimeSnapshot {
    private NetworkRuntimeSnapshot() {}

    static Map<String, Object> of(SourcePermitGuard guard, Collection<String> configured,
                                  Map<String, NetworkSourceCircuit> circuits) {
        var keys = configured.stream().distinct().sorted().toList();
        var nodes = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < keys.size(); i++) {
            var circuit = circuits.get(keys.get(i));
            nodes.add(Map.of("ordinal", i + 1, "state", circuit == null ? "UNOBSERVED" : circuit.state()));
        }
        return Map.of("inUse", guard.inUse(), "limit", guard.limit(), "waiting", guard.waiting(),
                "nodes", List.copyOf(nodes));
    }
}
