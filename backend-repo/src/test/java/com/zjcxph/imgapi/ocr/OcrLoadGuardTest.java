package com.zjcxph.imgapi.ocr;

import org.junit.jupiter.api.Test;
import java.time.*;
import static org.assertj.core.api.Assertions.*;

class OcrLoadGuardTest {
    @Test void historyRunsOnlyAtNightAndFailsClosedWithoutMeasurements() {
        assertThat(OcrLoadGuard.night(ZonedDateTime.of(2026,10,18,22,0,0,0,ZoneId.of("Asia/Shanghai")))).isTrue();
        assertThat(OcrLoadGuard.night(ZonedDateTime.of(2026,10,18,6,0,0,0,ZoneId.of("Asia/Shanghai")))).isFalse();
        assertThat(OcrLoadGuard.reason(.2,.5,0,100,false)).isEqualTo("P95_UNAVAILABLE");
        assertThat(OcrLoadGuard.reason(.2,.19,100,100,false)).isEqualTo("MEMORY_PRESSURE");
        assertThat(OcrLoadGuard.reason(.2,.5,111,100,false)).isEqualTo("IMAGE_LATENCY");
        assertThat(OcrLoadGuard.reason(.8,.5,100,100,true)).isEqualTo("CPU_PRESSURE");
        assertThat(OcrLoadGuard.reason(.2,.5,100,100,false)).isEqualTo("READY");
    }
}
