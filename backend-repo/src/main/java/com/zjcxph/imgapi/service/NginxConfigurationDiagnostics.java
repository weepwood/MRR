package com.zjcxph.imgapi.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
public class NginxConfigurationDiagnostics {
    private final boolean enabled;
    private final boolean windows;
    private final Path root;
    private final Launcher launcher;
    private final Semaphore permit = new Semaphore(1);

    @FunctionalInterface
    interface Launcher {
        Process start(List<String> command, Path directory) throws IOException;
    }

    @Autowired
    public NginxConfigurationDiagnostics(
            @Value("${app.operations.nginx.enabled:false}") boolean enabled,
            @Value("${app.operations.nginx.root:}") String configuredRoot) {
        this(enabled, System.getProperty("os.name","").toLowerCase(Locale.ROOT).startsWith("windows"),
                localWindowsRoot(configuredRoot), NginxConfigurationDiagnostics::start);
    }

    NginxConfigurationDiagnostics(boolean enabled, boolean windows, Path root, Launcher launcher) {
        this.enabled = enabled;
        this.windows = windows;
        this.root = root;
        this.launcher = launcher;
    }

    static Path localWindowsRoot(String value) {
        // Windows local drive only; no UNC, relative path, device path or HTTP input.
        if (value == null || !value.matches("^[A-Za-z]:[\\\\/].*")) return null;
        try {
            Path path = Path.of(value);
            return path.isAbsolute() ? path.normalize() : null;
        } catch (RuntimeException invalid) { return null; }
    }

    private static Process start(List<String> command, Path directory) throws IOException {
        return new ProcessBuilder(command).directory(directory.toFile())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
    }

    private record Installation(Path executable, Path prefix, Path config, BasicFileAttributes configAttributes) {}
    public record Status(boolean enabled, String state, Long configBytes, Instant configModifiedAt) {}
    public record Check(String status, Instant checkedAt, Integer exitCode) {}

    private String unavailable() {
        if (!enabled) return "DISABLED";
        if (!windows) return "UNSUPPORTED_PLATFORM";
        if (root == null) return "NOT_CONFIGURED";
        return null;
    }

    private Installation installation() throws IOException {
        // Do not accept redirected fixed installation files. The root and config includes
        // must be controlled by the host administrator and protected by Windows ACLs.
        if (!root.toRealPath().equals(root)) throw new IOException("installation root redirected");
        Path prefix = fixed("runtime/nginx");
        Path executable = fixed("runtime/nginx/nginx.exe");
        Path config = fixed("config/nginx/nginx.conf");
        if (!Files.isDirectory(prefix) || !Files.isRegularFile(executable) || !Files.isRegularFile(config))
            throw new IOException("missing installation");
        return new Installation(executable,prefix,config,Files.readAttributes(config,BasicFileAttributes.class));
    }

    private Path fixed(String relative) throws IOException {
        Path path = root.resolve(relative).normalize();
        if (!path.startsWith(root) || !path.toRealPath().equals(path)) throw new IOException("redirected installation");
        return path;
    }

    public Status status() {
        String unavailable = unavailable();
        if (unavailable != null) return new Status(enabled,unavailable,null,null);
        try {
            var config = installation().configAttributes();
            return new Status(true,"AVAILABLE",config.size(),config.lastModifiedTime().toInstant());
        } catch (IOException | RuntimeException ignored) {
            return new Status(true,"INSTALLATION_UNAVAILABLE",null,null);
        }
    }

    public Check check() {
        String unavailable = unavailable();
        if (unavailable != null) return result(unavailable,null);
        if (!permit.tryAcquire()) return result("BUSY",null);
        Process process = null;
        try {
            var files = installation();
            // Fixed executable and arguments; no shell, reload, configuration editing or output disclosure.
            process = launcher.start(List.of(files.executable().toString(),"-p",files.prefix().toString(),
                    "-c",files.config().toString(),"-t"),files.prefix());
            process.getOutputStream().close();
            if (!process.waitFor(8,TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return result("TIMEOUT",null);
            }
            int code = process.exitValue();
            return result(code == 0 ? "PASSED" : "FAILED",code);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return result("INTERRUPTED",null);
        } catch (IOException | RuntimeException ignored) {
            return result("UNAVAILABLE",null);
        } finally {
            // Never release the single execution permit while the old process is still alive.
            if (process != null && process.isAlive()) {
                try {
                    process.destroyForcibly();
                } finally {
                    if (process.isAlive()) process.onExit().thenRun(permit::release);
                    else permit.release();
                }
            } else permit.release();
        }
    }

    private Check result(String status, Integer exit) { return new Check(status,Instant.now(),exit); }
}
