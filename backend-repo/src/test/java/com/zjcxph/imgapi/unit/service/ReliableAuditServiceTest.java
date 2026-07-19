package com.zjcxph.imgapi.unit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.entity.Log;
import com.zjcxph.imgapi.mapper.LogMapper;
import com.zjcxph.imgapi.service.ReliableAuditService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReliableAuditServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void fallsBackToDurableSpoolAndReplaysAfterDatabaseRecovery() throws Exception {
        LogMapper logMapper = mock(LogMapper.class);
        doThrow(new IllegalStateException("database unavailable"))
                .when(logMapper).insert(org.mockito.ArgumentMatchers.any(Log.class));

        Path spool = tempDir.resolve("audit/audit-events.jsonl");
        ReliableAuditService service = new ReliableAuditService(
                logMapper,
                new ObjectMapper(),
                new SimpleMeterRegistry(),
                spool.toString());

        Log audit = new Log();
        audit.setEventId("event-12345678");
        audit.setRequestId("request-12345678");
        audit.setTraceId("trace-12345678");
        audit.setAuditAction("VIEW_IMAGE");
        audit.setAuditResult("SUCCESS");
        audit.setAccessTime(new Date());

        service.persist(audit);

        assertThat(service.getQueuedEvents()).isEqualTo(1);
        assertThat(Files.readString(spool))
                .contains("event-12345678")
                .contains("\"persistedVia\":\"SPOOL\"");

        reset(logMapper);
        when(logMapper.insert(org.mockito.ArgumentMatchers.any(Log.class))).thenReturn(1);

        service.replaySpool();

        assertThat(service.getQueuedEvents()).isZero();
        assertThat(Files.readString(spool)).isEmpty();
        verify(logMapper).insert(org.mockito.ArgumentMatchers.argThat(
                value -> "event-12345678".equals(value.getEventId())
                        && "SPOOL".equals(value.getPersistedVia())));
    }
}
