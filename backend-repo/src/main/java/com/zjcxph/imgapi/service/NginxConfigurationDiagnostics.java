package com.zjcxph.imgapi.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.util.TreeSet;
import java.util.concurrent.FutureTask;
import java.util.regex.Pattern;
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
    private final Launcher versionLauncher;
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
                localWindowsRoot(configuredRoot), NginxConfigurationDiagnostics::start, NginxConfigurationDiagnostics::startVersion);
    }

    NginxConfigurationDiagnostics(boolean enabled, boolean windows, Path root, Launcher launcher) {
        this(enabled,windows,root,launcher,NginxConfigurationDiagnostics::startVersion);
    }

    NginxConfigurationDiagnostics(boolean enabled, boolean windows, Path root, Launcher launcher, Launcher versionLauncher) {
        this.enabled = enabled;
        this.windows = windows;
        this.root = root;
        this.launcher = launcher;
        this.versionLauncher = versionLauncher;
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

    private static Process startVersion(List<String> command,Path directory) throws IOException {
        return new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
    }

    private static final Pattern VERSION=Pattern.compile("nginx version: nginx/([0-9]{1,4}\\.[0-9]{1,4}\\.[0-9]{1,4})");
    // Strict textual hints only: no addresses, variables, includes or effective configuration claim.
    private static final Pattern LISTEN=Pattern.compile("^\\s*listen\\s+([0-9]{1,5})(?:\\s+default_server)?\\s*;\\s*(?:#.*)?$");
    public record Version(String state,String version,Instant observedAt) {}
    public record ListenerHints(String state,List<Integer> ports,Instant observedAt) {}

    public ListenerHints listenerHints() {
        String state=unavailable();
        if(state!=null) return new ListenerHints(state,List.of(),Instant.now());
        try {
            var file=installation().config();
            byte[] bytes;
            try(var input=Files.newInputStream(file,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)) {
                bytes=input.readNBytes(65537);
            }
            if(bytes.length>65536) return new ListenerHints("CONFIG_TOO_LARGE",List.of(),Instant.now());
            var ports=new TreeSet<Integer>();
            for(String line:new String(bytes,StandardCharsets.UTF_8).split("\\R")) {
                var match=LISTEN.matcher(line);
                if(match.matches()) {
                    int port=Integer.parseInt(match.group(1));
                    if(port>0&&port<=65535) ports.add(port);
                    if(ports.size()>32) return new ListenerHints("CONFIG_TOO_COMPLEX",List.of(),Instant.now());
                }
            }
            return new ListenerHints("TEXT_HINTS",List.copyOf(ports),Instant.now());
        } catch(IOException|RuntimeException ignored) {
            return new ListenerHints("UNAVAILABLE",List.of(),Instant.now());
        }
    }

    public Version version() {
        String state=unavailable();
        if(state!=null) return new Version(state,null,Instant.now());
        if(!permit.tryAcquire()) return new Version("BUSY",null,Instant.now());
        Process process=null;
        FutureTask<byte[]> reader=null;
        try {
            var files=installation();
            process=versionLauncher.start(List.of(files.executable().toString(),"-v"),files.prefix());
            process.getOutputStream().close();
            final Process launched=process;
            reader=new FutureTask<>(()-> {
                try(var input=launched.getInputStream()) { return input.readNBytes(1025); }
            });
            Thread.ofVirtual().start(reader);
            if(!process.waitFor(5,TimeUnit.SECONDS)) return new Version("TIMEOUT",null,Instant.now());
            if(process.exitValue()!=0) return new Version("FAILED",null,Instant.now());
            byte[] bytes=reader.get(1,TimeUnit.SECONDS);
            if(bytes.length>1024) return new Version("OUTPUT_UNRECOGNIZED",null,Instant.now());
            var match=VERSION.matcher(new String(bytes,StandardCharsets.US_ASCII).strip());
            return match.matches()?new Version("AVAILABLE",match.group(1),Instant.now()):new Version("OUTPUT_UNRECOGNIZED",null,Instant.now());
        } catch(InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new Version("INTERRUPTED",null,Instant.now());
        } catch(java.util.concurrent.TimeoutException timeout) {
            return new Version("TIMEOUT",null,Instant.now());
        } catch(IOException|java.util.concurrent.ExecutionException|RuntimeException ignored) {
            return new Version("UNAVAILABLE",null,Instant.now());
        } finally {
            if(reader!=null) reader.cancel(true);
            releaseProcess(process);
        }
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
            releaseProcess(process);
        }
    }

    private void releaseProcess(Process process) {
        // Shared permit for syntax and version: never overlap still-alive subprocesses.
        if (process != null && process.isAlive()) {
            try { process.destroyForcibly(); }
            finally {
                if (process.isAlive()) process.onExit().thenRun(permit::release);
                else permit.release();
            }
        } else permit.release();
    }

    private Check result(String status, Integer exit) { return new Check(status,Instant.now(),exit); }
}
