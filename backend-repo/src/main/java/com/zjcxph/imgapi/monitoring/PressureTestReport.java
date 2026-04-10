package com.zjcxph.imgapi.monitoring;

import lombok.Data;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Data
public class PressureTestReport {

    private String runId;
    private String name;
    private String targetUrl;
    private String method;
    private int concurrency;
    private int totalRequests;
    private int successCount;
    private int failureCount;
    private double successRate;
    private long minLatencyMs;
    private long avgLatencyMs;
    private long p95LatencyMs;
    private long maxLatencyMs;
    private double requestsPerSecond;
    private long durationMillis;
    private String startedAt;
    private String finishedAt;
    private PressureTestSnapshot beforeSnapshot;
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
}
