package com.zjcxph.imgapi.storage;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class NasOperationIsolationTest {
    @Test
    void successfulMetadataCallReturnsWithNodeSlotAvailable() throws Exception {
        // Independent sources create scheduling pressure without legitimately
        // contending for the same node slot. A completed call must release it.
        try (var executor = Executors.newFixedThreadPool(4)) {
            var checks = new ArrayList<Future<Integer>>();
            for (int worker = 0; worker < 4; worker++) {
                checks.add(executor.submit(() -> {
                    try (var isolation = new NasOperationIsolation(2, Set.of("synthetic-a", "synthetic-b"))) {
                        for (int i = 0; i < 100000; i++) {
                            long size = isolation.<Long>call("synthetic-a", Duration.ofSeconds(2), started -> {
                                started.run();
                                return 9L;
                            }, ignored -> {});
                            assertThat(size).isEqualTo(9L);
                        }
                    }
                    return 100000;
                }));
            }
            for (var check : checks) {
                assertThat(check.get(60, TimeUnit.SECONDS)).isEqualTo(100000);
            }
        }
    }
}
