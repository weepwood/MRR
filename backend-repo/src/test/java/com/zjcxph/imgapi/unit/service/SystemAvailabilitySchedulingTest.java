package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.config.SchedulingConfig;
import com.zjcxph.imgapi.repository.SystemAvailabilityRepository;
import com.zjcxph.imgapi.service.SystemAvailabilityService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SystemAvailabilitySchedulingTest {
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    static class SchedulingEnabled {}

    @Test
    void actualSchedulerKeepsHeartbeatIndependentAndRetriesFailedRetention() throws Exception {
        var repository = mock(SystemAvailabilityRepository.class);
        var jdbc = mock(JdbcTemplate.class);
        var registry = new SimpleMeterRegistry();
        var entered = new CountDownLatch(1);
        var heartbeatWhileCleaning = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var cutoff = new AtomicReference<Instant>();
        var firstCallAt = new AtomicReference<Instant>();
        when(repository.deleteEndedBefore(any())).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                firstCallAt.set(Instant.now());
                cutoff.set(invocation.getArgument(0));
                entered.countDown();
                if (!release.await(15, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("synthetic cleanup was not released");
                }
                throw new org.springframework.dao.QueryTimeoutException("synthetic timeout");
            }
            return 3;
        });
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenAnswer(invocation -> {
            if (entered.getCount() == 0 && release.getCount() == 1) {
                heartbeatWhileCleaning.countDown();
            }
            return 1;
        });

        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "app.status.retention-days", "30",
                    "app.status.retention-cleanup-initial-delay-ms", "50",
                    "app.status.retention-cleanup-interval-ms", "100",
                    "app.status.initial-delay-ms", "50",
                    "app.status.check-interval-ms", "100"
            )));
            context.register(SchedulingEnabled.class, SchedulingConfig.class, SystemAvailabilityService.class);
            context.registerBean(SystemAvailabilityRepository.class, () -> repository);
            context.registerBean(JdbcTemplate.class, () -> jdbc);
            context.registerBean(MeterRegistry.class, () -> registry);
            Instant before = Instant.now();
            context.refresh();
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS), "retention must be triggered by scheduling");
                assertTrue(heartbeatWhileCleaning.await(10, TimeUnit.SECONDS),
                        "heartbeat must run while cleanup is still blocked");
                assertFalse(cutoff.get().isBefore(before.minus(30, ChronoUnit.DAYS)));
                assertFalse(cutoff.get().isAfter(firstCallAt.get().minus(30, ChronoUnit.DAYS)));
                assertEquals(0, registry.get("mrr.status.retention.last.success.timestamp").gauge().value());
            } finally {
                release.countDown();
            }
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                assertEquals(1, registry.get("mrr.status.retention.failures").gauge().value());
                assertTrue(registry.get("mrr.status.retention.deleted").gauge().value() >= 3);
                assertTrue(registry.get("mrr.status.retention.last.success.timestamp").gauge().value() > 0);
            });
        } finally {
            release.countDown();
            registry.close();
        }
    }

    @Test
    void nonPositiveRetentionConfigurationIsClampedToOneDay() {
        var repository = mock(SystemAvailabilityRepository.class);
        var jdbc = mock(JdbcTemplate.class);
        var service = new SystemAvailabilityService(repository, jdbc, true, 120000, 3000, "", 0, "UTC");
        Instant before = Instant.now().minus(1, ChronoUnit.DAYS);
        service.cleanupHistory();
        Instant after = Instant.now().minus(1, ChronoUnit.DAYS);
        var cutoff = org.mockito.ArgumentCaptor.forClass(Instant.class);
        verify(repository).deleteEndedBefore(cutoff.capture());
        assertFalse(cutoff.getValue().isBefore(before));
        assertFalse(cutoff.getValue().isAfter(after));
        verifyNoInteractions(jdbc);
    }
}
