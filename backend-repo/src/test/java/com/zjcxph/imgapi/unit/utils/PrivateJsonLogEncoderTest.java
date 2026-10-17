package com.zjcxph.imgapi.unit.utils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.zjcxph.imgapi.config.PrivateJsonLogEncoder;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;

class PrivateJsonLogEncoderTest {
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
