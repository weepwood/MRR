package com.zjcxph.imgapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.entity.SystemErrorEvent;
import com.zjcxph.imgapi.mapper.SystemErrorEventMapper;
import com.zjcxph.imgapi.utils.RuntimeErrorSanitizer;
import com.zjcxph.imgapi.utils.RuntimeLogContext;
import com.zjcxph.imgapi.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@ConditionalOnProperty(name = "app.runtime-logs.enabled", havingValue = "true", matchIfMissing = false)
public class RuntimeLogJournalService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final SystemErrorEventMapper errors;
    private final Path live;
    private final ObjectMapper json = new ObjectMapper();
    private final AtomicBoolean running = new AtomicBoolean();
    private final Instant startedAt = Instant.now();
    private volatile String failureCode = "";
    private volatile Instant lastSuccess;
    private volatile long invalidLines;
    private volatile boolean unknownGap;
    private final Set<YearMonth> readyPartitions = new HashSet<>();
    private Instant lastCapacitySample = Instant.EPOCH;

    public RuntimeLogJournalService(JdbcTemplate jdbc, PlatformTransactionManager transactions,
                                   SystemErrorEventMapper errors, @Value("${logging.file.name:img-api.log}") String file) {
        this.jdbc = jdbc; this.tx = new TransactionTemplate(transactions); this.errors = errors;
        this.live = Path.of(file).toAbsolutePath().normalize();
    }

    @Scheduled(fixedDelayString = "${app.runtime-logs.poll-ms:2000}", initialDelay = 5000)
    public void ingest() {
        if (!running.compareAndSet(false, true)) return;
        RuntimeLogContext.enter();
        try {
            List<Path> inventory = candidates();
            Set<String> present = new HashSet<>();
            for (Path file : inventory) present.add(sourceKey(file));
            for (Map<String,Object> source : jdbc.queryForList("SELECT source_key FROM app.runtime_log_source WHERE state='PENDING' AND (file_name=? OR file_name LIKE ?) LIMIT 2000", live.getFileName().toString(), live.getFileName()+".%")) {
                if (!present.contains(String.valueOf(source.get("source_key")))) {
                    jdbc.update("UPDATE app.runtime_log_source SET state='HALTED',failure_code='SOURCE_DISAPPEARED',updated_at=CURRENT_TIMESTAMP WHERE source_key=?", source.get("source_key"));
                    unknownGap = true;
                }
            }
            for (Path file : inventory) {
                String key = sourceKey(file);
                if (file.toString().endsWith(".gz") && Boolean.TRUE.equals(jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM app.runtime_log_source WHERE source_key=? AND state='COMPLETE')", Boolean.class, key))) continue;
                boolean active = file.equals(live) || Files.getLastModifiedTime(file).toInstant().isAfter(startedAt) || isPrivateJournal(file);
                if (!active && !Boolean.TRUE.equals(jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM app.runtime_log_source WHERE source_key=? AND state<>'HALTED')", Boolean.class, key))) continue;
                if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM app.runtime_log_source WHERE source_key=? AND state='HALTED')", Boolean.class, key))) continue;
                boolean progressed;
                try { progressed = readAndCommit(file, key); }
                catch (java.io.IOException e) {
                    unknownGap = true;
                    jdbc.update("UPDATE app.runtime_log_source SET state='HALTED',failure_code='SOURCE_READ_FAILED' WHERE source_key=?", key);
                    continue;
                }
                // 每轮仅消费一个有积压的批次，避免后台占满数据库连接。
                if (progressed) break;
            }
            if (Duration.between(lastCapacitySample, Instant.now()).toSeconds() >= 3600) {
                long bytes = candidates().stream().mapToLong(RuntimeLogJournalService::safeSize).sum();
                jdbc.update("INSERT INTO app.runtime_log_capacity_sample(measured_at,database_bytes,buffer_bytes,disk_free_bytes,possible_gap) VALUES (CURRENT_TIMESTAMP,?,?,?,?)",
                        databaseBytes(), bytes, Files.getFileStore(live.getParent()).getUsableSpace(),
                        unknownGap || com.zjcxph.imgapi.config.ObservableAsyncLogAppender.SATURATION.get() > 0);
                lastCapacitySample = Instant.now();
            }
            failureCode = "";
        } catch (Exception e) { failureCode = "JOURNAL_INGEST_UNAVAILABLE"; }
        finally { RuntimeLogContext.leave(); running.set(false); }
    }

    private boolean isPrivateJournal(Path file) {
        try {
            var first = RuntimeLogJournalReader.read(file, 0, 1).lines();
            return !first.isEmpty() && json.readTree(first.getFirst().text()).path("privacy_version").asInt() == 1;
        } catch (Exception e) { return false; }
    }

    private boolean readAndCommit(Path file, String key) throws Exception {
        jdbc.update("INSERT INTO app.runtime_log_source(source_key,file_name) VALUES (?,?) ON CONFLICT DO NOTHING", key, file.getFileName().toString());
        Long value = jdbc.queryForObject("SELECT byte_offset FROM app.runtime_log_source WHERE source_key=?", Long.class, key);
        long offset = value == null ? 0 : value;
        if (!file.toString().endsWith(".gz") && Files.size(file) < offset) { offset = 0; unknownGap = true; }
        var batch = RuntimeLogJournalReader.read(file, offset, 250);
        long rejectedBefore = invalidLines;
        var rows = new ArrayList<Object[]>();
        for (var line : batch.lines()) {
            try {
                JsonNode node = json.readTree(line.text());
                String level = node.path("level").asText();
                if (!Set.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR").contains(level)) throw new IllegalArgumentException();
                Instant at = Instant.parse(node.path("@timestamp").asText());
                if (at.isBefore(Instant.parse("2000-01-01T00:00:00Z")) || at.isAfter(Instant.now().plusSeconds(86400))) throw new IllegalArgumentException();
                UUID id = node.hasNonNull("event_id") ? UUID.fromString(node.get("event_id").asText())
                        : UUID.nameUUIDFromBytes((key + ":" + line.offset()).getBytes(StandardCharsets.UTF_8));
                boolean privateFormat = node.path("privacy_version").asInt() == 1;
                String message = privateFormat ? com.zjcxph.imgapi.config.PrivateJsonLogEncoder.safeMessage(node.path("message").asText()) : "[LEGACY MESSAGE OMITTED]";
                String stack = privateFormat ? com.zjcxph.imgapi.config.PrivateJsonLogEncoder.safeStoredStack(node.path("stack_trace").asText()) : "";
                rows.add(new Object[]{Timestamp.from(at), id, level, safe(node.path("logger_name").asText(), 255),
                        safe(node.path("requestId").asText(), 80), nullableSafe(node.path("errorId").asText(), 32), message, stack,
                        key, line.offset(), !privateFormat || !Set.of("WARN", "ERROR").contains(level)});
            } catch (Exception e) { invalidLines++; unknownGap = true; }
        }
        // 数据库故障不得被当作坏日志处理并推进断点。
        for (Object[] row : rows) ensurePartition(((Timestamp) row[0]).toInstant());
        long rejected = invalidLines - rejectedBefore + batch.invalidLines();
        tx.executeWithoutResult(status -> {
            jdbc.execute("SET LOCAL statement_timeout='3s'");
            jdbc.batchUpdate("INSERT INTO app.runtime_log_event(occurred_at,event_id,level,logger_name,request_id,error_id,message_summary,stack_trace,source_key,source_offset,projected) "
                    + "VALUES (?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING", rows);
            for (Map<String, Object> row : jdbc.queryForList("SELECT * FROM app.runtime_log_event WHERE source_key=? AND NOT projected LIMIT 250", key)) project(row);
            jdbc.update("UPDATE app.runtime_log_source SET byte_offset=?,observed_bytes=?,state=?,invalid_lines=invalid_lines+?,failure_code=NULL,updated_at=CURRENT_TIMESTAMP WHERE source_key=?",
                    batch.nextOffset(), safeSize(file), batch.complete() ? "COMPLETE" : "PENDING", rejected, key);
        });
        invalidLines += batch.invalidLines();
        if (batch.invalidLines() > 0) unknownGap = true;
        lastSuccess = Instant.now();
        return batch.nextOffset() > offset;
    }

    private void project(Map<String, Object> row) {
        SystemErrorEvent event = new SystemErrorEvent();
        String logger = String.valueOf(row.get("logger_name"));
        String message = String.valueOf(row.get("message_summary"));
        event.setErrorId(row.get("error_id") == null || String.valueOf(row.get("error_id")).isBlank() ? RuntimeErrorSanitizer.newErrorId() : String.valueOf(row.get("error_id")));
        event.setProjectionEventId(String.valueOf(row.get("event_id")));
        event.setLevel(String.valueOf(row.get("level"))); event.setModule("runtime"); event.setLoggerName(logger);
        event.setMessageSummary(message); event.setStackTrace(String.valueOf(row.get("stack_trace")));
        event.setRequestId((String) row.get("request_id")); event.setThreadName("journal");
        LocalDateTime at = LocalDateTime.ofInstant(((Timestamp) row.get("occurred_at")).toInstant(), ZoneId.systemDefault());
        event.setFirstSeenAt(at); event.setLastSeenAt(at);
        String exceptionType = String.valueOf(row.get("stack_trace")).lines().findFirst().orElse(null);
        event.setExceptionType(exceptionType);
        event.setFingerprint(RuntimeErrorSanitizer.fingerprint(event.getLevel(), logger, exceptionType, message));
        errors.upsert(event);
        jdbc.update("UPDATE app.runtime_log_event SET projected=true,error_id=? WHERE occurred_at=? AND event_id=?", event.getErrorId(), row.get("occurred_at"), row.get("event_id"));
    }

    private void ensurePartition(Instant at) {
        YearMonth month = YearMonth.from(at.atZone(ZoneOffset.UTC));
        if (readyPartitions.contains(month)) return;
        String name = "runtime_log_event_" + month.toString().replace("-", "");
        tx.executeWithoutResult(status -> {
            jdbc.execute("SET LOCAL lock_timeout='1s'");
            jdbc.execute("SET LOCAL statement_timeout='3s'");
            jdbc.execute("CREATE TABLE IF NOT EXISTS app." + name + " PARTITION OF app.runtime_log_event FOR VALUES FROM ('"
                    + month.atDay(1) + " 00:00:00+00') TO ('" + month.plusMonths(1).atDay(1) + " 00:00:00+00')");
        });
        readyPartitions.add(month);
    }

    public List<Map<String, Object>> files() {
        try { return candidates().stream().map(file -> Map.<String, Object>of("fileName", file.getFileName().toString(), "bytes", safeSize(file))).toList(); }
        catch (Exception e) { throw new BusinessException(503, "日志目录暂不可用"); }
    }

    public void approveImport(String name) {
        if (name == null || name.contains("/") || name.contains("\\")) throw new BusinessException(400, "请选择日志清单中的文件");
        try {
            Path file = candidates().stream().filter(path -> path.getFileName().toString().equals(name)).findFirst()
                    .orElseThrow(() -> new BusinessException(400, "日志文件不在允许目录中"));
            if (Files.size(file) > RuntimeLogJournalReader.MAX_BYTES) throw new BusinessException(400, "日志文件超过补录限额");
            jdbc.update("INSERT INTO app.runtime_log_source(source_key,file_name) VALUES (?,?) ON CONFLICT(source_key) DO UPDATE SET state='PENDING',failure_code=NULL",
                    sourceKey(file), name);
        } catch (java.io.IOException e) { throw new BusinessException(503, "日志文件暂不可用"); }
    }

    public Map<String, Object> health() {
        var result = new LinkedHashMap<String, Object>();
        result.put("failureCode", failureCode); result.put("lastSuccess", lastSuccess); result.put("running", running.get());
        result.put("invalidLines", invalidLines); result.put("unknownGap", unknownGap || com.zjcxph.imgapi.config.ObservableAsyncLogAppender.SATURATION.get() > 0);
        result.put("saturationObservations", com.zjcxph.imgapi.config.ObservableAsyncLogAppender.SATURATION.get());
        result.put("suppressedCollectorLogs", RuntimeLogContext.SUPPRESSED.get());
        result.put("auditHmacConfigured", com.zjcxph.imgapi.utils.AuditPayloadSanitizer.hmacConfigured());
        result.put("backlogNote", "pollAgeSeconds仅表示轮询新鲜度；积压看pending_files/字节及最早观察时间，压缩文件不能用压缩字节减解压偏移");
        result.put("completeness", "有限磁盘；缺口计数可能仅为可观察下界，未发现缺口不等于完整性证明");
        result.put("pollAgeSeconds", lastSuccess == null ? null : Math.max(0, Duration.between(lastSuccess, Instant.now()).toSeconds()));
        try {
            result.put("sources", jdbc.queryForList("SELECT file_name,byte_offset,observed_bytes,state,failure_code,invalid_lines,updated_at FROM app.runtime_log_source ORDER BY updated_at DESC LIMIT 50"));
            result.put("invalidLines", jdbc.queryForObject("SELECT COALESCE(SUM(invalid_lines),0) FROM app.runtime_log_source", Long.class));
            result.put("unknownGap", Boolean.TRUE.equals(result.get("unknownGap")) || Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM app.runtime_log_source WHERE invalid_lines>0 OR state='HALTED') OR EXISTS(SELECT 1 FROM app.runtime_log_capacity_sample WHERE possible_gap)", Boolean.class)));
            result.put("backlog", jdbc.queryForMap("SELECT COUNT(*) FILTER(WHERE state='PENDING') AS pending_files, COALESCE(SUM(GREATEST(observed_bytes-byte_offset,0)) FILTER(WHERE state='PENDING' AND file_name NOT LIKE '%.gz'),0) AS pending_raw_bytes, COUNT(*) FILTER(WHERE state='PENDING' AND file_name LIKE '%.gz') AS pending_compressed_files, EXTRACT(EPOCH FROM CURRENT_TIMESTAMP-MIN(updated_at) FILTER(WHERE state='PENDING')) AS oldest_observed_age_seconds FROM app.runtime_log_source"));
            result.put("databaseBytes", databaseBytes());
            long free = Files.getFileStore(live.getParent()).getUsableSpace();
            result.put("bufferDiskFreeBytes", free);
            result.put("databaseDiskFreeBytes", "UNKNOWN");
            result.put("capacityPredictionScope", "磁盘剩余空间仅对应本地缓冲卷；数据库卷剩余空间需要Windows现场采集");
            var capacity = jdbc.queryForList("SELECT measured_at,database_bytes,buffer_bytes FROM app.runtime_log_capacity_sample ORDER BY measured_at DESC LIMIT 2");
            if (capacity.size() == 2) {
                long growth = ((Number)capacity.get(0).get("database_bytes")).longValue() - ((Number)capacity.get(1).get("database_bytes")).longValue();
                long seconds = (((Timestamp)capacity.get(0).get("measured_at")).getTime() - ((Timestamp)capacity.get(1).get("measured_at")).getTime()) / 1000;
                if (growth > 0 && seconds > 0) result.put("databaseGrowthBytesPerDay", (double)growth * 86400 / seconds);
                long bufferGrowth = ((Number)capacity.get(0).get("buffer_bytes")).longValue() - ((Number)capacity.get(1).get("buffer_bytes")).longValue();
                if (bufferGrowth > 0 && seconds > 0) result.put("estimatedBufferHoursUntilDiskFull", (double)free / bufferGrowth * seconds / 3600);
            }
        } catch (Exception e) { result.put("status", "UNAVAILABLE"); }
        return result;
    }

    private List<Path> candidates() throws java.io.IOException {
        Path directory = live.getParent();
        if (!Files.isDirectory(directory)) return List.of();
        String base = java.util.regex.Pattern.quote(live.getFileName().toString());
        try (var stream = Files.list(directory)) {
            return stream.filter(path -> !Files.isSymbolicLink(path) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().matches(base + "(?:\\.\\d{4}-\\d{2}-\\d{2}\\.\\d+\\.gz)?"))
                    .sorted(Comparator.comparing((Path path) -> path.equals(live)).thenComparing(Path::toString)).limit(2000).toList();
        }
    }
    private String sourceKey(Path file) throws java.io.IOException {
        BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((file.toRealPath() + ":" + attributes.creationTime() + ":" + attributes.fileKey()).getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static long safeSize(Path file) { try { return Files.size(file); } catch (Exception e) { return 0; } }
    private static String nullableSafe(String value, int length) { String result = safe(value, length); return result.isBlank() ? null : result; }
    private static String safe(String value, int length) { return value != null && value.length() <= length && value.matches("[A-Za-z0-9_.$:-]*") ? value : ""; }
    private long databaseBytes() { Long value = jdbc.queryForObject("SELECT pg_total_relation_size('app.runtime_error_projection_receipt') + pg_total_relation_size('app.runtime_log_event') + COALESCE(SUM(pg_total_relation_size(inhrelid)),0) FROM pg_inherits WHERE inhparent='app.runtime_log_event'::regclass", Long.class); return value == null ? 0 : value; }
}
