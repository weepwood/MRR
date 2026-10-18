package com.zjcxph.imgapi.ocr;

import com.zjcxph.imgapi.config.OcrRuntimeProperties;
import com.zjcxph.imgapi.service.OcrJobService;
import com.zjcxph.imgapi.service.SystemSettingService;
import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.concurrent.*;

@Component
public class OcrScheduler {
    private final OcrJobService service;
    private final ExecutorService executor;
    private final Semaphore slots;
    private final int capacity;
    private final SystemSettingService settings;
    private final OcrEngine engine;
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(OcrScheduler.class);
    public OcrScheduler(OcrJobService service,OcrRuntimeProperties properties,SystemSettingService settings,OcrEngine engine) {
        this.service=service;
        int concurrency=Math.max(1,Math.min(4,properties.getMaxWorkers()));
        this.capacity=concurrency;this.settings=settings;this.engine=engine;
        slots=new Semaphore(concurrency);
        executor=Executors.newFixedThreadPool(concurrency,r->{Thread t=new Thread(r,"ocr-page");t.setDaemon(true);return t;});
    }
    @Scheduled(fixedDelayString="${ocr.runtime.poll-millis:1000}")
    public void dispatch() {
        if (!service.enabled()) { engine.reset(); return; }
        int requested=1;
        try{requested=Math.max(1,Math.min(capacity,Integer.parseInt(settings.getSetting("ocrMaxConcurrency"))));}catch(Exception invalid){ /* safe single worker */ }
        if (capacity-slots.availablePermits()>=requested || !slots.tryAcquire()) return;
        try { executor.execute(()->{try { service.runOne(); } catch(RuntimeException failure) {
            LOG.warn("OCR 持久化暂不可用，等待租约恢复");
        } finally { slots.release(); }}); }
        catch(RejectedExecutionException e) { slots.release(); }
    }
    @PreDestroy public void close() { executor.shutdownNow(); }
}
