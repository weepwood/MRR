package com.zjcxph.imgapi.health;

import com.zjcxph.imgapi.service.ReliableAuditService;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("auditSpool")
public class AuditSpoolHealthIndicator implements HealthIndicator {

    private final ReliableAuditService reliableAuditService;

    public AuditSpoolHealthIndicator(ReliableAuditService reliableAuditService) {
        this.reliableAuditService = reliableAuditService;
    }

    @Override
    public Health health() {
        long queued = reliableAuditService.getQueuedEvents();
        long deadLetters = reliableAuditService.getDeadLetterEvents();
        String lastFailure = reliableAuditService.getLastFailure();

        Health.Builder builder;
        if (!reliableAuditService.isHealthy()) {
            builder = Health.down();
        } else if (queued > 10_000) {
            builder = Health.outOfService();
        } else {
            builder = Health.up();
        }

        return builder
                .withDetail("status", reliableAuditService.isDegraded() ? "DEGRADED" : (reliableAuditService.isHealthy() ? "UP" : "DOWN"))
                .withDetail("queuedEvents", queued)
                .withDetail("deadLetterEvents", deadLetters)
                .withDetail("fallbackAvailable", reliableAuditService.isFallbackAvailable())
                .withDetail("lostEventDetected", reliableAuditService.isLostEventDetected())
                .withDetail("lastFailure", lastFailure == null ? "none" : lastFailure)
                .withDetail("lastFailureAt", reliableAuditService.getLastFailureAt() == null
                        ? "none"
                        : reliableAuditService.getLastFailureAt().toString())
                .build();
    }
}
