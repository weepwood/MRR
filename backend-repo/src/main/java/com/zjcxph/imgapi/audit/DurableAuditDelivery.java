package com.zjcxph.imgapi.audit;

import com.zjcxph.imgapi.entity.Log;
import com.zjcxph.imgapi.mapper.LogMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

/** Database transaction fence for cooperating durable writers, including retired partitions. */
public final class DurableAuditDelivery {
    public static final class IdentityConflictException extends IllegalStateException {
        public IdentityConflictException(){super("Audit request identity conflict");}
    }
    private final LogMapper mapper;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final boolean batched;

    public DurableAuditDelivery(LogMapper mapper, JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this(mapper,jdbc,manager,false);
    }

    public DurableAuditDelivery(LogMapper mapper, JdbcTemplate jdbc, PlatformTransactionManager manager, boolean batched) {
        this.batched=batched;
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
            if(batched) {
                deliverBatch(ordered);
                return;
            }
            for (Log row : ordered) {
                // Use a dedicated 64-bit advisory namespace. A hash collision may still deadlock;
                // rollback retains the entire batch for later retry.
                jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 614508237))",
                        rs -> {}, row.getRequestId());
                long count = mapper.countRequestIdentity(row.getRequestId());
                if (count == 0) {
                    mapper.insert(row);
                } else if (count != 1 || !mapper.matchesDurableAudit(row)) {
                    throw new IdentityConflictException();
                }
            }
        });
    }

    private void deliverBatch(List<Log> ordered) {
        Map<String,Log> distinct=new LinkedHashMap<>();
        for(Log row:ordered) {
            Log previous=distinct.putIfAbsent(row.getRequestId(),row);
            if(previous!=null&&!payload(previous).equals(payload(row)))throw new IdentityConflictException();
        }
        var rows=List.copyOf(distinct.values());
        // One statement to lock all hash keys in a deterministic numeric order.
        // READ COMMITTED: reconciliation runs in a separate statement AFTER waiting for locks.
        String placeholders=String.join(",",Collections.nCopies(rows.size(),"?"));
        jdbc.query("SELECT pg_advisory_xact_lock(k) FROM (SELECT DISTINCT hashtextextended(id,614508237) AS k FROM unnest(ARRAY["+placeholders+"]::text[]) AS t(id)) AS keys ORDER BY k",
                rs->{},rows.stream().map(Log::getRequestId).toArray());
        var observed=mapper.reconcileAuditBatch(rows);
        if(observed.size()!=rows.size())throw new IllegalStateException("Audit batch reconciliation incomplete");
        List<Log> missing=new ArrayList<>();
        for(var item:observed){
            Log row=distinct.remove((String)item.get("request_id"));
            if(row==null)throw new IllegalStateException("Audit batch identity mismatch");
            long count=((Number)item.get("existing_count")).longValue();
            long matched=((Number)item.get("matched_count")).longValue();
            if(count==0)missing.add(row);
            else if(count!=1||matched!=1)throw new IdentityConflictException();
        }
        if(!distinct.isEmpty())throw new IllegalStateException("Audit batch reconciliation incomplete");
        if(!missing.isEmpty()&&mapper.batchInsert(missing)!=missing.size())throw new IllegalStateException("Audit batch insert incomplete");
    }

    private static List<Object> payload(Log l) {
        return Arrays.asList(l.getUsername(),l.getClientIp(),l.getRequestUri(),l.getEndpointTemplate(),l.getMethod(),
                l.getUserAgent(),l.getAccessTime(),l.getQueryString(),l.getRequestBody(),l.getResponseStatus(),l.getExecuteTime(),
                l.getReferer(),l.getErrorMessage(),l.getAuditAction(),l.getAuditTarget(),l.getAuditDescription());
    }
}
