package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.repository.StorageCircuitHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class StorageCircuitHistoryService {
    private static final Logger log=LoggerFactory.getLogger(StorageCircuitHistoryService.class);
    private static final Set<String> KINDS=Set.of("NAS","HTTP","NGINX","OSS");
    private static final Set<String> TYPES=Set.of("OPENED","PROBE_STARTED","METADATA_ONLY","RECOVERED");
    private final OperationsCenterService center;
    private final StorageCircuitHistoryRepository repository;
    private final boolean enabled;
    private Instant lastWarning=Instant.EPOCH;

    public StorageCircuitHistoryService(OperationsCenterService center,StorageCircuitHistoryRepository repository,
            @Value("${app.operations.history.enabled:false}") boolean enabled) {
        this.center=center;this.repository=repository;this.enabled=enabled;
    }

    public record History(boolean enabled,int hours,boolean truncated,List<StorageCircuitHistoryRepository.Event> events) {}

    public History history(int hours) {
        if(hours!=24 && hours!=168) throw new BusinessException(400,"仅支持最近24小时或7天");
        if(!enabled) return new History(false,hours,false,List.of());
        Instant now=Instant.now();
        var events=repository.read(now.minus(hours,ChronoUnit.HOURS),now);
        return new History(true,hours,events.size()>StorageCircuitHistoryRepository.MAX_RESULTS,
                events.stream().limit(StorageCircuitHistoryRepository.MAX_RESULTS).toList());
    }

    /** Collect only the newest bounded process-local buffer events, never intercept storage I/O. */
    @Scheduled(fixedDelay=60000,initialDelay=60000)
    public void collect() {
        if(!enabled) return;
        Instant now=Instant.now(), cutoff=now.minus(7,ChronoUnit.DAYS);
        try {
            var events=events(center.storageCircuitSnapshots(),now,cutoff);
            repository.record(events);
            repository.prune(cutoff);
        } catch(RuntimeException ignored) {
            if(lastWarning.plusSeconds(3600).isBefore(now)) {
                lastWarning=now;
                log.warn("存储熔断历史采集暂不可用");
            }
        }
    }

    static List<StorageCircuitHistoryRepository.Event> events(Map<String,Map<String,Object>> snapshots,Instant now,Instant cutoff) {
        var newest=new PriorityQueue<StorageCircuitHistoryRepository.Event>(Comparator.comparing(StorageCircuitHistoryRepository.Event::occurredAt)
                .thenComparing(StorageCircuitHistoryRepository.Event::circuitId).thenComparingLong(StorageCircuitHistoryRepository.Event::sequence));
        for(var entry:snapshots.entrySet()) {
            if(!KINDS.contains(entry.getKey()) || !(entry.getValue().get("nodes") instanceof List<?> nodes)) continue;
            for(var item:nodes) {
                if(!(item instanceof Map<?,?> node) || !(node.get("ordinal") instanceof Integer ordinal) || ordinal<=0
                        || !(node.get("history") instanceof Map<?,?> history) || !(history.get("events") instanceof List<?> buffer)) continue;
                UUID circuit;
                try { circuit=UUID.fromString(String.valueOf(history.get("circuitId"))); }
                catch(IllegalArgumentException invalid) { continue; }
                for(var raw:buffer) {
                    if(!(raw instanceof Map<?,?> event) || !(event.get("sequence") instanceof Number seq) || seq.longValue()<=0
                            || !(event.get("type") instanceof String type) || !TYPES.contains(type)) continue;
                    Instant at;
                    try { at=Instant.parse(String.valueOf(event.get("at"))); }
                    catch(java.time.format.DateTimeParseException invalid) { continue; }
                    if(at.isBefore(cutoff)||at.isAfter(now)) continue;
                    newest.add(new StorageCircuitHistoryRepository.Event(circuit,seq.longValue(),entry.getKey(),ordinal,type,at,now));
                    if(newest.size()>StorageCircuitHistoryRepository.MAX_BATCH) newest.poll();
                }
            }
        }
        return newest.stream().sorted(newest.comparator()).toList();
    }
}
