package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.repository.SystemAvailabilityRepository;
import com.zjcxph.imgapi.repository.SystemAvailabilityRepository.Period;
import com.zjcxph.imgapi.service.SystemAvailabilityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemAvailabilityServiceTest {

    @Mock
    private SystemAvailabilityRepository repository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void summaryUsesRecordedDurationInsteadOfHeartbeatCount() {
        Instant now = Instant.now();
        Period down = new Period(
                1L,
                "DOWN",
                now.minusSeconds(7_200),
                now.minusSeconds(3_600),
                now.minusSeconds(3_600),
                "服务心跳中断"
        );
        Period up = new Period(
                2L,
                "UP",
                now.minusSeconds(3_600),
                null,
                now,
                null
        );
        when(repository.findOverlapping(any(), any())).thenReturn(List.of(down, up));
        when(repository.findOpenPeriod()).thenReturn(Optional.of(up));

        SystemAvailabilityService service = createService();
        Map<String, Object> summary = service.getSummary(1);

        assertEquals("UP", summary.get("currentStatus"));
        assertNotNull(summary.get("uptimePercentage"));
        double uptimePercentage = ((Number) summary.get("uptimePercentage")).doubleValue();
        assertTrue(uptimePercentage >= 49.9 && uptimePercentage <= 50.1);
        assertTrue(((Number) summary.get("downtimeSeconds")).longValue() >= 3_599);
    }

    @Test
    void startupConvertsAStaleUpHeartbeatIntoADowntimePeriod() {
        Instant lastHeartbeat = Instant.now().minusSeconds(600);
        Instant inferredDownAt = lastHeartbeat.plusSeconds(120);
        Period staleUp = new Period(
                10L,
                "UP",
                lastHeartbeat.minusSeconds(3_600),
                null,
                lastHeartbeat,
                null
        );
        Period inferredDown = new Period(
                11L,
                "DOWN",
                inferredDownAt,
                null,
                Instant.now(),
                "服务心跳中断"
        );
        when(repository.findOpenPeriod())
                .thenReturn(Optional.of(staleUp))
                .thenReturn(Optional.of(inferredDown));
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        SystemAvailabilityService service = createService();
        service.initialize();

        verify(repository).close(10L, inferredDownAt);
        verify(repository).insertOpen(eq("DOWN"), eq(inferredDownAt), any(), eq("服务心跳中断"));
        verify(repository).close(eq(11L), any());
        verify(repository).insertOpen(eq("UP"), any(), any(), isNull());
        verify(repository).deleteEndedBefore(any());
    }

    @Test
    void minuteAvailabilityMarksAnyMinuteWithDowntimeAsDown() {
        Instant currentMinute = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        Period up = new Period(
                1L,
                "UP",
                currentMinute.minus(2, ChronoUnit.MINUTES),
                null,
                currentMinute,
                null
        );
        Period down = new Period(
                2L,
                "DOWN",
                currentMinute.minus(3, ChronoUnit.MINUTES),
                currentMinute.minus(2, ChronoUnit.MINUTES),
                currentMinute.minus(2, ChronoUnit.MINUTES),
                "服务心跳中断"
        );
        when(repository.findOverlapping(any(), any())).thenReturn(List.of(up, down));

        SystemAvailabilityService service = createService();
        List<Map<String, Object>> minutes = service.getMinuteAvailability();

        assertEquals(1_440, minutes.size());
        Map<String, Object> downtimeMinute = minutes.stream()
                .filter(item -> currentMinute.minus(3, ChronoUnit.MINUTES).equals(item.get("startedAt")))
                .findFirst()
                .orElseThrow();
        assertEquals("DOWN", downtimeMinute.get("status"));
    }

    @Test
    void minuteAvailabilityForDateCoversTheSelectedCalendarDay() {
        LocalDate date = LocalDate.of(2026, 7, 16);
        Instant expectedStart = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        when(repository.findOverlapping(eq(expectedStart), eq(expectedStart.plus(1, ChronoUnit.DAYS))))
                .thenReturn(List.of());

        SystemAvailabilityService service = createService();
        List<Map<String, Object>> minutes = service.getMinuteAvailability(date);

        assertEquals(1_440, minutes.size());
        assertEquals(expectedStart, minutes.getFirst().get("startedAt"));
        assertEquals(expectedStart.plus(1_439, ChronoUnit.MINUTES), minutes.getLast().get("startedAt"));
        verify(repository).findOverlapping(expectedStart, expectedStart.plus(1, ChronoUnit.DAYS));
    }

    private SystemAvailabilityService createService() {
        return new SystemAvailabilityService(
                repository,
                jdbcTemplate,
                true,
                120_000,
                3_000,
                "",
                365,
                "UTC"
        );
    }
}
