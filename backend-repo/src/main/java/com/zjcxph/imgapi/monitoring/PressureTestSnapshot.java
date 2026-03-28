package com.zjcxph.imgapi.monitoring;

public record PressureTestSnapshot(
        long heapUsedBytes,
        long heapCommittedBytes,
        long heapMaxBytes,
        double heapUsagePercent,
        double systemLoadAverage,
        int availableProcessors,
        String timestamp
) {
}
