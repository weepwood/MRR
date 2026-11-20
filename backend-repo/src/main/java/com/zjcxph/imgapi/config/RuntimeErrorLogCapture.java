package com.zjcxph.imgapi.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.zjcxph.imgapi.service.SystemErrorEventService;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * 把 WARN/ERROR 日志实时捕获进运行错误中心的常驻挂载器。
 *
 * <p>原实现与运行日志 journal 功能互斥（开关二选一）；journal 链路已按 ADR-0008 取消，
 * 本类成为错误中心唯一的数据来源，无条件生效。</p>
 */
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
        context.getLoggerList().stream()
                .filter(logger -> logger == rootLogger || !logger.isAdditive())
                .forEach(logger -> logger.addAppender(captureAppender));

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
            context.getLoggerList().forEach(logger -> logger.detachAppender(appender));
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
