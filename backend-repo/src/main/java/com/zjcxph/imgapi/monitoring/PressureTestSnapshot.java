package com.zjcxph.imgapi.monitoring;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class PressureTestSnapshot {

    private long heapUsedBytes;
    private long heapCommittedBytes;
    private long heapMaxBytes;
    private double heapUsagePercent;
    private double systemLoadAverage;
    private int availableProcessors;
    private String timestamp;

    public PressureTestSnapshot() {
    }

    public PressureTestSnapshot(
            long heapUsedBytes,
            long heapCommittedBytes,
            long heapMaxBytes,
            double heapUsagePercent,
            double systemLoadAverage,
            int availableProcessors,
            String timestamp
    ) {
        this.heapUsedBytes = heapUsedBytes;
        this.heapCommittedBytes = heapCommittedBytes;
        this.heapMaxBytes = heapMaxBytes;
        this.heapUsagePercent = heapUsagePercent;
        this.systemLoadAverage = systemLoadAverage;
        this.availableProcessors = availableProcessors;
        this.timestamp = timestamp;
    }

    public long heapUsedBytes() {
        return heapUsedBytes;
    }

    public long heapCommittedBytes() {
        return heapCommittedBytes;
    }

    public long heapMaxBytes() {
        return heapMaxBytes;
    }

    public double heapUsagePercent() {
        return heapUsagePercent;
    }

    public double systemLoadAverage() {
        return systemLoadAverage;
    }

    public int availableProcessors() {
        return availableProcessors;
    }

    public String timestamp() {
        return timestamp;
    }

}
