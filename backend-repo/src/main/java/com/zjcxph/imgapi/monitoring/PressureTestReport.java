package com.zjcxph.imgapi.monitoring;

import java.util.ArrayList;
import java.util.List;

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
    private List<PressureTestSample> samples;

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

    public String getRunId() {
        return runId;
    }

    public String runId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getName() {
        return name;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public String targetUrl() {
        return targetUrl;
    }

    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    public String getMethod() {
        return method;
    }

    public String method() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public int getConcurrency() {
        return concurrency;
    }

    public int concurrency() {
        return concurrency;
    }

    public void setConcurrency(int concurrency) {
        this.concurrency = concurrency;
    }

    public int getTotalRequests() {
        return totalRequests;
    }

    public int totalRequests() {
        return totalRequests;
    }

    public void setTotalRequests(int totalRequests) {
        this.totalRequests = totalRequests;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public int successCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public int failureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    public double getSuccessRate() {
        return successRate;
    }

    public double successRate() {
        return successRate;
    }

    public void setSuccessRate(double successRate) {
        this.successRate = successRate;
    }

    public long getMinLatencyMs() {
        return minLatencyMs;
    }

    public long minLatencyMs() {
        return minLatencyMs;
    }

    public void setMinLatencyMs(long minLatencyMs) {
        this.minLatencyMs = minLatencyMs;
    }

    public long getAvgLatencyMs() {
        return avgLatencyMs;
    }

    public long avgLatencyMs() {
        return avgLatencyMs;
    }

    public void setAvgLatencyMs(long avgLatencyMs) {
        this.avgLatencyMs = avgLatencyMs;
    }

    public long getP95LatencyMs() {
        return p95LatencyMs;
    }

    public long p95LatencyMs() {
        return p95LatencyMs;
    }

    public void setP95LatencyMs(long p95LatencyMs) {
        this.p95LatencyMs = p95LatencyMs;
    }

    public long getMaxLatencyMs() {
        return maxLatencyMs;
    }

    public long maxLatencyMs() {
        return maxLatencyMs;
    }

    public void setMaxLatencyMs(long maxLatencyMs) {
        this.maxLatencyMs = maxLatencyMs;
    }

    public double getRequestsPerSecond() {
        return requestsPerSecond;
    }

    public double requestsPerSecond() {
        return requestsPerSecond;
    }

    public void setRequestsPerSecond(double requestsPerSecond) {
        this.requestsPerSecond = requestsPerSecond;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public long durationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(long durationMillis) {
        this.durationMillis = durationMillis;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public String startedAt() {
        return startedAt;
    }

    public void setStartedAt(String startedAt) {
        this.startedAt = startedAt;
    }

    public String getFinishedAt() {
        return finishedAt;
    }

    public String finishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(String finishedAt) {
        this.finishedAt = finishedAt;
    }

    public PressureTestSnapshot getBeforeSnapshot() {
        return beforeSnapshot;
    }

    public PressureTestSnapshot beforeSnapshot() {
        return beforeSnapshot;
    }

    public void setBeforeSnapshot(PressureTestSnapshot beforeSnapshot) {
        this.beforeSnapshot = beforeSnapshot;
    }

    public PressureTestSnapshot getAfterSnapshot() {
        return afterSnapshot;
    }

    public PressureTestSnapshot afterSnapshot() {
        return afterSnapshot;
    }

    public void setAfterSnapshot(PressureTestSnapshot afterSnapshot) {
        this.afterSnapshot = afterSnapshot;
    }

    public List<PressureTestSample> getSamples() {
        return samples;
    }

    public List<PressureTestSample> samples() {
        return samples;
    }

    public void setSamples(List<PressureTestSample> samples) {
        this.samples = samples == null ? new ArrayList<>() : new ArrayList<>(samples);
    }
}
