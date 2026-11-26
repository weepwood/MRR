package com.zjcxph.imgapi.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.entity.Log;
import com.zjcxph.imgapi.mapper.LogMapper;
import com.zjcxph.imgapi.service.AsyncLogService;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/** Explicit experimental alternative; never silently falls back to the volatile service. */
@Service
@ConditionalOnProperty(name="mrr.audit.spool.enabled",havingValue="true")
public class DurableAuditLogService implements AsyncLogService {
    private static final Logger logger = LoggerFactory.getLogger(DurableAuditLogService.class);
    private final EncryptedAuditSpool spool;
    private final DurableAuditDelivery delivery;
    private final GroupedAuditAdmission grouped;
    private final ReentrantLock flushLock = new ReentrantLock();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();
    private volatile boolean closing;

    public DurableAuditLogService(Environment env, ObjectMapper mapper, LogMapper logs, JdbcTemplate jdbc,
                                  PlatformTransactionManager manager, MeterRegistry metrics) throws IOException {
        byte[] key;
        try { key = Base64.getDecoder().decode(env.getRequiredProperty("MRR_AUDIT_SPOOL_KEY")); }
        catch (RuntimeException failure) { throw new IOException("Independent audit spool key required"); }
        try {
            if (key.length != 32 || java.util.Arrays.equals(key, new byte[32])) throw new IOException("Invalid audit spool key");
            spool = new EncryptedAuditSpool(Path.of(env.getRequiredProperty("mrr.audit.spool.directory")),key,
                    env.getProperty("mrr.audit.spool.max-records",Integer.class,10000),
                    env.getProperty("mrr.audit.spool.max-bytes",Long.class,64L*1024*1024),mapper);
        } finally { Arrays.fill(key,(byte)0); }
        boolean groupEnabled=env.getProperty("mrr.audit.spool.grouped",Boolean.class,false);
        grouped=groupEnabled?new GroupedAuditAdmission(spool):null;
        delivery = new DurableAuditDelivery(logs,jdbc,manager,groupEnabled);
        metrics.gauge("mrr.audit.pending",spool,EncryptedAuditSpool::pendingCount);
        metrics.gauge("mrr.audit.spool.bytes",spool,EncryptedAuditSpool::pendingBytes);
        metrics.gauge("mrr.audit.spool.published.segments",spool,EncryptedAuditSpool::publishedSegments);
        metrics.gauge("mrr.audit.spool.published.bytes",spool,EncryptedAuditSpool::publishedBytes);
        metrics.gauge("mrr.audit.spool.file.forces",spool,EncryptedAuditSpool::fileForces);
        metrics.gauge("mrr.audit.spool.directory.forces",spool,EncryptedAuditSpool::directoryForces);
        metrics.gauge("mrr.audit.dropped",rejected);
        metrics.gauge("mrr.audit.spool.delivery.failures",failures);
    }

    // Intentionally synchronous: no volatile executor queue before durable admission.
    // Servlet afterCompletion is already too late to retract a response.
    @Override public void saveLogAsync(Log log) {
        try { if (closing) throw new IOException("Audit service closing"); if(grouped==null)spool.append(log); else grouped.append(log); }
        catch (IOException | RuntimeException failure) {
            long total = rejected.incrementAndGet();
            if (total == 1 || total % 100 == 0) {
                logger.error("Audit durable admission failed: type={}, rejectedTotal={}",failure.getClass().getSimpleName(),total);
            }
        }
    }

    @Override public void batchSaveLogsAsync(List<Log> logs) {
        if (logs == null) return;
        // Per-record admission only; an explicit batch is not an atomic whole.
        for (Log log : logs) saveLogAsync(log);
    }

    @Scheduled(fixedDelayString="${mrr.audit.spool.flush-delay-ms:250}")
    public void flush() {
        if (closing || !flushLock.tryLock()) return;
        try {
            var entries = spool.peek(50);
            if (entries.isEmpty()) return;
            delivery.deliver(entries.stream().map(EncryptedAuditSpool.Entry::log).toList());
            if(grouped==null && entries.stream().map(EncryptedAuditSpool.Entry::name).distinct().count()==entries.size()) {
                for(var entry:entries)spool.acknowledge(entry.name());
            } else spool.acknowledgeSegments(entries.stream().map(EncryptedAuditSpool.Entry::name).toList());
        } catch (IOException | RuntimeException failure) {
            long total = failures.incrementAndGet();
            if (total == 1 || total % 100 == 0)
                logger.error("Audit durable delivery deferred: type={}, failures={}",failure.getClass().getSimpleName(),total);
        } finally {
            try {
                if (closing) spool.close();
            } catch (IOException failure) {
                logger.error("Audit spool close failed: type={}",failure.getClass().getSimpleName());
            } finally { flushLock.unlock(); }
        }
    }

    @PreDestroy public void close() throws IOException {
        // No unbounded shutdown drain; published ciphertext remains for restart.
        if(grouped!=null)grouped.close();
        closing = true;
        if (flushLock.tryLock()) {
            try { spool.close(); } finally { flushLock.unlock(); }
        } // In-flight writer owns closure; never release the directory lock ahead of it.
    }
}
