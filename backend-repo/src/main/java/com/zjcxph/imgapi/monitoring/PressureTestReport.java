package com.zjcxph.imgapi.monitoring;

import java.util.ArrayList;
import java.util.List;

public record PressureTestReport(
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
    public PressureTestReport {
        if (samples == null) {
            samples = new ArrayList<>();
        }
    }
}
