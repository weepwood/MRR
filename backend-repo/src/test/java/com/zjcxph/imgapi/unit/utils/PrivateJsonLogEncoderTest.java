package com.zjcxph.imgapi.unit.utils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.zjcxph.imgapi.config.PrivateJsonLogEncoder;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;

class PrivateJsonLogEncoderTest {
    @Test void failedFallbackWriteRetriesTheSameIdentityAndShutdownDoesNotAccessDatabase() {
        var mapper=org.mockito.Mockito.mock(com.zjcxph.imgapi.mapper.SystemErrorEventMapper.class);
        org.mockito.Mockito.when(mapper.upsert(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("synthetic database outage")).thenReturn(1);
        var service=new com.zjcxph.imgapi.service.SystemErrorEventService(mapper);
        var context=new LoggerContext();
        var event=new LoggingEvent(getClass().getName(),context.getLogger("synthetic.retry"),Level.ERROR,"Query failed for {}",null,null);
        event.setMDCPropertyMap(java.util.Map.of());
        service.capture(event);service.flushPending();service.flushPending();
        var captor=org.mockito.ArgumentCaptor.forClass(com.zjcxph.imgapi.entity.SystemErrorEvent.class);
        org.mockito.Mockito.verify(mapper,org.mockito.Mockito.times(2)).upsert(captor.capture());
        assertThat(captor.getAllValues().get(0).getProjectionEventId()).isEqualTo(captor.getAllValues().get(1).getProjectionEventId());
        org.mockito.Mockito.when(mapper.upsert(org.mockito.ArgumentMatchers.any())).thenThrow(new IllegalStateException("synthetic outage"));
        service.capture(event);
        service.destroy();
        org.mockito.Mockito.verify(mapper,org.mockito.Mockito.times(2)).upsert(org.mockito.ArgumentMatchers.any());
        context.stop();
    }

    @Test void ordinaryLogsHaveEventIdentityWithoutInventingAnErrorGroup() {
        var context=new LoggerContext();
        var event=new LoggingEvent(getClass().getName(),context.getLogger("synthetic.info"),Level.INFO,"Query failed for {}",null,null);
        event.setMDCPropertyMap(java.util.Map.of());
        String encoded=new String(new PrivateJsonLogEncoder().encode(event),StandardCharsets.UTF_8);
        assertThat(encoded).contains("event_id").doesNotContain("errorId", "ERR-");
        context.stop();
    }

    @Test void legacyStackOnlyRetainsTechnicalClassNamesAndFrames() {
        assertThat(PrivateJsonLogEncoder.safeStoredStack("SYNTHETIC-PATIENT\njava.lang.IllegalStateException: SYNTHETIC-PATIENT\n\tat com.example.Synthetic.run(Synthetic.java:12)"))
                .contains("Synthetic.java:12").doesNotContain("SYNTHETIC-PATIENT");
    }
    @Test void concatenatedAndThirdPartyMessagesAreHiddenByDefault() {
        assertThat(PrivateJsonLogEncoder.safeMessage("删除临时文件: 12345678.temp")).isEqualTo("[MESSAGE OMITTED]");
        assertThat(PrivateJsonLogEncoder.safeMessage("SYNTHETIC-PATIENT")).isEqualTo("[MESSAGE OMITTED]");
    }
    @Test void parametersAndExceptionMessagesCannotLeakIntoTheJournal() {
        LoggerContext context = new LoggerContext();
        LoggingEvent event = new LoggingEvent(getClass().getName(), context.getLogger("synthetic"), Level.ERROR,
                "Query failed for {}", new IllegalStateException("SYNTHETIC-PRIVATE-DATA"), new Object[]{"SYNTHETIC-PRIVATE-DATA"});
        event.setMDCPropertyMap(java.util.Map.of("requestId", "synthetic-request"));
        String encoded = new String(new PrivateJsonLogEncoder().encode(event), StandardCharsets.UTF_8);
        assertThat(encoded).contains("Query failed for {}", "synthetic-request", "IllegalStateException")
                .doesNotContain("SYNTHETIC-PRIVATE-DATA");
        context.stop();
    }
}
