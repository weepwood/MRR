package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.entity.Log;
import com.zjcxph.imgapi.mapper.LogMapper;
import com.zjcxph.imgapi.service.AsyncLogService;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="mrr.audit.spool.enabled",havingValue="false",matchIfMissing=true)
public class AsyncLogServiceImpl implements AsyncLogService {
    private static final Logger logger = LoggerFactory.getLogger(AsyncLogServiceImpl.class);
    private static final int BATCH_SIZE = 50;
    // In-memory outage buffer only. It does not claim crash durability.
    static final int MAX_PENDING = 1000;
    private final LogMapper logMapper;
    private final ArrayDeque<Log> buffer = new ArrayDeque<>();
    private final Object bufferLock = new Object();
    private final ReentrantLock flushLock = new ReentrantLock();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong retried = new AtomicLong();
    private int inFlight;
    private boolean retryDeferred;

    public AsyncLogServiceImpl(LogMapper logMapper) {
        this(logMapper, null);
    }

    @Autowired
    public AsyncLogServiceImpl(LogMapper logMapper, MeterRegistry metrics) {
        this.logMapper = logMapper;
        if (metrics != null) {
            metrics.gauge("mrr.audit.pending", this, AsyncLogServiceImpl::pendingCount);
            metrics.gauge("mrr.audit.dropped", dropped);
            metrics.gauge("mrr.audit.requeued", retried);
        }
    }

    public int pendingCount() {
        synchronized (bufferLock) { return buffer.size() + inFlight; }
    }

    public long droppedCount() { return dropped.get(); }

    @Override
    @Async("logAsyncExecutor")
    public void saveLogAsync(Log log) {
        if (log == null) return;
        boolean flush;
        synchronized (bufferLock) {
            if (buffer.size() + inFlight >= MAX_PENDING) {
                long total = dropped.incrementAndGet();
                if (total == 1 || total % 100 == 0) {
                    logger.error("Audit buffer capacity exceeded; droppedTotal={}", total);
                }
                return;
            }
            buffer.addLast(log);
            flush = buffer.size() >= BATCH_SIZE && !retryDeferred;
        }
        if (flush) flushOne(false);
    }

    @Override
    @Async("logAsyncExecutor")
    @Transactional(rollbackFor = Exception.class)
    public void batchSaveLogsAsync(List<Log> logs) {
        if (logs == null || logs.isEmpty()) return;
        // Explicit transactional caller retains its exception/rollback contract.
        logMapper.batchInsert(logs);
    }

    private void flushOne(boolean scheduled) {
        if (!flushLock.tryLock()) return;
        try {
            List<Log> batch = new ArrayList<>(BATCH_SIZE);
            boolean verifyBeforeWrite;
            synchronized (bufferLock) {
                if (!scheduled && retryDeferred) return;
                verifyBeforeWrite = retryDeferred;
                while (batch.size() < BATCH_SIZE && !buffer.isEmpty()) batch.add(buffer.removeFirst());
                inFlight = batch.size();
            }
            if (batch.isEmpty()) return;
            List<Log> failed;
            try {
                // A retired partition layout may not have a global unique request_id.
                // Verify uncertain writes BEFORE trying a batch again, not only on duplicate key.
                List<Log> remaining = verifyBeforeWrite
                        ? batch.stream().filter(log -> !confirmed(log)).toList() : batch;
                if (!remaining.isEmpty()) logMapper.batchInsert(remaining);
                failed = List.of();
            } catch (RuntimeException failure) {
                logger.warn("Audit batch write failed: type={}, count={}", failure.getClass().getSimpleName(), batch.size());
                failed = unavailable(failure) ? batch : retryIndividually(batch);
            }
            synchronized (bufferLock) {
                // Reservation of in-flight slots prevents overflow while a batch is outside the lock.
                for (int i = failed.size() - 1; i >= 0; i--) buffer.addFirst(failed.get(i));
                inFlight = 0;
                retryDeferred = !failed.isEmpty();
                retried.addAndGet(failed.size());
            }
        } finally {
            flushLock.unlock();
        }
    }

    private List<Log> retryIndividually(List<Log> batch) {
        List<Log> failed = new ArrayList<>();
        for (int i = 0; i < batch.size(); i++) {
            Log log = batch.get(i);
            try {
                // A previous batch may have committed even if the client received an exception.
                if (!confirmed(log)) logMapper.insert(log);
            } catch (RuntimeException failure) {
                if (unavailable(failure)) {
                    failed.addAll(batch.subList(i, batch.size()));
                    break;
                }
                // Unique-key errors are success only when the expected request is actually visible.
                try {
                    if (confirmed(log)) continue;
                } catch (RuntimeException unavailable) {
                    failed.addAll(batch.subList(i, batch.size()));
                    break;
                }
                failed.add(log);
                logger.warn("Audit record retry deferred: type={}", failure.getClass().getSimpleName());
            }
        }
        return failed;
    }

    private boolean confirmed(Log log) {
        return log.getRequestId() != null && !log.getRequestId().isBlank() && logMapper.containsAudit(log);
    }

    private boolean unavailable(RuntimeException failure) {
        return failure instanceof DataAccessResourceFailureException || failure instanceof CannotGetJdbcConnectionException;
    }

    @Scheduled(fixedDelay = 5000)
    public void flushPendingLogs() {
        // At most one bounded batch per timer tick; no tight loop during an outage.
        flushOne(true);
    }

    @PreDestroy
    public void destroy() {
        // Do not spin indefinitely if the database stays unavailable.
        int before;
        do {
            before = pendingCount();
            if (before == 0) return;
            flushOne(true);
        } while (pendingCount() < before);
        logger.error("Audit shutdown with unresolved in-memory records: pending={}, droppedTotal={}", pendingCount(), dropped.get());
    }
}
