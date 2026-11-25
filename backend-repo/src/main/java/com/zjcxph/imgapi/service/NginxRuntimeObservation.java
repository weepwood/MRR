package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;

@Service
public class NginxRuntimeObservation {
    private static final int MAX_BYTES=65536;
    private static final Pattern ERROR_PREFIX=Pattern.compile("^([0-9]{4}/[0-9]{2}/[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}) \\[(emerg|alert|crit|error|warn|notice|info|debug)\\] ");
    private static final DateTimeFormatter ERROR_TIME=DateTimeFormatter.ofPattern("uuuu/MM/dd HH:mm:ss").withResolverStyle(java.time.format.ResolverStyle.STRICT);
    private final boolean enabled,windows;
    private final Path root;
    private final ObjectMapper mapper;
    private final ProcessLookup lookup;
    private final Semaphore permit=new Semaphore(1);
    interface ProcessLookup { String state(long pid,Path executable); }

    @Autowired
    public NginxRuntimeObservation(@Value("${app.operations.nginx.enabled:false}") boolean enabled,
            @Value("${app.operations.nginx.root:}") String configuredRoot,ObjectMapper mapper) {
        this(enabled,System.getProperty("os.name","").toLowerCase(Locale.ROOT).startsWith("windows"),
                NginxConfigurationDiagnostics.localWindowsRoot(configuredRoot),mapper,NginxRuntimeObservation::processState);
    }
    NginxRuntimeObservation(boolean enabled,boolean windows,Path root,ObjectMapper mapper,ProcessLookup lookup) {
        this.enabled=enabled;this.windows=windows;this.root=root;this.mapper=mapper;this.lookup=lookup;
    }
    public record RuntimeStatus(String state,Instant observedAt) {}
    public record Entry(String time,String method,Integer status,Double requestSeconds,String severity) {}
    public record LogTail(String state,String kind,Instant observedAt,boolean truncated,int omitted,List<Entry> entries) {}
    private record Tail(String content,boolean truncated) {}

    private String unavailable() {
        if(!enabled) return "DISABLED";
        if(!windows) return "UNSUPPORTED_PLATFORM";
        if(root==null) return "NOT_CONFIGURED";
        return null;
    }
    private Path fixed(String relative) throws IOException {
        if(!root.toRealPath().equals(root)) throw new IOException("redirected root");
        Path p=root.resolve(relative).normalize();
        if(!p.startsWith(root)||!p.toRealPath().equals(p)||!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("invalid fixed file");
        return p;
    }
    private static String processState(long pid,Path expected) {
        var found=ProcessHandle.of(pid);
        if(found.isEmpty()||!found.get().isAlive()) return "PID_NOT_RUNNING";
        var process=found.get();
        var command=process.info().command();
        if(command.isEmpty()) return "PROCESS_UNVERIFIABLE";
        if(!Path.of(command.get()).normalize().equals(expected)) return "PID_MISMATCH";
        return process.isAlive()?"PROCESS_MATCHED":"PID_NOT_RUNNING";
    }
    public RuntimeStatus runtime() {
        String state=unavailable();
        if(state!=null) return new RuntimeStatus(state,Instant.now());
        try {
            Path executable=fixed("runtime/nginx/nginx.exe");
            Path pidFile=fixed("state/nginx.pid");
            try(var channel=Files.newByteChannel(pidFile,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer bytes=ByteBuffer.allocate(65);
                while(bytes.hasRemaining()&&channel.read(bytes)>0) {}
                if(bytes.position()>64) return new RuntimeStatus("PID_UNVERIFIABLE",Instant.now());
                String pid=new String(bytes.array(),0,bytes.position(),StandardCharsets.US_ASCII).trim();
                if(!pid.matches("[1-9][0-9]{0,9}")) return new RuntimeStatus("PID_UNVERIFIABLE",Instant.now());
                state=lookup.state(Long.parseLong(pid),executable);
            }
        } catch(IOException|RuntimeException ignored) { state="PROCESS_UNVERIFIABLE"; }
        return new RuntimeStatus(state,Instant.now());
    }
    public LogTail logs(String kind,int limit) {
        if(kind==null||!Set.of("access","error").contains(kind)||limit<1||limit>100) throw new BusinessException(400,"无效日志类型或条数");
        String state=unavailable();
        if(state!=null) return empty(state,kind);
        if(!permit.tryAcquire()) return empty("BUSY",kind);
        try {
            Path file=fixed("access".equals(kind)?"logs/nginx/access-audit.jsonl":"logs/nginx/error.log");
            Tail tail=tail(file);
            var lines=tail.content().split("\\r?\\n",-1);
            var entries=new ArrayList<Entry>();
            int omitted=0,examined=0;
            // Ignore partial first/last records and never expose arbitrary text.
            int first=tail.truncated()?1:0;
            for(int i=lines.length-2;i>=first&&examined<limit;i--,examined++) {
                Entry entry="access".equals(kind)?access(lines[i]):error(lines[i]);
                if(entry==null) omitted++; else entries.add(entry);
            }
            return new LogTail("AVAILABLE",kind,Instant.now(),tail.truncated()||lines.length-1-first>limit,omitted,List.copyOf(entries));
        } catch(IOException|RuntimeException ignored) { return empty("LOG_UNAVAILABLE",kind); }
        finally { permit.release(); }
    }
    private LogTail empty(String state,String kind) { return new LogTail(state,kind,Instant.now(),false,0,List.of()); }
    private static Tail tail(Path file) throws IOException {
        try(SeekableByteChannel channel=Files.newByteChannel(file,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)) {
            long size=channel.size(),start=Math.max(0,size-MAX_BYTES);
            channel.position(start);
            ByteBuffer bytes=ByteBuffer.allocate((int)(size-start));
            while(bytes.hasRemaining()&&channel.read(bytes)>0) {}
            return new Tail(new String(bytes.array(),0,bytes.position(),StandardCharsets.UTF_8),start>0);
        }
    }
    private Entry access(String line) {
        if(line.length()>16384) return null;
        try {
            JsonNode n=mapper.readTree(line);
            if(n==null||!n.isObject()||!n.path("status").isIntegralNumber()||!n.path("status").canConvertToInt()||!n.path("request_time").isNumber()
                    ||!n.path("time").isTextual()||!n.path("method").isTextual()) return null;
            int status=n.path("status").asInt();double seconds=n.path("request_time").asDouble();
            if(status<100||status>599||!Double.isFinite(seconds)||seconds<0||seconds>86400) return null;
            String time=OffsetDateTime.parse(n.path("time").asText()).toString();
            String method=n.path("method").asText();
            if(!Set.of("GET","HEAD","POST","PUT","PATCH","DELETE","OPTIONS","CONNECT","TRACE").contains(method)) method="OTHER";
            return new Entry(time,method,status,seconds,null);
        } catch(Exception invalid) { return null; }
    }
    private Entry error(String line) {
        var match=ERROR_PREFIX.matcher(line);
        if(!match.find()) return null;
        try {
            String time=LocalDateTime.parse(match.group(1),ERROR_TIME).toString();
            return new Entry(time,null,null,null,match.group(2));
        } catch(RuntimeException invalid) { return null; }
    }
}
