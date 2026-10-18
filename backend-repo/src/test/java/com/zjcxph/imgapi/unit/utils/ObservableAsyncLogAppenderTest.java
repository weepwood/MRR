package com.zjcxph.imgapi.unit.utils;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.zjcxph.imgapi.config.ObservableAsyncLogAppender;
import com.zjcxph.imgapi.utils.RuntimeLogContext;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ObservableAsyncLogAppenderTest {
    @Test void collectorEventsAreSuppressedBeforeCrossingAsyncThreadBoundary() {
        LoggerContext context = new LoggerContext();
        ListAppender<ILoggingEvent> sink = new ListAppender<>();
        sink.setContext(context); sink.start();
        ObservableAsyncLogAppender async = new ObservableAsyncLogAppender();
        async.setContext(context); async.addAppender(sink); async.start();
        var event = new LoggingEvent(getClass().getName(), context.getLogger("synthetic"), Level.INFO, "synthetic", null, null);
        event.setMDCPropertyMap(java.util.Map.of());
        RuntimeLogContext.enter();
        try { async.doAppend(event); } finally { RuntimeLogContext.leave(); }
        async.stop(); context.stop();
        assertThat(sink.list).isEmpty();
    }
}
