package com.zjcxph.imgapi.ocr;

import com.zjcxph.imgapi.config.OcrRuntimeProperties;
import com.zjcxph.imgapi.service.OcrJobService;
import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.concurrent.*;

@Component
public class OcrScheduler {
    private final OcrJobService service;
    private final ExecutorService executor;
    private final Semaphore slots;
    public OcrScheduler(OcrJobService service,OcrRuntimeProperties properties) {
        this.service=service;
        int concurrency=Math.max(1,Math.min(4,properties.getMaxWorkers()));
        slots=new Semaphore(concurrency);
        executor=Executors.newFixedThreadPool(concurrency,r->{Thread t=new Thread(r,"ocr-page");t.setDaemon(true);return t;});
    }
    @Scheduled(fixedDelayString="${ocr.runtime.poll-millis:1000}")
    public void dispatch() {
        if (!service.enabled() || !slots.tryAcquire()) return;
        try { executor.execute(()->{try { service.runOne(); } finally { slots.release(); }}); }
        catch(RejectedExecutionException e) { slots.release(); }
    }
    @PreDestroy public void close() { executor.shutdownNow(); }
}
