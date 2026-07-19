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
        String lastFailure = reliableAuditService.getLastFailure();
        Health.Builder builder = queued > 10_000 ? Health.outOfService() : Health.up();
        return builder
                .withDetail("queuedEvents", queued)
                .withDetail("spoolFile", reliableAuditService.getSpoolFile().toString())
                .withDetail("lastFailure", lastFailure == null ? "none" : lastFailure)
                .build();
    }
}
