package com.zjcxph.imgapi.ocr;

import com.zjcxph.imgapi.config.OcrRuntimeProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.lang.management.ManagementFactory;
import java.time.*;

/** Uses the existing HTTP timers. Missing latency evidence blocks history, never interactive OCR. */
@Component
public class OcrLoadGuard {
    private final OcrRuntimeProperties properties;private final MeterRegistry registry;
    private volatile String current="P95_UNAVAILABLE";private long highCpuSince;
    public OcrLoadGuard(OcrRuntimeProperties properties,MeterRegistry registry) {this.properties=properties;this.registry=registry;}
    public static boolean night(ZonedDateTime time) {int hour=time.withZoneSameInstant(ZoneId.of("Asia/Shanghai")).getHour();return hour>=22||hour<6;}
    public static String reason(double cpu,double free,double p95,double baseline,boolean sustainedCpu) {
        if(!Double.isFinite(cpu)||cpu<0||!Double.isFinite(free)||free<=0) return "MEASUREMENT_UNAVAILABLE";
        if(free<.20) return "MEMORY_PRESSURE";
        if(sustainedCpu && cpu>.70) return "CPU_PRESSURE";
        if(p95<=0||!Double.isFinite(p95)||baseline<=0) return "P95_UNAVAILABLE";
        if(p95>baseline*1.10) return "IMAGE_LATENCY";
        return "READY";
    }
    @Scheduled(fixedDelay=10000) public void sample() {
        if(!night(ZonedDateTime.now(ZoneId.of("Asia/Shanghai")))) {current="OUTSIDE_WINDOW";return;}
        var os=ManagementFactory.getOperatingSystemMXBean();
        if(!(os instanceof com.sun.management.OperatingSystemMXBean system)) {current="MEASUREMENT_UNAVAILABLE";return;}
        double cpu=system.getCpuLoad();long now=System.nanoTime();
        if(cpu>.70) {if(highCpuSince==0) highCpuSince=now;} else highCpuSince=0;
        double p95=0;
        for(var timer:registry.find("http.server.requests").timers()) {
            String uri=timer.getId().getTag("uri");
            if(uri==null||(!uri.startsWith("/api/v1/img/")&&!uri.startsWith("/api/v1/scan/"))) continue;
            for(var value:timer.takeSnapshot().percentileValues())
                if(Math.abs(value.percentile()-.95)<.001) p95=Math.max(p95,value.value(java.util.concurrent.TimeUnit.MILLISECONDS));
        }
        current=reason(cpu,(double)system.getFreeMemorySize()/Math.max(1,system.getTotalMemorySize()),p95,properties.getImageP95BaselineMillis(),
                highCpuSince!=0&&now-highCpuSince>=java.util.concurrent.TimeUnit.MINUTES.toNanos(5));
    }
    public String reason() {return night(ZonedDateTime.now(ZoneId.of("Asia/Shanghai")))?current:"OUTSIDE_WINDOW";}
    public boolean allowed() {return "READY".equals(reason());}
}
