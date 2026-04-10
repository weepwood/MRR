package com.zjcxph.imgapi.monitoring;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class PressureTestReport {

    @Setter
    private String runId;
    @Setter
    private String name;
    @Setter
    private String targetUrl;
    @Setter
    private String method;
    @Setter
    private int concurrency;
    @Setter
    private int totalRequests;
    @Setter
    private int successCount;
    @Setter
    private int failureCount;
    @Setter
    private double successRate;
    @Setter
    private long minLatencyMs;
    @Setter
    private long avgLatencyMs;
    @Setter
    private long p95LatencyMs;
    @Setter
    private long maxLatencyMs;
    @Setter
    private double requestsPerSecond;
    @Setter
    private long durationMillis;
    @Setter
    private String startedAt;
    @Setter
    private String finishedAt;
    @Setter
    private PressureTestSnapshot beforeSnapshot;
    @Setter
    private PressureTestSnapshot afterSnapshot;
    private final List<PressureTestSample> samples;

    public PressureTestReport() {
        this.samples = new ArrayList<>();
    }

    public PressureTestReport(
            String runId,
            String name,
            String targetUrl,
            String method,
            int concurrency,
            int totalRequests,
            int successCount,
            int failureCount,
            double successRate,
            long minLatencyMs,
            long avgLatencyMs,
            long p95LatencyMs,
            long maxLatencyMs,
            double requestsPerSecond,
            long durationMillis,
            String startedAt,
            String finishedAt,
            PressureTestSnapshot beforeSnapshot,
            PressureTestSnapshot afterSnapshot,
            List<PressureTestSample> samples
    ) {
        this.runId = runId;
        this.name = name;
        this.targetUrl = targetUrl;
        this.method = method;
        this.concurrency = concurrency;
        this.totalRequests = totalRequests;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.successRate = successRate;
        this.minLatencyMs = minLatencyMs;
        this.avgLatencyMs = avgLatencyMs;
        this.p95LatencyMs = p95LatencyMs;
        this.maxLatencyMs = maxLatencyMs;
        this.requestsPerSecond = requestsPerSecond;
        this.durationMillis = durationMillis;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.beforeSnapshot = beforeSnapshot;
        this.afterSnapshot = afterSnapshot;
        this.samples = samples == null ? new ArrayList<>() : new ArrayList<>(samples);
    }

    public String runId() {
        return runId;
    }

    public String name() {
        return name;
    }

    public String method() {
        return method;
    }

    public int totalRequests() {
        return totalRequests;
    }

    public int successCount() {
        return successCount;
    }

    public int failureCount() {
        return failureCount;
    }

    public long minLatencyMs() {
        return minLatencyMs;
    }

    public long avgLatencyMs() {
        return avgLatencyMs;
    }

    public long p95LatencyMs() {
        return p95LatencyMs;
    }

    public long maxLatencyMs() {
        return maxLatencyMs;
    }

    public double requestsPerSecond() {
        return requestsPerSecond;
    }

    public PressureTestSnapshot beforeSnapshot() {
        return beforeSnapshot;
    }

    public PressureTestSnapshot afterSnapshot() {
        return afterSnapshot;
    }

    public List<PressureTestSample> samples() {
        return samples;
    }
}
