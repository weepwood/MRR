package com.zjcxph.imgapi.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.zjcxph.imgapi.service.SystemErrorEventService;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class RuntimeErrorLogCapture implements SmartLifecycle {

    private static final String APPENDER_NAME = "MRR_RUNTIME_ERROR_CAPTURE";

    private final SystemErrorEventService systemErrorEventService;
    private volatile boolean running;
    private AppenderBase<ILoggingEvent> appender;

    public RuntimeErrorLogCapture(SystemErrorEventService systemErrorEventService) {
        this.systemErrorEventService = systemErrorEventService;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);

        CaptureAppender captureAppender = new CaptureAppender(systemErrorEventService);
        captureAppender.setContext(context);
        captureAppender.setName(APPENDER_NAME);
        captureAppender.start();
        rootLogger.addAppender(captureAppender);

        appender = captureAppender;
        running = true;
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        if (appender != null) {
            rootLogger.detachAppender(appender);
            appender.stop();
            appender = null;
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }

    private static final class CaptureAppender extends AppenderBase<ILoggingEvent> {
        private final SystemErrorEventService service;

        private CaptureAppender(SystemErrorEventService service) {
            this.service = service;
        }

        @Override
        protected void append(ILoggingEvent eventObject) {
            service.capture(eventObject);
        }
    }
}
