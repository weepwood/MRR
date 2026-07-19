package com.zjcxph.imgapi.unit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.entity.Log;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.mapper.LogMapper;
import com.zjcxph.imgapi.service.ReliableAuditService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

        Log audit = audit("event-12345678");
        service.persist(audit);

        assertThat(service.getQueuedEvents()).isEqualTo(1);
        assertThat(service.isHealthy()).isTrue();
        assertThat(service.isDegraded()).isTrue();
        assertThat(Files.readString(spool))
                .contains("event-12345678")
                .contains("\"persistedVia\":\"SPOOL\"");

        reset(logMapper);
        when(logMapper.insert(org.mockito.ArgumentMatchers.any(Log.class))).thenReturn(1);

        service.replaySpool();

        assertThat(service.getQueuedEvents()).isZero();
        assertThat(service.isHealthy()).isTrue();
        assertThat(service.isDegraded()).isFalse();
        assertThat(Files.readString(spool)).isEmpty();
        verify(logMapper).insert(org.mockito.ArgumentMatchers.argThat(
                value -> "event-12345678".equals(value.getEventId())
                        && "SPOOL".equals(value.getPersistedVia())));
    }

    @Test
    void rejectsSensitiveWorkWhenFallbackDirectoryIsNotWritable() throws Exception {
        Path blockedParent = tempDir.resolve("blocked-parent");
        Files.writeString(blockedParent, "this is a file, not a directory");

        ReliableAuditService service = new ReliableAuditService(
                mock(LogMapper.class),
                new ObjectMapper(),
                new SimpleMeterRegistry(),
                blockedParent.resolve("audit-events.jsonl").toString());

        assertThatThrownBy(service::assertFallbackAvailable)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("审计服务暂不可用");
        assertThat(service.isFallbackAvailable()).isFalse();
        assertThat(service.isHealthy()).isFalse();
    }

    @Test
    void throwsAndMarksLostEventWhenDatabaseAndSpoolBothFail() throws Exception {
        LogMapper logMapper = mock(LogMapper.class);
        doThrow(new IllegalStateException("database unavailable"))
                .when(logMapper).insert(org.mockito.ArgumentMatchers.any(Log.class));

        Path blockedParent = tempDir.resolve("blocked-spool");
        Files.writeString(blockedParent, "this is a file, not a directory");
        ReliableAuditService service = new ReliableAuditService(
                logMapper,
                new ObjectMapper(),
                new SimpleMeterRegistry(),
                blockedParent.resolve("audit-events.jsonl").toString());

        assertThatThrownBy(() -> service.persist(audit("event-lost-1234")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("审计服务暂不可用");
        assertThat(service.isLostEventDetected()).isTrue();
        assertThat(service.isHealthy()).isFalse();
    }

    @Test
    void quarantinesMalformedSpoolLinesAndReportsDown() throws Exception {
        Path spool = tempDir.resolve("audit-corrupt/audit-events.jsonl");
        Files.createDirectories(spool.getParent());
        Files.writeString(spool, "{not-valid-json}" + System.lineSeparator());

        ReliableAuditService service = new ReliableAuditService(
                mock(LogMapper.class),
                new ObjectMapper(),
                new SimpleMeterRegistry(),
                spool.toString());

        service.replaySpool();

        assertThat(service.getQueuedEvents()).isZero();
        assertThat(service.getDeadLetterEvents()).isEqualTo(1);
        assertThat(service.isHealthy()).isFalse();
        assertThat(Files.readString(spool.resolveSibling("audit-events.jsonl.deadletter")))
                .contains("{not-valid-json}");
    }

    private Log audit(String eventId) {
        Log audit = new Log();
        audit.setEventId(eventId);
        audit.setRequestId("request-12345678");
        audit.setTraceId("trace-12345678");
        audit.setAuditAction("VIEW_IMAGE");
        audit.setAuditResult("SUCCESS");
        audit.setAccessTime(new Date());
        return audit;
    }
}
