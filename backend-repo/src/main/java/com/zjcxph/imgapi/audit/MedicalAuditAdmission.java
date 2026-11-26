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
    private final GroupedAuditAdmission grouped;
    private final boolean groupedEnabled;
    private final boolean fastCapacity;
    private final int maxRecords;
    private final long maxBytes;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ReentrantLock flushLock=new ReentrantLock();
    private final Map<String,Log> reservations=new HashMap<>();
    private final Set<String> attemptedOutcomes=new HashSet<>();
    private final java.util.concurrent.atomic.AtomicLong deliveryFailures=new java.util.concurrent.atomic.AtomicLong();
    private boolean unavailable,closing,intentFailure;
    private final Map<String,Log> failedOutcomes=new HashMap<>();
    private int pendingIntent;
    private int activePublications;
    private final java.util.concurrent.atomic.AtomicLong agedUnpaired=new java.util.concurrent.atomic.AtomicLong(-1);
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
        fastCapacity=env.getProperty("mrr.audit.admission.fast-capacity",Boolean.class,false);
        groupedEnabled=env.getProperty("mrr.audit.admission.grouped",Boolean.class,false);
        grouped=groupedEnabled?new GroupedAuditAdmission(spool):null;
        transaction=new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setTimeout(10);
        rejected=metrics.counter("mrr.medical.audit.rejected");
        failed=metrics.counter("mrr.medical.audit.failure");
        metrics.gauge("mrr.medical.audit.pending",spool,EncryptedAuditSpool::pendingCount);
        metrics.gauge("mrr.medical.audit.reserved",this,MedicalAuditAdmission::reserved);
        metrics.gauge("mrr.medical.audit.aged.unpaired",agedUnpaired);
        metrics.gauge("mrr.medical.audit.published.bytes",spool,EncryptedAuditSpool::publishedBytes);
        metrics.gauge("mrr.medical.audit.file.forces",spool,EncryptedAuditSpool::fileForces);
        metrics.gauge("mrr.medical.audit.directory.forces",spool,EncryptedAuditSpool::directoryForces);
    }
    public synchronized int reserved(){return reservations.size();}

    public String admit(long actor,String requestId,String method,String route,String target)throws IOException{
        String correlation=UUID.randomUUID().toString();
        Log event=new Log();
        event.setRequestId(correlation+":I");
        event.setUsername(Long.toString(actor));event.setRequestUri(route);event.setEndpointTemplate(requestId);
        event.setMethod(method);event.setAuditTarget(target);event.setAuditAction("INTENT");
        event.setAuditDescription("ADMITTED");event.setAccessTime(new Date());
        validateSize(event);
        Log sizeProbe=new Log();
        sizeProbe.setRequestId(correlation+":O");sizeProbe.setUsername(event.getUsername());
        sizeProbe.setRequestUri(event.getRequestUri());sizeProbe.setEndpointTemplate(event.getEndpointTemplate());
        sizeProbe.setMethod(event.getMethod());sizeProbe.setAuditTarget(event.getAuditTarget());
        sizeProbe.setAuditAction("OUTCOME");sizeProbe.setAuditDescription("HTTP_EXCEPTION");
        sizeProbe.setResponseStatus("-2147483648");sizeProbe.setAccessTime(event.getAccessTime());
        validateSize(sizeProbe);
        synchronized(this){
            // Include unpublished INTENT plus the future OUTCOME. A publication already
            // visible in spool but not yet acknowledged is deliberately double-counted.
            long reservedSlots=reservations.size()+pendingIntent;
            var capacity=fastCapacity?spool.capacitySnapshot():spool.lockedCapacitySnapshot();
            if(closing||unavailable||capacity.records()+reservedSlots+2>maxRecords
                    ||capacity.bytes()+(reservedSlots+2)*EVENT_BYTES>maxBytes){
                rejected.increment();throw new IOException("Medical audit unavailable");
            }
            reservations.put(correlation,event);pendingIntent++;activePublications++;
        }
        try{
            publish(event);
            synchronized(this){
                pendingIntent--;
                // Do not enter business if another publication failed uncertainly or shutdown began.
                if(unavailable||closing){
                    reservations.remove(correlation);rejected.increment();
                    throw new AdmissionStoppedException();
                }
            }
            return correlation;
        }catch(AdmissionStoppedException e){
            throw new IOException("Medical audit unavailable");
        }catch(IOException|RuntimeException e){
            synchronized(this){pendingIntent--;reservations.remove(correlation);unavailable=true;intentFailure=true;}
            rejected.increment();failed.increment();
            logger.error("Medical audit intent publication uncertain: type={}",e.getClass().getSimpleName());
            throw new IOException("Medical audit unavailable");
        }finally{synchronized(this){activePublications--;notifyAll();}}
    }

    private static final class AdmissionStoppedException extends IOException {}

    public void complete(String correlation,int status,boolean exception){
        Log event=new Log();
        synchronized(this){
            Log intent=reservations.get(correlation);
            if(closing||intent==null||!attemptedOutcomes.add(correlation))return;
            event.setRequestId(correlation+":O");event.setUsername(intent.getUsername());event.setRequestUri(intent.getRequestUri());
            event.setEndpointTemplate(intent.getEndpointTemplate());event.setMethod(intent.getMethod());event.setAuditTarget(intent.getAuditTarget());
            event.setAuditAction("OUTCOME");event.setAuditDescription(exception?"HTTP_EXCEPTION":"HTTP_COMPLETED");
            event.setResponseStatus(Integer.toString(status));event.setAccessTime(new Date());
            activePublications++;
        }
        try{
            validateSize(event);publish(event);
            synchronized(this){reservations.remove(correlation);attemptedOutcomes.remove(correlation);}
        }catch(IOException|RuntimeException e){
            synchronized(this){unavailable=true;failedOutcomes.put(correlation,event);}
            failed.increment();
            logger.error("Medical audit outcome publication uncertain: type={}",e.getClass().getSimpleName());
        }finally{synchronized(this){activePublications--;notifyAll();}}
    }

    private void publish(Log event)throws IOException{
        if(grouped==null)spool.append(event);else grouped.append(event);
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
            var entries=fastCapacity?spool.peekConcurrent(50):spool.peek(50);
            if(entries.isEmpty()){recoverOneOutcome();return;}
            transaction.executeWithoutResult(status->{
                jdbc.execute("SET LOCAL lock_timeout='2s'");
                jdbc.execute("SET LOCAL statement_timeout='3s'");
                if(groupedEnabled)deliverBatch(entries);
                else for(var entry:entries)deliverOne(entry.log());
            });
            spool.acknowledgeSegments(entries.stream().map(EncryptedAuditSpool.Entry::name).toList());
            recoverOneOutcome();
        }catch(IOException|RuntimeException e){
            failed.increment();
            long total=deliveryFailures.incrementAndGet();
            if(total==1||total%100==0)logger.warn("Medical audit delivery deferred: type={}, failures={}",e.getClass().getSimpleName(),total);
        }finally{
            try{synchronized(this){if(closing&&activePublications==0)spool.close();}}catch(IOException e){logger.error("Medical audit close failed");}
            finally{flushLock.unlock();}
        }
    }
    // Called only by the single flush owner, after confirmed delivery or on empty spool.
    private void recoverOneOutcome()throws IOException{
        String correlation;Log event;
        synchronized(this){
            if(closing||failedOutcomes.isEmpty())return;
            var item=failedOutcomes.entrySet().iterator().next();
            correlation=item.getKey();event=item.getValue();activePublications++;
        }
        try{
            validateSize(event);
            spool.appendOrConfirm(event);
            synchronized(this){
                failedOutcomes.remove(correlation);reservations.remove(correlation);attemptedOutcomes.remove(correlation);
                if(failedOutcomes.isEmpty()&&!intentFailure)unavailable=false;
            }
        }finally{synchronized(this){activePublications--;notifyAll();}}
    }
    private static final String COLUMNS="event_id,correlation_id,request_id,phase,actor_id,method,route,target,observed_at,observation,http_status";
    private static final String TUPLE="(CAST(? AS varchar),CAST(? AS varchar),CAST(? AS varchar),CAST(? AS varchar),CAST(? AS bigint),CAST(? AS varchar),CAST(? AS varchar),CAST(? AS varchar),CAST(? AS timestamp),CAST(? AS varchar),CAST(? AS varchar))";
    private static Object[] arguments(Log row){
        String id=row.getRequestId();
        return new Object[]{id,id.substring(0,36),row.getEndpointTemplate(),row.getAuditAction(),
                Long.parseLong(row.getUsername()),row.getMethod(),row.getRequestUri(),row.getAuditTarget(),
                new java.sql.Timestamp(row.getAccessTime().getTime()),row.getAuditDescription(),row.getResponseStatus()};
    }
    private void deliverOne(Log row){
        Object[] args=arguments(row);
        jdbc.update("INSERT INTO app.medical_audit_event("+COLUMNS+") VALUES "+TUPLE+" ON CONFLICT DO NOTHING",args);
        Integer matched=jdbc.queryForObject("SELECT count(*) FROM app.medical_audit_event WHERE event_id=? AND ROW("+COLUMNS+") IS NOT DISTINCT FROM ROW"+TUPLE,
                Integer.class,java.util.stream.Stream.concat(java.util.stream.Stream.of(row.getRequestId()),Arrays.stream(args)).toArray());
        if(matched==null||matched!=1)throw new IllegalStateException("Medical audit identity conflict");
    }
    private void deliverBatch(List<EncryptedAuditSpool.Entry> entries){
        var unique=new TreeMap<String,Log>();
        for(var entry:entries){
            Log row=entry.log(), prior=unique.putIfAbsent(row.getRequestId(),row);
            if(prior!=null&&!Arrays.equals(arguments(prior),arguments(row)))
                throw new IllegalStateException("Conflicting medical event in batch");
        }
        var rows=new ArrayList<>(unique.values());
        String incoming="WITH incoming("+COLUMNS+") AS (VALUES "+String.join(",",Collections.nCopies(rows.size(),TUPLE))+") ";
        Object[] args=rows.stream().flatMap(row->Arrays.stream(arguments(row))).toArray();
        jdbc.update(incoming+"INSERT INTO app.medical_audit_event("+COLUMNS+") SELECT "+COLUMNS+" FROM incoming ORDER BY event_id ON CONFLICT DO NOTHING",args);
        String left=Arrays.stream(COLUMNS.split(",")).map(c->"e."+c).collect(java.util.stream.Collectors.joining(","));
        String right=Arrays.stream(COLUMNS.split(",")).map(c->"i."+c).collect(java.util.stream.Collectors.joining(","));
        Integer matched=jdbc.queryForObject(incoming+"SELECT count(*) FROM incoming i JOIN app.medical_audit_event e ON e.event_id=i.event_id WHERE ROW("+left+") IS NOT DISTINCT FROM ROW("+right+")",Integer.class,args);
        if(matched==null||matched!=rows.size())throw new IllegalStateException("Medical audit identity conflict");
    }

    @Scheduled(fixedDelayString="${mrr.audit.admission.monitor-delay-ms:60000}",initialDelayString="${mrr.audit.admission.monitor-delay-ms:60000}")
    public void monitorUnpaired(){
        try{
            Integer count=transaction.execute(status->{
                jdbc.execute("SET LOCAL statement_timeout='3s'");
                return jdbc.queryForObject("""
                    SELECT count(*) FROM (SELECT 1 FROM app.medical_audit_event i
                      WHERE i.phase='INTENT' AND i.observed_at<CURRENT_TIMESTAMP-interval '10 minutes'
                      AND NOT EXISTS (SELECT 1 FROM app.medical_audit_event o WHERE o.correlation_id=i.correlation_id AND o.phase='OUTCOME')
                      LIMIT 1000) aged
                    """,Integer.class);
            });
            long prior=agedUnpaired.getAndSet(count==null?-1:count);
            if(count!=null&&count>0&&prior!=count)logger.warn("Medical audit aged unmatched intents (capped at 1000): {}",count);
        }catch(RuntimeException e){agedUnpaired.set(-1);}
    }

    @PreDestroy @Override public void close()throws IOException{
        synchronized(this){closing=true;}
        if(grouped!=null)grouped.close();
        synchronized(this){
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while(activePublications>0){
                long left=deadline-System.nanoTime();
                if(left<=0)throw new IOException("Medical audit publication still active");
                try{java.util.concurrent.TimeUnit.NANOSECONDS.timedWait(this,left);}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Medical audit shutdown interrupted");}
            }
        }
        if(flushLock.tryLock()){
            try{spool.close();}finally{flushLock.unlock();}
        }
    }
}
