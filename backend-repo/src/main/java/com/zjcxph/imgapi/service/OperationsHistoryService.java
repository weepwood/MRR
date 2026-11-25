package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.repository.OperationsHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class OperationsHistoryService {
    private static final Logger log = LoggerFactory.getLogger(OperationsHistoryService.class);
    private final OperationsCenterService center;
    private final OperationsHistoryRepository repository;
    private final boolean enabled;
    private volatile Instant lastWarning = Instant.EPOCH;

    public OperationsHistoryService(OperationsCenterService center, OperationsHistoryRepository repository,
            @Value("${app.operations.history.enabled:false}") boolean enabled) {
        this.center = center; this.repository = repository; this.enabled = enabled;
    }

    public record History(boolean enabled, int hours, int bucketSeconds, Instant from, Instant to,
                          List<OperationsHistoryRepository.Sample> samples) {}

    public History history(int hours) {
        if (hours != 24 && hours != 168) throw new BusinessException(400,"仅支持最近24小时或7天");
        Instant now = Instant.now();
        Instant from = now.minus(hours,ChronoUnit.HOURS);
        return new History(enabled,hours,OperationsHistoryRepository.BUCKET_SECONDS,from,now,
                enabled ? repository.read(from,now) : List.of());
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void sample() {
        if (!enabled) return;
        Instant now = Instant.now();
        try {
            var sample = center.historySample(now);
            if (sample != null) {
                repository.record(sample);
                repository.prune(now.minus(7,ChronoUnit.DAYS));
            }
        } catch (RuntimeException ignored) {
            // Bounded warning without connection details or source identifiers; no readiness side effects.
            if (lastWarning.plusSeconds(3600).isBefore(now)) {
                lastWarning = now;
                log.warn("运维历史采样暂不可用");
            }
        }
    }
}
