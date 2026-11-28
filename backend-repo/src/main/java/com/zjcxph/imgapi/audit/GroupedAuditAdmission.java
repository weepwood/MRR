package com.zjcxph.imgapi.audit;

import com.zjcxph.imgapi.entity.Log;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/** Bounded synchronous admission. No caller receives success before its segment is forced. */
final class GroupedAuditAdmission implements AutoCloseable {
    private record Request(Log log, CompletableFuture<Void> result) {}
    private final EncryptedAuditSpool spool;
    private final ArrayBlockingQueue<Request> queue=new ArrayBlockingQueue<>(128);
    private final Thread writer;
    private boolean closing;

    GroupedAuditAdmission(EncryptedAuditSpool spool) {
        this.spool=spool;
        writer=Thread.ofPlatform().daemon().name("audit-group-writer").start(this::run);
    }

    void append(Log log) throws IOException {
        var request=new Request(log,new CompletableFuture<>());
        synchronized(this){
            if(closing)throw new IOException("Audit admission closed");
            if(!queue.offer(request))throw new EncryptedAuditSpool.CapacityException();
        }
        // Do not report interruption as a definite failed write when the disk outcome is uncertain.
        try{request.result().join();}
        catch(CompletionException e){throw new IOException("Audit group admission failed");}
    }

    private void run(){
        while(true){
            synchronized(this){if(closing&&queue.isEmpty())return;}
            List<Request> group=new ArrayList<>(16);
            try {
                Request first=queue.poll(10,TimeUnit.MILLISECONDS);
                if(first==null)continue;
                group.add(first);
                long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(3);
                while(group.size()<16){
                    long left=deadline-System.nanoTime();
                    if(left<=0)break;
                    Request next=queue.poll(left,TimeUnit.NANOSECONDS);
                    if(next==null)break;
                    group.add(next);
                }
                spool.appendGroup(group.stream().map(Request::log).toList());
                group.forEach(r->r.result().complete(null));
            } catch(IOException|RuntimeException failure){
                group.forEach(r->r.result().completeExceptionally(new IOException("Audit segment write failed")));
            } catch(InterruptedException interrupted){
                group.forEach(r->r.result().completeExceptionally(new IOException("Audit writer interrupted")));
                synchronized(this){closing=true;Request r;while((r=queue.poll())!=null)r.result().completeExceptionally(new IOException("Audit writer stopped"));}
                Thread.currentThread().interrupt();return;
            }
        }
    }

    @Override public void close() throws IOException {
        synchronized(this){closing=true;}
        try{writer.join(5000);}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Audit writer shutdown interrupted");}
        if(writer.isAlive())throw new IOException("Audit writer still owns spool");
    }
}
