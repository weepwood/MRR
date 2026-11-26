package com.zjcxph.imgapi.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.entity.Log;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/** Experimental internal medical gate; independent ledger and key. No plaintext disk fallback. */
@Service
@ConditionalOnProperty(name="mrr.audit.admission.enabled",havingValue="true")
public class MedicalAuditAdmission implements AutoCloseable {
    private static final int EVENT_BYTES=4096;
    private final EncryptedAuditSpool spool;
    private final ObjectMapper json;
    private final int maxRecords;
    private final long maxBytes;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ReentrantLock flushLock=new ReentrantLock();
    private final Map<String,Log> reservations=new HashMap<>();
    private final Set<String> attemptedOutcomes=new HashSet<>();
    private final java.util.concurrent.atomic.AtomicLong deliveryFailures=new java.util.concurrent.atomic.AtomicLong();
    private boolean unavailable,closing;
    private final io.micrometer.core.instrument.Counter rejected,failed;
    private static final org.slf4j.Logger logger=org.slf4j.LoggerFactory.getLogger(MedicalAuditAdmission.class);

    public MedicalAuditAdmission(Environment env,ObjectMapper json,JdbcTemplate jdbc,PlatformTransactionManager manager,
                                 io.micrometer.core.instrument.MeterRegistry metrics)throws IOException {
        this.json=json;this.jdbc=jdbc;
        maxRecords=env.getProperty("mrr.audit.admission.max-records",Integer.class,10000);
        maxBytes=env.getProperty("mrr.audit.admission.max-bytes",Long.class,64L*1024*1024);
        if(maxRecords<2||maxBytes<2L*EVENT_BYTES)throw new IOException("Medical audit capacity too small");
        // Explicitly separate the ordinary and medical key domains, including configured key rings.
        AuditSpoolKeys.requireIndependent(env,"MRR_MEDICAL_AUDIT","MRR_AUDIT_SPOOL",json);
        spool=AuditSpoolKeys.open(env,"MRR_MEDICAL_AUDIT",Path.of(env.getRequiredProperty("mrr.audit.admission.directory")),
                maxRecords,maxBytes,json);
        transaction=new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setTimeout(10);
        rejected=metrics.counter("mrr.medical.audit.rejected");
        failed=metrics.counter("mrr.medical.audit.failure");
        metrics.gauge("mrr.medical.audit.pending",spool,EncryptedAuditSpool::pendingCount);
        metrics.gauge("mrr.medical.audit.reserved",this,MedicalAuditAdmission::reserved);
        metrics.gauge("mrr.medical.audit.published.bytes",spool,EncryptedAuditSpool::publishedBytes);
        metrics.gauge("mrr.medical.audit.file.forces",spool,EncryptedAuditSpool::fileForces);
        metrics.gauge("mrr.medical.audit.directory.forces",spool,EncryptedAuditSpool::directoryForces);
    }
    public synchronized int reserved(){return reservations.size();}

    public synchronized String admit(long actor,String requestId,String method,String route,String target)throws IOException{
        if(closing||unavailable||spool.pendingCount()+reservations.size()+2>maxRecords
                ||spool.pendingBytes()+(reservations.size()+2L)*EVENT_BYTES>maxBytes){
            rejected.increment();throw new IOException("Medical audit unavailable");
        }
        String correlation=UUID.randomUUID().toString();
        Log event=new Log();
        event.setRequestId(correlation+":I");
        event.setUsername(Long.toString(actor));event.setRequestUri(route);event.setEndpointTemplate(requestId);
        event.setMethod(method);event.setAuditTarget(target);event.setAuditAction("INTENT");
        event.setAuditDescription("ADMITTED");event.setAccessTime(new Date());
        try{
            validateSize(event);spool.append(event);
            reservations.put(correlation,event);
            return correlation;
        }catch(IOException|RuntimeException e){
            unavailable=true;rejected.increment();failed.increment();
            logger.error("Medical audit intent publication uncertain: type={}",e.getClass().getSimpleName());
            throw new IOException("Medical audit unavailable");
        }
    }

    public synchronized void complete(String correlation,int status,boolean exception){
        Log intent=reservations.get(correlation);
        // A publication error may have happened after atomic rename. Never generate a different
        // timestamp/status for the same event identity on a second MVC/container callback.
        if(intent==null||!attemptedOutcomes.add(correlation))return;
        Log event=new Log();
        event.setRequestId(correlation+":O");event.setUsername(intent.getUsername());event.setRequestUri(intent.getRequestUri());
        event.setEndpointTemplate(intent.getEndpointTemplate());event.setMethod(intent.getMethod());event.setAuditTarget(intent.getAuditTarget());
        event.setAuditAction("OUTCOME");event.setAuditDescription(exception?"HTTP_EXCEPTION":"HTTP_COMPLETED");
        event.setResponseStatus(Integer.toString(status));event.setAccessTime(new Date());
        try{
            validateSize(event);spool.append(event);reservations.remove(correlation);attemptedOutcomes.remove(correlation);
        }catch(IOException|RuntimeException e){
            unavailable=true;failed.increment();
            logger.error("Medical audit outcome publication uncertain: type={}",e.getClass().getSimpleName());
        }
    }

    private void validateSize(Log row)throws IOException{
        byte[] encoded=json.writeValueAsBytes(row);
        try{if(encoded.length+64>EVENT_BYTES)throw new IOException("Medical audit event too large");}
        finally{Arrays.fill(encoded,(byte)0);}
    }

    @Scheduled(fixedDelayString="${mrr.audit.admission.flush-delay-ms:250}")
    public void flush(){
        if(!flushLock.tryLock())return;
        try{
            synchronized(this){if(closing)return;}
            var entries=spool.peek(50);
            if(entries.isEmpty())return;
            transaction.executeWithoutResult(status->{
                jdbc.execute("SET LOCAL lock_timeout='2s'");
                jdbc.execute("SET LOCAL statement_timeout='3s'");
                for(var entry:entries){
                    Log row=entry.log();
                    String id=row.getRequestId();
                    Object[] args={id,id.substring(0,36),row.getEndpointTemplate(),row.getAuditAction(),
                            Long.parseLong(row.getUsername()),row.getMethod(),row.getRequestUri(),row.getAuditTarget(),
                            new java.sql.Timestamp(row.getAccessTime().getTime()),row.getAuditDescription(),row.getResponseStatus()};
                    jdbc.update("""
                            INSERT INTO app.medical_audit_event(event_id,correlation_id,request_id,phase,actor_id,method,route,target,observed_at,observation,http_status)
                            VALUES (?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING
                            """,args);
                    Integer matched=jdbc.queryForObject("""
                            SELECT count(*) FROM app.medical_audit_event
                            WHERE ROW(event_id,correlation_id,request_id,phase,actor_id,method,route,target,observed_at,observation,http_status)
                            IS NOT DISTINCT FROM ROW(CAST(? AS varchar),CAST(? AS varchar),CAST(? AS varchar),CAST(? AS varchar),
                            CAST(? AS bigint),CAST(? AS varchar),CAST(? AS varchar),CAST(? AS varchar),CAST(? AS timestamp),CAST(? AS varchar),CAST(? AS varchar))
                            """,Integer.class,args);
                    if(matched==null||matched!=1)throw new IllegalStateException("Medical audit identity conflict");
                }
            });
            spool.acknowledgeSegments(entries.stream().map(EncryptedAuditSpool.Entry::name).toList());
        }catch(IOException|RuntimeException e){
            failed.increment();
            long total=deliveryFailures.incrementAndGet();
            if(total==1||total%100==0)logger.warn("Medical audit delivery deferred: type={}, failures={}",e.getClass().getSimpleName(),total);
        }finally{
            try{synchronized(this){if(closing)spool.close();}}catch(IOException e){logger.error("Medical audit close failed");}
            finally{flushLock.unlock();}
        }
    }
    @PreDestroy @Override public void close()throws IOException{
        synchronized(this){closing=true;}
        if(flushLock.tryLock()){
            try{spool.close();}finally{flushLock.unlock();}
        }
    }
}
