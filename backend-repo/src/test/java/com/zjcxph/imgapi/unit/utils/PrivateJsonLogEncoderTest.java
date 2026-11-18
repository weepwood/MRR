package com.zjcxph.imgapi.unit.utils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.zjcxph.imgapi.config.PrivateJsonLogEncoder;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;

class PrivateJsonLogEncoderTest {
    private static final String BUSINESS_LOGGER = "com.zjcxph.imgapi.service.RecordService";
    private static final String UNKNOWN_LOGGER = "synthetic.thirdparty";

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

    /**
     * 缺陷回归：旧实现只放行 6 条硬编码模板，其余一律替换为 {@code [MESSAGE OMITTED]}，
     * 实测日志文件 372/372 条全部被抹除（含 Spring Boot 启动日志与全部业务模板），
     * 运维无法判断任何故障。ADR-0002 要求的是「message 使用模板、参数不序列化」。
     */
    @Test void ordinaryOperationalMessagesRemainReadableForTroubleshooting() {
        assertThat(PrivateJsonLogEncoder.safeMessage(
                "org.springframework.boot.web.embedded.tomcat.TomcatWebServer",
                "Tomcat initialized with port 18045 (http)", false))
                .isEqualTo("Tomcat initialized with port 18045 (http)");
        assertThat(PrivateJsonLogEncoder.safeMessage(BUSINESS_LOGGER, "Query failed for {}", true))
                .isEqualTo("Query failed for {}");
        assertThat(PrivateJsonLogEncoder.safeMessage(BUSINESS_LOGGER, "影像落库完成，耗时 1234 ms", true))
                .isEqualTo("影像落库完成，耗时 1234 ms");
    }

    /** 既有安全契约：不带参数的未知自由文本（拼接或第三方来源）仍整体隐藏。 */
    @Test void unknownFreeTextWithoutArgumentsStaysHidden() {
        assertThat(PrivateJsonLogEncoder.safeMessage("com.zjcxph.imgapi.synthetic", "SYNTHETIC-PRIVATE", false))
                .isEqualTo("[MESSAGE OMITTED]");
        assertThat(PrivateJsonLogEncoder.safeMessage(UNKNOWN_LOGGER, "SYNTHETIC-PRIVATE", false))
                .isEqualTo("[MESSAGE OMITTED]");
    }

    /** 可读性不得以泄漏为代价：敏感值就地脱敏，其余正文保留。 */
    @Test void sensitiveValuesAreRedactedInPlaceInsteadOfHidingTheWholeMessage() {
        assertThat(PrivateJsonLogEncoder.safeMessage(BUSINESS_LOGGER, "查询病案失败 bah=12345678", true))
                .contains("查询病案失败").doesNotContain("12345678");
        assertThat(PrivateJsonLogEncoder.safeMessage(BUSINESS_LOGGER, "校验身份证 110101199003071234 失败", true))
                .contains("校验身份证").doesNotContain("110101199003071234");
        // 15 位旧证：既有 LONG_ID_NUMBER 正则要求 16 位以上才会命中，属于漏匹配。
        assertThat(PrivateJsonLogEncoder.safeMessage(BUSINESS_LOGGER, "校验旧证 110101900307123 失败", true))
                .doesNotContain("110101900307123");
        assertThat(PrivateJsonLogEncoder.safeMessage(BUSINESS_LOGGER, "通知手机 13800138000 失败", true))
                .doesNotContain("13800138000");
        assertThat(PrivateJsonLogEncoder.safeMessage(BUSINESS_LOGGER, "Authorization: Bearer abcdefghijklmnop", true))
                .doesNotContain("abcdefghijklmnop");
        assertThat(PrivateJsonLogEncoder.safeMessage(BUSINESS_LOGGER, "删除临时文件 D:\\temp\\12345678.temp", true))
                .doesNotContain("12345678").doesNotContain("temp");
        assertThat(PrivateJsonLogEncoder.safeMessage(BUSINESS_LOGGER, "连接 jdbc:postgresql://10.0.0.5:5432/mrr 失败", true))
                .doesNotContain("10.0.0.5");
        // 方括号包裹的真实姓名仍须抹除；已被抹除的占位符则必须原样保留（幂等）。
        assertThat(PrivateJsonLogEncoder.safeMessage(BUSINESS_LOGGER, "name=[张三] 查询失败", true))
                .doesNotContain("张三").contains("name=[REDACTED]");
        // 单斜杠的协议写法不是 Windows 路径，不应被误截。
        assertThat(PrivateJsonLogEncoder.safeMessage(BUSINESS_LOGGER, "psql:/tmp/dump.sql 执行失败", true))
                .contains("psql:/tmp/dump.sql");
    }

    /** 采集器会对同一行二次处理，脱敏必须幂等；超长正文需按列宽截断。 */
    @Test void sanitizingIsIdempotentAndBoundedForJournalReplay() {
        String once = PrivateJsonLogEncoder.safeStoredMessage("查询病案失败 bah=12345678");
        assertThat(PrivateJsonLogEncoder.safeStoredMessage(once)).isEqualTo(once);
        assertThat(PrivateJsonLogEncoder.safeStoredMessage("[MESSAGE OMITTED]")).isEqualTo("[MESSAGE OMITTED]");
        assertThat(PrivateJsonLogEncoder.safeStoredMessage("x".repeat(5000))).hasSizeLessThan(2100);
    }
}
