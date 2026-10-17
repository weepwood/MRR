package com.zjcxph.imgapi.ocr;

import com.zjcxph.imgapi.config.OcrRuntimeProperties;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.service.ImageContentService;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import jakarta.annotation.PreDestroy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

@Component
public class OcrInput {
    private final ImageContentService images;
    private final OcrRuntimeProperties properties;
    private final ExecutorService readers=new ThreadPoolExecutor(0,8,30,TimeUnit.SECONDS,new SynchronousQueue<>(),r->{
        Thread thread=new Thread(r,"ocr-source-read");thread.setDaemon(true);return thread;
    },new ThreadPoolExecutor.AbortPolicy());
    public OcrInput(ImageContentService images, OcrRuntimeProperties properties) { this.images=images; this.properties=properties; }
    public record Prepared(Path path, String hash) implements AutoCloseable {
        @Override public void close() {
            try { Files.deleteIfExists(path); } catch (java.io.IOException ignored) { /* retry by cleanup */ }
        }
    }
    public Prepared prepare(Integer scan, BooleanSupplier cancelled) {
        var abandoned=new java.util.concurrent.atomic.AtomicBoolean();
        var produced=new java.util.concurrent.atomic.AtomicReference<Prepared>();
        Future<Prepared> work;
        try {work=readers.submit(()->{
            Prepared result=copy(scan,()->abandoned.get()||cancelled.getAsBoolean()||Thread.currentThread().isInterrupted());
            produced.set(result);
            if(abandoned.get()) {Prepared orphan=produced.getAndSet(null);if(orphan!=null) orphan.close();throw new OcrFailure("CANCELLED");}
            return result;
        });}
        catch(RejectedExecutionException full) {throw new OcrFailure("SOURCE_UNAVAILABLE");}
        long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(Math.max(50,Math.min(30000,properties.getReadTimeoutMillis())));
        try {
            while(true) {
                if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted()) throw new OcrFailure("CANCELLED");
                if(System.nanoTime()>=deadline) throw new OcrFailure("TIMEOUT");
                try {Prepared result=work.get(25,TimeUnit.MILLISECONDS);produced.set(null);return result;}
                catch(TimeoutException waiting){ /* bounded polling */ }
            }
        } catch(ExecutionException failure) {
            if(failure.getCause() instanceof OcrFailure safe) throw safe;
            throw new OcrFailure("SOURCE_UNAVAILABLE");
        } catch(InterruptedException interrupted) {Thread.currentThread().interrupt();throw new OcrFailure("CANCELLED");}
        finally {abandoned.set(true);work.cancel(true);Prepared orphan=produced.getAndSet(null);if(orphan!=null) orphan.close();}
    }
    private Prepared copy(Integer scan, BooleanSupplier cancelled) {
        Path path = null;
        try {
            Path root = Path.of(properties.getTempDirectory()).toAbsolutePath().normalize();
            Files.createDirectories(root);
            root = root.toRealPath();
            path = Files.createTempFile(root, "page-", ".ocr");
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            long deadline = System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
            try (var content=images.open(scan); var output=Files.newOutputStream(path)) {
                byte[] buffer=new byte[65536]; long total=0; int n;
                while ((n=content.inputStream().read(buffer))!=-1) {
                    if (cancelled.getAsBoolean()) throw new OcrFailure("CANCELLED");
                    if (System.nanoTime()>deadline) throw new OcrFailure("TIMEOUT");
                    total+=n;
                    if (total>Math.min(50L*1024*1024, properties.getMaxInputBytes())) throw new OcrFailure("INPUT_LIMIT");
                    hash.update(buffer,0,n); output.write(buffer,0,n);
                }
                if (total==0) throw new OcrFailure("INVALID_IMAGE");
            }
            if(cancelled.getAsBoolean()) throw new OcrFailure("CANCELLED");
            return new Prepared(path, HexFormat.of().formatHex(hash.digest()));
        } catch (Exception e) {
            if (path!=null) new Prepared(path, "").close();
            if (e instanceof OcrFailure failure) throw failure;
            if (e instanceof BusinessException business && business.getCode()==404) throw new OcrFailure("SOURCE_MISSING");
            throw new OcrFailure("SOURCE_UNAVAILABLE");
        }
    }
    @Scheduled(fixedDelay=900000) public void cleanup() {
        Path root=Path.of(properties.getTempDirectory()).toAbsolutePath().normalize();
        if(!Files.isDirectory(root)) return;
        long cutoff=System.currentTimeMillis()-TimeUnit.HOURS.toMillis(24);
        try(var files=Files.newDirectoryStream(root,"page-*.ocr")) {
            int scanned=0;
            for(Path file:files) {
                if(++scanned>1000) break;
                if(Files.isRegularFile(file,java.nio.file.LinkOption.NOFOLLOW_LINKS)&&Files.getLastModifiedTime(file).toMillis()<cutoff)
                    Files.deleteIfExists(file);
            }
        } catch(java.io.IOException ignored) { /* Only managed names are retried next cleanup cycle. */ }
    }
    @PreDestroy public void close() {readers.shutdownNow();}
}
