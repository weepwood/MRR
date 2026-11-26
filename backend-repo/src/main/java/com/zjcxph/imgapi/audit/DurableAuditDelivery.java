package com.zjcxph.imgapi.audit;

import com.zjcxph.imgapi.entity.Log;
import com.zjcxph.imgapi.mapper.LogMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Comparator;
import java.util.List;

/** Database transaction fence for cooperating durable writers, including retired partitions. */
public final class DurableAuditDelivery {
    private final LogMapper mapper;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public DurableAuditDelivery(LogMapper mapper, JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.mapper = mapper;
        this.jdbc = jdbc;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(10);
    }

    public void deliver(List<Log> rows) {
        if (rows.isEmpty() || rows.size() > 50) throw new IllegalArgumentException("Invalid audit delivery batch");
        // One stable order across all cooperating instances; collisions serialize rather than bypass a fence.
        var ordered = rows.stream().sorted(Comparator.comparing(Log::getRequestId)).toList();
        transaction.executeWithoutResult(status -> {
            jdbc.execute("SET LOCAL lock_timeout = '2s'");
            jdbc.execute("SET LOCAL statement_timeout = '3s'");
            for (Log row : ordered) {
                // Use a dedicated 64-bit advisory namespace. A hash collision may still deadlock;
                // rollback retains the entire batch for later retry.
                jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 614508237))",
                        rs -> {}, row.getRequestId());
                long count = mapper.countRequestIdentity(row.getRequestId());
                if (count == 0) {
                    mapper.insert(row);
                } else if (count != 1 || !mapper.matchesDurableAudit(row)) {
                    throw new IllegalStateException("Audit request identity conflict");
                }
            }
        });
    }
}
