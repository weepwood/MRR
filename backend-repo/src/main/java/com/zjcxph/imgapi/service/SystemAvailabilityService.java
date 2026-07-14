package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.repository.SystemAvailabilityRepository;
import com.zjcxph.imgapi.repository.SystemAvailabilityRepository.Period;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SystemAvailabilityService {

    private static final Logger logger = LoggerFactory.getLogger(SystemAvailabilityService.class);
    private static final String STATUS_UP = "UP";
    private static final String STATUS_DOWN = "DOWN";
    private static final String STATUS_NO_DATA = "NO_DATA";
    private static final String FRONTEND_HEALTH_BODY = "MRR_FRONTEND_OK";

    private final SystemAvailabilityRepository repository;
    private final JdbcTemplate jdbcTemplate;
    private final boolean enabled;
    private final Duration heartbeatTimeout;
    private final Duration requestTimeout;
    private final String frontendHealthUrl;
    private final int retentionDays;
    private final ZoneId zoneId;
    private final HttpClient httpClient;

    public SystemAvailabilityService(
            SystemAvailabilityRepository repository,
            JdbcTemplate jdbcTemplate,
            @Value("${app.status.enabled:true}") boolean enabled,
            @Value("${app.status.heartbeat-timeout-ms:120000}") long heartbeatTimeoutMs,
            @Value("${app.status.request-timeout-ms:3000}") long requestTimeoutMs,
            @Value("${app.status.frontend-health-url:}") String frontendHealthUrl,
            @Value("${app.status.retention-days:365}") int retentionDays,
            @Value("${app.status.zone-id:Asia/Shanghai}") String zoneId
    ) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
        this.enabled = enabled;
        this.heartbeatTimeout = Duration.ofMillis(Math.max(1_000L, heartbeatTimeoutMs));
        this.requestTimeout = Duration.ofMillis(Math.max(500L, requestTimeoutMs));
        this.frontendHealthUrl = frontendHealthUrl == null ? "" : frontendHealthUrl.trim();
        this.retentionDays = Math.max(1, retentionDays);
        this.zoneId = ZoneId.of(zoneId);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.requestTimeout)
                .build();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public synchronized void initialize() {
        if (!enabled) {
            logger.info("System availability history is disabled");
            return;
        }

        try {
            runHeartbeatCycle(true);
            logger.info("System availability history initialized");
        } catch (Exception exception) {
            logger.warn("Unable to initialize system availability history: {}", exception.getMessage());
        }
    }

    @Scheduled(
            fixedDelayString = "${app.status.check-interval-ms:60000}",
            initialDelayString = "${app.status.initial-delay-ms:60000}"
    )
    @Transactional
    public synchronized void heartbeat() {
        if (!enabled) {
            return;
        }

        try {
            runHeartbeatCycle(false);
        } catch (Exception exception) {
            // 数据库不可用时无法即时写入 DOWN；恢复后会根据最后心跳缺口补录停机区间。
            logger.warn("Unable to persist system availability heartbeat: {}", exception.getMessage());
        }
    }

    public Map<String, Object> getSummary(int requestedDays) {
        int days = normalizeDays(requestedDays);
        Instant rangeEnd = Instant.now();
        Instant rangeStart = rangeEnd.minus(days, ChronoUnit.DAYS);
        List<Period> periods = repository.findOverlapping(rangeStart, rangeEnd);
        Optional<Period> current = repository.findOpenPeriod();
        DurationSummary durationSummary = summarize(periods, rangeStart, rangeEnd);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currentStatus", current.map(Period::status).orElse(STATUS_NO_DATA));
        result.put("currentStatusSince", current.map(Period::startedAt).orElse(null));
        result.put("lastCheckedAt", current.map(Period::lastHeartbeatAt).orElse(null));
        result.put("rangeStartedAt", rangeStart);
        result.put("rangeEndedAt", rangeEnd);
        result.put("days", days);
        result.put("uptimePercentage", durationSummary.uptimePercentage());
        result.put("monitoredSeconds", durationSummary.coverageSeconds());
        result.put("downtimeSeconds", durationSummary.downtimeSeconds());
        return result;
    }

    public List<Map<String, Object>> getDaily(int requestedDays) {
        int days = normalizeDays(requestedDays);
        LocalDate today = LocalDate.now(zoneId);
        LocalDate firstDay = today.minusDays(days - 1L);
        Instant rangeStart = firstDay.atStartOfDay(zoneId).toInstant();
        Instant rangeEnd = Instant.now();
        List<Period> periods = repository.findOverlapping(rangeStart, rangeEnd);
        List<Map<String, Object>> result = new ArrayList<>(days);

        for (int offset = 0; offset < days; offset++) {
            LocalDate date = firstDay.plusDays(offset);
            Instant dayStart = date.atStartOfDay(zoneId).toInstant();
            Instant dayEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant();
            if (dayEnd.isAfter(rangeEnd)) {
                dayEnd = rangeEnd;
            }

            DurationSummary summary = summarize(periods, dayStart, dayEnd);
            String status = summary.coverageSeconds() == 0
                    ? STATUS_NO_DATA
                    : summary.downtimeSeconds() > 0 ? STATUS_DOWN : STATUS_UP;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date);
            item.put("status", status);
            item.put("uptimePercentage", summary.uptimePercentage());
            item.put("monitoredSeconds", summary.coverageSeconds());
            item.put("downtimeSeconds", summary.downtimeSeconds());
            result.add(item);
        }

        return result;
    }

    public List<Map<String, Object>> getIncidents(int requestedDays) {
        int days = normalizeDays(requestedDays);
        Instant rangeEnd = Instant.now();
        Instant rangeStart = rangeEnd.minus(days, ChronoUnit.DAYS);
        List<Map<String, Object>> incidents = new ArrayList<>();

        for (Period period : repository.findOverlapping(rangeStart, rangeEnd)) {
            if (!STATUS_DOWN.equals(period.status())) {
                continue;
            }

            Instant startedAt = max(period.startedAt(), rangeStart);
            Instant endedAt = min(period.endedAt() == null ? rangeEnd : period.endedAt(), rangeEnd);
            Map<String, Object> incident = new LinkedHashMap<>();
            incident.put("startedAt", startedAt);
            incident.put("endedAt", period.endedAt() == null ? null : endedAt);
            incident.put("durationSeconds", positiveSeconds(startedAt, endedAt));
            incident.put("ongoing", period.endedAt() == null);
            incident.put("reason", sanitizeReason(period.reason()));
            incidents.add(incident);
        }

        incidents.sort((left, right) -> ((Instant) right.get("startedAt")).compareTo((Instant) left.get("startedAt")));
        return incidents;
    }

    public Map<String, Object> ping() {
        return Map.of(
                "status", STATUS_UP,
                "timestamp", Instant.now()
        );
    }

    private void runHeartbeatCycle(boolean cleanupOldPeriods) {
        Instant now = Instant.now();
        reconcileHeartbeatGap(now);
        recordCurrentStatus(now, checkCurrentStatus());
        if (cleanupOldPeriods) {
            repository.deleteEndedBefore(now.minus(retentionDays, ChronoUnit.DAYS));
        }
    }

    private void reconcileHeartbeatGap(Instant now) {
        Optional<Period> currentOptional = repository.findOpenPeriod();
        if (currentOptional.isEmpty()) {
            return;
        }

        Period current = currentOptional.get();
        Instant inferredDownAt = current.lastHeartbeatAt().plus(heartbeatTimeout);
        if (!STATUS_UP.equals(current.status()) || !inferredDownAt.isBefore(now)) {
            return;
        }

        repository.close(current.id(), inferredDownAt);
        repository.insertOpen(STATUS_DOWN, inferredDownAt, now, "服务心跳中断");
    }

    private void recordCurrentStatus(Instant now, HealthResult healthResult) {
        Optional<Period> currentOptional = repository.findOpenPeriod();
        if (currentOptional.isEmpty()) {
            repository.insertOpen(healthResult.status(), now, now, healthResult.reason());
            return;
        }

        Period current = currentOptional.get();
        if (current.status().equals(healthResult.status())) {
            repository.updateHeartbeat(current.id(), now, healthResult.reason());
            return;
        }

        repository.close(current.id(), now);
        repository.insertOpen(healthResult.status(), now, now, healthResult.reason());
    }

    private HealthResult checkCurrentStatus() {
        try {
            Integer value = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (value == null || value != 1) {
                return HealthResult.down("数据库健康检查失败");
            }
        } catch (Exception exception) {
            return HealthResult.down("数据库连接失败");
        }

        if (frontendHealthUrl.isBlank()) {
            return HealthResult.up();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(frontendHealthUrl))
                    .timeout(requestTimeout)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || !response.body().contains(FRONTEND_HEALTH_BODY)) {
                return HealthResult.down("前端服务健康检查失败");
            }
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return HealthResult.down("前端服务无法访问");
        }

        return HealthResult.up();
    }

    private DurationSummary summarize(List<Period> periods, Instant rangeStart, Instant rangeEnd) {
        long coverageSeconds = 0;
        long downtimeSeconds = 0;

        for (Period period : periods) {
            Instant startedAt = max(period.startedAt(), rangeStart);
            Instant endedAt = min(period.endedAt() == null ? rangeEnd : period.endedAt(), rangeEnd);
            long seconds = positiveSeconds(startedAt, endedAt);
            coverageSeconds += seconds;
            if (STATUS_DOWN.equals(period.status())) {
                downtimeSeconds += seconds;
            }
        }

        Double uptimePercentage = coverageSeconds == 0
                ? null
                : roundPercentage((coverageSeconds - downtimeSeconds) * 100.0 / coverageSeconds);
        return new DurationSummary(coverageSeconds, downtimeSeconds, uptimePercentage);
    }

    private int normalizeDays(int requestedDays) {
        return Math.max(1, Math.min(requestedDays, 365));
    }

    private static long positiveSeconds(Instant startedAt, Instant endedAt) {
        if (!endedAt.isAfter(startedAt)) {
            return 0;
        }
        return Duration.between(startedAt, endedAt).getSeconds();
    }

    private static Instant max(Instant left, Instant right) {
        return left.isAfter(right) ? left : right;
    }

    private static Instant min(Instant left, Instant right) {
        return left.isBefore(right) ? left : right;
    }

    private static double roundPercentage(double value) {
        return Math.round(value * 1_000.0) / 1_000.0;
    }

    private static String sanitizeReason(String reason) {
        return reason == null || reason.isBlank() ? "服务不可用" : reason;
    }

    private record HealthResult(String status, String reason) {
        private static HealthResult up() {
            return new HealthResult(STATUS_UP, null);
        }

        private static HealthResult down(String reason) {
            return new HealthResult(STATUS_DOWN, reason);
        }
    }

    private record DurationSummary(long coverageSeconds, long downtimeSeconds, Double uptimePercentage) {
    }
}
