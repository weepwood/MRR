package com.zjcxph.imgapi.ocr;

import com.zjcxph.imgapi.config.OcrRuntimeProperties;
import jakarta.annotation.PreDestroy;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/** One persistent model process per bounded worker thread; it has no database credentials. */
@Component
public class OcrEngine {
    private final OcrRuntimeProperties properties;
    private final Map<Long, Worker> workers = new ConcurrentHashMap<>();
    private final AtomicLong generation = new AtomicLong();
    private final ExecutorService readers = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "ocr-protocol"); thread.setDaemon(true); return thread;
    });
    public OcrEngine(OcrRuntimeProperties properties) { this.properties = properties; }

    public String fingerprint(String profile) {
        var p = properties.getProfiles().get(profile);
        if (p == null || p.getManifestSha256() == null || !p.getManifestSha256().matches("[a-fA-F0-9]{64}"))
            throw new OcrFailure("PROFILE_UNAVAILABLE");
        try (var input = Files.newInputStream(Path.of(p.getModelDirectory()).resolve("manifest.json"))) {
            String actual = DigestUtils.sha256Hex(input);
            if (!actual.equalsIgnoreCase(p.getManifestSha256())) throw new OcrFailure("MODEL_INVALID");
            if (!Files.isRegularFile(Path.of(p.getExecutable())) || !Files.isRegularFile(Path.of(p.getScript())))
                throw new OcrFailure("PROFILE_UNAVAILABLE");
            return actual;
        } catch (OcrFailure e) { throw e; }
        catch (Exception e) { throw new OcrFailure("PROFILE_UNAVAILABLE"); }
    }

    public OcrProtocol.Result recognize(String profile, Path input, int timeout, int maxOutput, BooleanSupplier cancelled) {
        String fingerprint = fingerprint(profile);
        long thread = Thread.currentThread().threadId();
        long version = generation.get();
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new OcrFailure("CANCELLED");
        Worker worker;
        // Only registration is serialized; reset must never wait for a page's inference timeout.
        synchronized (workers) {
            if (version != generation.get()) throw new OcrFailure("CANCELLED");
            worker = workers.get(thread);
            if (worker == null || !worker.profile.equals(profile) || !worker.fingerprint.equals(fingerprint) || !worker.process.isAlive()) {
                if (worker != null) worker.close();
                worker = start(profile, fingerprint); workers.put(thread, worker);
            }
        }
        String id = java.util.UUID.randomUUID().toString();
        Worker current = worker;
        current.output.set(0);
        Future<String> response = null;
        try {
            String command = OcrProtocol.JSON.writeValueAsString(Map.of("id", id, "path", input.toAbsolutePath().toString()));
            current.process.getOutputStream().write((command + "\n").getBytes(StandardCharsets.UTF_8));
            current.process.getOutputStream().flush();
            response = readers.submit(() -> OcrProtocol.readLine(current.process.getInputStream(), maxOutput));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeout);
            while (true) {
                if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new OcrFailure("CANCELLED");
                if (current.output.get() > maxOutput) throw new OcrFailure("OUTPUT_LIMIT");
                if (System.nanoTime() > deadline) throw new OcrFailure("TIMEOUT");
                try {
                    String line = response.get(100, TimeUnit.MILLISECONDS);
                    if (line.getBytes(StandardCharsets.UTF_8).length + current.output.get() > maxOutput)
                        throw new OcrFailure("OUTPUT_LIMIT");
                    return OcrProtocol.parse(line, id);
                } catch (TimeoutException ignored) { /* poll persisted cancellation */ }
            }
        } catch (OcrFailure e) { current.close(); workers.remove(thread); throw e; }
        catch (Exception e) {
            current.close(); workers.remove(thread);
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            if (e instanceof ExecutionException && e.getCause() instanceof OcrFailure failure) throw failure;
            throw new OcrFailure("ENGINE_EXIT");
        } finally { if (response != null) response.cancel(true); }
    }

    private Worker start(String profile, String fingerprint) {
        var p = properties.getProfiles().get(profile);
        try {
            ProcessBuilder builder = new ProcessBuilder(p.getExecutable(), "-X", "utf8", "-I", p.getScript(), "--models", p.getModelDirectory(),
                    "--input-root", Path.of(properties.getTempDirectory()).toAbsolutePath().normalize().toString(), "--threads", "2");
            // Do not inherit database passwords, access tokens, proxy settings, or arbitrary Python hooks.
            String systemRoot = System.getenv("SystemRoot");
            builder.environment().clear();
            if (systemRoot != null) builder.environment().put("SystemRoot", systemRoot);
            builder.environment().put("PYTHONIOENCODING", "utf-8");
            Worker worker = new Worker(profile, fingerprint, builder.start());
            readers.submit(() -> {
                try (var stderr = worker.process.getErrorStream()) {
                    byte[] buffer = new byte[4096]; int n;
                    while ((n = stderr.read(buffer)) >= 0) {
                        if (worker.output.addAndGet(n) > 4L * 1024 * 1024) { worker.close(); break; }
                    }
                } catch (Exception ignored) { /* No raw process output leaves this boundary. */ }
            });
            return worker;
        } catch (Exception e) { throw new OcrFailure("ENGINE_EXIT"); }
    }

    private record Worker(String profile, String fingerprint, Process process, AtomicLong output) {
        Worker(String profile, String fingerprint, Process process) { this(profile, fingerprint, process, new AtomicLong()); }
        void close() { process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); }
    }
    /** Release model memory on disable without closing the reusable protocol executor. */
    public void reset() {
        synchronized (workers) { generation.incrementAndGet(); workers.values().forEach(Worker::close); workers.clear(); }
    }
    @PreDestroy public void close() { reset(); readers.shutdownNow(); }
}
