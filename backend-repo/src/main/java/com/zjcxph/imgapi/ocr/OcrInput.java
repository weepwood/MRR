package com.zjcxph.imgapi.ocr;

import com.zjcxph.imgapi.config.OcrRuntimeProperties;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.service.ImageContentService;
import org.springframework.stereotype.Component;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

@Component
public class OcrInput {
    private final ImageContentService images;
    private final OcrRuntimeProperties properties;
    public OcrInput(ImageContentService images, OcrRuntimeProperties properties) { this.images=images; this.properties=properties; }
    public record Prepared(Path path, String hash) implements AutoCloseable {
        @Override public void close() {
            try { Files.deleteIfExists(path); } catch (java.io.IOException ignored) { /* retry by cleanup */ }
        }
    }
    public Prepared prepare(Integer scan, BooleanSupplier cancelled) {
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
            return new Prepared(path, HexFormat.of().formatHex(hash.digest()));
        } catch (Exception e) {
            if (path!=null) new Prepared(path, "").close();
            if (e instanceof OcrFailure failure) throw failure;
            if (e instanceof BusinessException business && business.getCode()==404) throw new OcrFailure("SOURCE_MISSING");
            throw new OcrFailure("SOURCE_UNAVAILABLE");
        }
    }
}
