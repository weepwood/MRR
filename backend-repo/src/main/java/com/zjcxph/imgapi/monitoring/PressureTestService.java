package com.zjcxph.imgapi.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class PressureTestService {

    private static final Logger logger = LoggerFactory.getLogger(PressureTestService.class);
    private static final int HISTORY_LIMIT = 20;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final Deque<PressureTestReport> history = new ConcurrentLinkedDeque<>();

    public PressureTestReport run(PressureTestRequest request) {
        Instant started = Instant.now();
        String runId = "pt-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                .withZone(ZoneId.systemDefault())
                .format(started);

        PressureTestSnapshot beforeSnapshot = captureSnapshot();
        List<PressureTestSample> samples = execute(request);
        PressureTestSnapshot afterSnapshot = captureSnapshot();

        long durationMillis = Math.max(1L, Duration.between(started, Instant.now()).toMillis());
        List<Long> latencies = samples.stream()
                .map(PressureTestSample::latencyMillis)
                .sorted()
                .collect(Collectors.toList());

        int successCount = (int) samples.stream().filter(PressureTestSample::success).count();
        int failureCount = samples.size() - successCount;
        double successRate = samples.isEmpty() ? 0.0 : (successCount * 100.0 / samples.size());
        long minLatencyMs = latencies.isEmpty() ? 0L : latencies.getFirst();
        long avgLatencyMs = latencies.isEmpty() ? 0L : Math.round(latencies.stream().mapToLong(Long::longValue).average().orElse(0.0));
        long p95LatencyMs = percentile(latencies, 0.95d);
        long maxLatencyMs = latencies.isEmpty() ? 0L : latencies.getLast();
        double requestsPerSecond = samples.isEmpty() ? 0.0 : (samples.size() * 1000.0 / durationMillis);

        List<PressureTestSample> orderedSamples = samples.stream()
                .sorted(Comparator.comparingInt(PressureTestSample::index))
                .toList();

        PressureTestReport report = new PressureTestReport(
                runId,
                request.name(),
                request.targetUrl(),
                request.method(),
                request.concurrency(),
                request.totalRequests(),
                successCount,
                failureCount,
                round(successRate, 2),
                minLatencyMs,
                avgLatencyMs,
                p95LatencyMs,
                maxLatencyMs,
                round(requestsPerSecond, 2),
                durationMillis,
                formatInstant(started),
                formatInstant(Instant.now()),
                beforeSnapshot,
                afterSnapshot,
                orderedSamples
        );

        history.addFirst(report);
        trimHistory();
        logger.info("pressure test finished: runId={}, targetUrl={}, total={}, success={}, failure={}",
                runId, request.targetUrl(), request.totalRequests(), successCount, failureCount);
        return report;
    }

    public List<PressureTestReport> getHistory() {
        return new ArrayList<>(history);
    }

    public Optional<PressureTestReport> getLatest() {
        return Optional.ofNullable(history.peekFirst());
    }

    public Optional<PressureTestReport> findByRunId(String runId) {
        if (runId == null || runId.isBlank()) {
            return Optional.empty();
        }
        return history.stream().filter(item -> runId.equals(item.runId())).findFirst();
    }

    public void clearHistory() {
        history.clear();
    }

    private List<PressureTestSample> execute(PressureTestRequest request) {
        URI targetUri = URI.create(request.targetUrl());
        ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, request.concurrency()));
        AtomicInteger cursor = new AtomicInteger(0);
        List<PressureTestSample> samples = Collections.synchronizedList(new ArrayList<>(request.totalRequests()));

        try {
            List<java.util.concurrent.Future<?>> workers = new ArrayList<>();
            for (int i = 0; i < request.concurrency(); i++) {
                workers.add(executor.submit(() -> {
                    while (true) {
                        int current = cursor.getAndIncrement();
                        if (current >= request.totalRequests()) {
                            return null;
                        }
                        samples.add(executeSingle(current + 1, request, targetUri));
                    }
                }));
            }

            for (java.util.concurrent.Future<?> worker : workers) {
                try {
                    worker.get();
                } catch (Exception e) {
                    logger.warn("pressure worker failed: {}", e.getMessage(), e);
                }
            }
        } finally {
            executor.shutdownNow();
        }

        return samples;
    }

    private PressureTestSample executeSingle(int index, PressureTestRequest request, URI targetUri) {
        long started = System.nanoTime();
        try {
            HttpRequest httpRequest = buildHttpRequest(request, targetUri);
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long latencyMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
            boolean success = response.statusCode() >= 200 && response.statusCode() < 400;
            return new PressureTestSample(index, response.statusCode(), success, latencyMillis, success ? null : "HTTP " + response.statusCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long latencyMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
            return new PressureTestSample(index, 0, false, latencyMillis, "interrupted");
        } catch (IOException | IllegalArgumentException e) {
            long latencyMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
            return new PressureTestSample(index, 0, false, latencyMillis, e.getMessage());
        }
    }

    private HttpRequest buildHttpRequest(PressureTestRequest request, URI targetUri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(targetUri)
                .timeout(Duration.ofMillis(request.timeoutMillis()));

        Map<String, String> headers = new LinkedHashMap<>(Optional.ofNullable(request.headers()).orElse(Map.of()));
        boolean hasBody = request.body() != null && !request.body().isBlank();
        if (hasBody && headers.keySet().stream().noneMatch(key -> "content-type".equalsIgnoreCase(key))) {
            headers.put("Content-Type", "application/json");
        }

        headers.forEach(builder::header);

        String method = request.method().toUpperCase(Locale.ROOT);
        return switch (method) {
            case "POST", "PUT", "PATCH" -> builder.method(method,
                    hasBody ? HttpRequest.BodyPublishers.ofString(request.body(), StandardCharsets.UTF_8)
                            : HttpRequest.BodyPublishers.noBody())
                    .build();
            case "DELETE" -> builder.DELETE().build();
            default -> builder.GET().build();
        };
    }

    private PressureTestSnapshot captureSnapshot() {
        Runtime runtime = Runtime.getRuntime();
        long heapMax = runtime.maxMemory();
        long heapCommitted = runtime.totalMemory();
        long heapUsed = heapCommitted - runtime.freeMemory();
        double heapPercent = heapMax > 0 ? heapUsed * 100.0 / heapMax : 0.0;
        double loadAverage = java.lang.management.ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
        if (Double.isNaN(loadAverage) || loadAverage < 0) {
            loadAverage = 0.0;
        }
        return new PressureTestSnapshot(
                heapUsed,
                heapCommitted,
                heapMax,
                round(heapPercent, 2),
                round(loadAverage, 2),
                runtime.availableProcessors(),
                formatInstant(Instant.now())
        );
    }

    private long percentile(List<Long> sortedLatencies, double percentile) {
        if (sortedLatencies.isEmpty()) {
            return 0L;
        }
        int index = (int) Math.ceil(sortedLatencies.size() * percentile) - 1;
        index = Math.max(0, Math.min(index, sortedLatencies.size() - 1));
        return sortedLatencies.get(index);
    }

    private double round(double value, int scale) {
        double factor = Math.pow(10, scale);
        return Math.round(value * factor) / factor;
    }

    private String formatInstant(Instant instant) {
        return DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.systemDefault()).format(instant);
    }

    private void trimHistory() {
        while (history.size() > HISTORY_LIMIT) {
            history.removeLast();
        }
    }
}
