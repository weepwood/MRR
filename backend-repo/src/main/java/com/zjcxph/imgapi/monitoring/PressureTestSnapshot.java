package com.zjcxph.imgapi.monitoring;

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

    public long getHeapUsedBytes() {
        return heapUsedBytes;
    }

    public long heapUsedBytes() {
        return heapUsedBytes;
    }

    public void setHeapUsedBytes(long heapUsedBytes) {
        this.heapUsedBytes = heapUsedBytes;
    }

    public long getHeapCommittedBytes() {
        return heapCommittedBytes;
    }

    public long heapCommittedBytes() {
        return heapCommittedBytes;
    }

    public void setHeapCommittedBytes(long heapCommittedBytes) {
        this.heapCommittedBytes = heapCommittedBytes;
    }

    public long getHeapMaxBytes() {
        return heapMaxBytes;
    }

    public long heapMaxBytes() {
        return heapMaxBytes;
    }

    public void setHeapMaxBytes(long heapMaxBytes) {
        this.heapMaxBytes = heapMaxBytes;
    }

    public double getHeapUsagePercent() {
        return heapUsagePercent;
    }

    public double heapUsagePercent() {
        return heapUsagePercent;
    }

    public void setHeapUsagePercent(double heapUsagePercent) {
        this.heapUsagePercent = heapUsagePercent;
    }

    public double getSystemLoadAverage() {
        return systemLoadAverage;
    }

    public double systemLoadAverage() {
        return systemLoadAverage;
    }

    public void setSystemLoadAverage(double systemLoadAverage) {
        this.systemLoadAverage = systemLoadAverage;
    }

    public int getAvailableProcessors() {
        return availableProcessors;
    }

    public int availableProcessors() {
        return availableProcessors;
    }

    public void setAvailableProcessors(int availableProcessors) {
        this.availableProcessors = availableProcessors;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String timestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
