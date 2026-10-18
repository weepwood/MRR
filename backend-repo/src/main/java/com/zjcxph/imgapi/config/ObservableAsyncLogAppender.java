package com.zjcxph.imgapi.config;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;

/** 业务优先；队列饱和有明确的缺口信号，计数为观察到的下界。 */
public class ObservableAsyncLogAppender extends AsyncAppender {
    public static final java.util.concurrent.atomic.AtomicLong SATURATION = new java.util.concurrent.atomic.AtomicLong();
    @Override protected void append(ILoggingEvent event) {
        if (com.zjcxph.imgapi.utils.RuntimeLogContext.ingesting()) {
            com.zjcxph.imgapi.utils.RuntimeLogContext.SUPPRESSED.incrementAndGet();
            return;
        }
        if (getRemainingCapacity() == 0) SATURATION.incrementAndGet();
        super.append(event);
    }
}
