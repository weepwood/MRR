package com.zjcxph.imgapi.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class WindowsGatewayObservation {
    private static final Set<String> SERVICES=Set.of("RUNNING","STOPPED","PAUSED","START_PENDING","STOP_PENDING",
            "CONTINUE_PENDING","PAUSE_PENDING","NOT_FOUND","UNAVAILABLE");
    private final boolean enabled,windows;
    private final Path root,systemRoot;
    private final Supplier<List<NginxRuntimeObservation.ProcessIdentity>> owners;
    private final Launcher launcher;
    private final Semaphore permit=new Semaphore(1);
    interface Launcher {Process start(List<String> args,Path directory) throws IOException;}
    public record Observation(String state,String serviceState,String listenerState,List<Integer> ports,Instant observedAt) {}

    @Autowired
    public WindowsGatewayObservation(@Value("${app.operations.nginx.enabled:false}") boolean enabled,
            @Value("${app.operations.nginx.root:}") String root,NginxRuntimeObservation runtime) {
        this(enabled,System.getProperty("os.name","").toLowerCase(Locale.ROOT).startsWith("windows"),
                NginxConfigurationDiagnostics.localWindowsRoot(root),
                NginxConfigurationDiagnostics.localWindowsRoot(System.getenv("SystemRoot")),runtime::ownedProcesses,
                (args,directory)->new ProcessBuilder(args).directory(directory.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD).start());
    }

    WindowsGatewayObservation(boolean enabled,boolean windows,Path root,Path systemRoot,
            Supplier<List<NginxRuntimeObservation.ProcessIdentity>> owners,Launcher launcher) {
        this.enabled=enabled;this.windows=windows;this.root=root;this.systemRoot=systemRoot;this.owners=owners;this.launcher=launcher;
    }

    public Observation observe() {
        if(!enabled) return empty("DISABLED");
        if(!windows) return empty("UNSUPPORTED_PLATFORM");
        if(root==null||systemRoot==null) return empty("NOT_CONFIGURED");
        if(!permit.tryAcquire()) return empty("BUSY");
        Process process=null;
        FutureTask<byte[]> reader=null;
        try {
            if(!root.toRealPath().equals(root)||!systemRoot.toRealPath().equals(systemRoot)) return empty("UNAVAILABLE");
            Path powershell=systemRoot.resolve("System32/WindowsPowerShell/v1.0/powershell.exe");
            if(!powershell.toRealPath().equals(powershell)||!Files.isRegularFile(powershell,LinkOption.NOFOLLOW_LINKS)) return empty("UNAVAILABLE");
            var before=List.copyOf(owners.get());
            if(before.size()>17||before.stream().anyMatch(p->p.pid()<=0||p.pid()>4294967295L||p.startedAt()==null)
                    ||before.stream().map(NginxRuntimeObservation.ProcessIdentity::pid).distinct().count()!=before.size()) before=List.of();
            String encoded=Base64.getEncoder().encodeToString(script(before).getBytes(StandardCharsets.UTF_16LE));
            process=launcher.start(List.of(powershell.toString(),"-NoLogo","-NoProfile","-NonInteractive","-EncodedCommand",encoded),systemRoot);
            process.getOutputStream().close();
            var launched=process;
            reader=new FutureTask<>(()-> {try(var input=launched.getInputStream()) {return input.readNBytes(2049);}});
            Thread.ofVirtual().start(reader);
            if(!process.waitFor(8,TimeUnit.SECONDS)) return empty("TIMEOUT");
            if(process.exitValue()!=0) return empty("UNAVAILABLE");
            byte[] bytes=reader.get(1,TimeUnit.SECONDS);
            if(bytes.length>2048) return empty("UNAVAILABLE");
            var result=parse(new String(bytes,StandardCharsets.US_ASCII));
            if(!"OBSERVED".equals(result.state())) return result;
            if(!before.isEmpty()&&!before.equals(owners.get())) return new Observation(result.state(),result.serviceState(),"IDENTITY_CHANGED",List.of(),Instant.now());
            if(before.isEmpty()&&!"UNVERIFIED".equals(result.listenerState()))
                return new Observation(result.state(),result.serviceState(),"UNVERIFIED",List.of(),Instant.now());
            return result;
        } catch(InterruptedException interrupted) {
            Thread.currentThread().interrupt();return empty("INTERRUPTED");
        } catch(java.util.concurrent.TimeoutException timeout) {
            return empty("TIMEOUT");
        } catch(IOException|java.util.concurrent.ExecutionException|RuntimeException ignored) {
            return empty("UNAVAILABLE");
        } finally {
            if(reader!=null) reader.cancel(true);
            if(process!=null&&process.isAlive()) {
                try {process.destroyForcibly();}
                finally {if(process.isAlive()) process.onExit().thenRun(permit::release);else permit.release();}
            } else permit.release();
        }
    }

    // No HTTP inputs. Only bounded numeric PIDs from verified local process identities are interpolated.
    static String script(List<NginxRuntimeObservation.ProcessIdentity> owners) {
        String filter=owners.stream().map(p->"OwningProcess="+p.pid()).collect(Collectors.joining(" OR "));
        return """
                $ErrorActionPreference='Stop'
                $ProgressPreference='SilentlyContinue'
                try {
                  $s=@(Get-CimInstance -ClassName Win32_Service -Filter "Name='MRR-Gateway'")
                  if($s.Count -eq 0){'SERVICE=NOT_FOUND'}
                  elseif($s.Count -eq 1){'SERVICE='+($s[0].State.ToUpperInvariant().Replace(' ','_'))}
                  else{'SERVICE=UNAVAILABLE'}
                } catch {'SERVICE=UNAVAILABLE'}
                """+(owners.isEmpty()?"'TCP=UNVERIFIED'\n":"""
                try {
                  $ports=@(Get-CimInstance -Namespace root/StandardCimv2 -ClassName MSFT_NetTCPConnection -Filter "State=2 AND (%s)" |
                    Select-Object -ExpandProperty LocalPort | Sort-Object -Unique | Select-Object -First 33)
                  if($ports.Count -gt 32){'TCP=TRUNCATED'}
                  else{'TCP=OBSERVED:'+($ports -join ',')}
                } catch {'TCP=UNAVAILABLE'}
                """.formatted(filter));
    }

    static Observation parse(String output) {
        String[] lines=output.strip().split("\\R",-1);
        if(lines.length!=2||!lines[0].startsWith("SERVICE=")||!lines[1].startsWith("TCP=")) return empty("UNAVAILABLE");
        String service=lines[0].substring(8),tcp=lines[1].substring(4);
        if(!SERVICES.contains(service)) return empty("UNAVAILABLE");
        if(Set.of("UNVERIFIED","UNAVAILABLE","TRUNCATED").contains(tcp)) return new Observation("OBSERVED",service,tcp,List.of(),Instant.now());
        if(!tcp.startsWith("OBSERVED:")) return empty("UNAVAILABLE");
        String values=tcp.substring(9);
        var ports=new TreeSet<Integer>();
        if(!values.isEmpty()) for(String value:values.split(",",-1)) {
            if(!value.matches("[0-9]{1,5}")) return empty("UNAVAILABLE");
            int port=Integer.parseInt(value);
            if(port<1||port>65535||!ports.add(port)||ports.size()>32) return empty("UNAVAILABLE");
        }
        return new Observation("OBSERVED",service,"OBSERVED",List.copyOf(ports),Instant.now());
    }
    private static Observation empty(String state) {return new Observation(state,"UNAVAILABLE","UNAVAILABLE",List.of(),Instant.now());}
}
