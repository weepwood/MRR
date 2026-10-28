package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.config.ArchiveExportProperties;
import com.zjcxph.imgapi.config.ImageProperties;
import jakarta.annotation.PreDestroy;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 汇总部署前置条件，并在关键依赖异常时提供自动只读降级判定。
 *
 * <p>业务请求只读取最后一次不可变快照。数据库、Nginx 和 OSS 探测由启动检查、
 * 定时任务或运维人员手动刷新执行，避免把外部依赖超时传递到正常写请求。</p>
 */
@Service
public class DeploymentReadinessService {

    private static final Logger logger = LoggerFactory.getLogger(DeploymentReadinessService.class);

    /**
     * 病案影像表的编号查询索引。
     *
     * <p>由 {@code backend-repo/scripts/build-code-key-indexes.sql} 在批准的副本窗口
     * 人工创建。{@code mr_scan} 约三千万行，Flyway 迁移里同步建索引会长时间阻塞写入，
     * 因此故意不纳入自动迁移——代价是「缺索引」在运行时不报任何错，只让按病案号/
     * 上架号查询退化为全表扫描（实测两千万行下约 613 ms，建索引后 0.5 ms）。</p>
     */
    static final List<String> SCAN_CODE_KEY_INDEXES = List.of(
            "idx_mr_scan_bah_numeric_cursor",
            "idx_mr_scan_sjh_numeric_cursor");

    static final String SCAN_CODE_KEY_INDEX_SCRIPT = "scripts/build-code-key-indexes.sql";

    private final JdbcTemplate jdbcTemplate;
    private final Flyway flyway;
    private final ImageProperties imageProperties;
    private final ArchiveExportProperties exportProperties;
    private final ImageUrlService imageUrlService;
    private final OssService ossService;
    private final SystemSettingService systemSettingService;
    private final HttpClient httpClient;
    private final ExecutorService ossHealthExecutor;
    private final long minimumFreeBytes;
    private final long maximumBackupAgeHours;
    private final long ossHealthTimeoutSeconds;
    private final long scanIndexMinimumRows;

    /**
     * 上一次编号索引检查的状态，仅用于「状态变化时打一次日志」。
     * 就绪快照每 30 秒重建一次，无条件打日志会淹没运行日志。
     */
    private volatile String lastScanIndexState;

    private volatile Map<String, Object> cachedSnapshot = Map.of(
            "ready", false,
            "readOnly", true,
            "mode", "READ_ONLY_DEGRADED",
            "checkedAt", Instant.EPOCH.toString(),
            "checks", List.of()
    );

    public DeploymentReadinessService(
            JdbcTemplate jdbcTemplate,
            Flyway flyway,
            ImageProperties imageProperties,
            ArchiveExportProperties exportProperties,
            ImageUrlService imageUrlService,
            OssService ossService,
            SystemSettingService systemSettingService,
            @Value("${app.readiness.minimum-free-bytes:5368709120}") long minimumFreeBytes,
            @Value("${app.readiness.maximum-backup-age-hours:48}") long maximumBackupAgeHours,
            @Value("${app.readiness.oss-health-timeout-seconds:5}") long ossHealthTimeoutSeconds,
            @Value("${app.readiness.scan-index-min-rows:1000000}") long scanIndexMinimumRows
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.flyway = flyway;
        this.imageProperties = imageProperties;
        this.exportProperties = exportProperties;
        this.imageUrlService = imageUrlService;
        this.ossService = ossService;
        this.systemSettingService = systemSettingService;
        this.minimumFreeBytes = Math.max(0L, minimumFreeBytes);
        this.maximumBackupAgeHours = Math.max(1L, maximumBackupAgeHours);
        this.ossHealthTimeoutSeconds = Math.max(1L, ossHealthTimeoutSeconds);
        this.scanIndexMinimumRows = Math.max(0L, scanIndexMinimumRows);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.ossHealthExecutor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "mrr-oss-readiness");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 返回最后一次检查结果，不触发任何数据库或网络 I/O。
     */
    public Map<String, Object> getSnapshot() {
        return cachedSnapshot;
    }

    /**
     * 只读取快照，供写请求拦截器进行常数时间判断。
     */
    public boolean isReadOnly() {
        return Boolean.TRUE.equals(cachedSnapshot.get("readOnly"));
    }

    /**
     * 显式执行一次完整检查。该方法只由启动任务、调度任务和运维刷新接口调用。
     */
    public synchronized Map<String, Object> refreshSnapshot() {
        cachedSnapshot = buildSnapshot();
        return cachedSnapshot;
    }

    @Scheduled(
            fixedDelayString = "${app.readiness.refresh-interval-ms:30000}",
            initialDelayString = "${app.readiness.refresh-initial-delay-ms:30000}"
    )
    public void refreshScheduled() {
        try {
            refreshSnapshot();
        } catch (Exception exception) {
            logger.error("定时刷新部署就绪状态失败", exception);
        }
    }

    @PreDestroy
    public void shutdown() {
        ossHealthExecutor.shutdownNow();
    }

    private Map<String, Object> buildSnapshot() {
        List<Map<String, Object>> checks = new ArrayList<>();
        checks.add(checkDatabase());
        checks.add(checkFlyway());
        checks.add(checkArchiveForeignKeys());
        checks.add(checkScanCodeKeyIndexes());
        checks.add(checkTemporaryDirectory());
        checks.add(checkNginxSources());
        checks.add(checkOss());
        checks.add(checkBackupAge());

        boolean criticalFailure = checks.stream().anyMatch(check ->
                "CRITICAL".equals(check.get("severity")) && !Boolean.TRUE.equals(check.get("passed")));
        boolean ready = checks.stream().allMatch(check -> Boolean.TRUE.equals(check.get("passed"))
                || "WARNING".equals(check.get("severity")));

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("ready", ready && !criticalFailure);
        snapshot.put("readOnly", criticalFailure);
        snapshot.put("mode", criticalFailure ? "READ_ONLY_DEGRADED" : "READ_WRITE");
        snapshot.put("checkedAt", Instant.now().toString());
        snapshot.put("checks", List.copyOf(checks));
        return Map.copyOf(snapshot);
    }

    private Map<String, Object> checkDatabase() {
        try {
            Integer value = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return check("database", "数据库连接", true, "CRITICAL", "数据库连接正常", value);
        } catch (Exception exception) {
            return failed("database", "数据库连接", "CRITICAL", exception);
        }
    }

    private Map<String, Object> checkFlyway() {
        try {
            MigrationInfoService info = flyway.info();
            MigrationInfo[] pending = info.pending();
            long failed = Arrays.stream(info.all())
                    .filter(item -> item.getState().name().startsWith("FAILED"))
                    .count();
            MigrationInfo current = info.current();
            boolean passed = pending.length == 0 && failed == 0;
            String currentVersion = current == null || current.getVersion() == null
                    ? ""
                    : current.getVersion().getVersion();

            return check(
                    "flyway",
                    "数据库迁移",
                    passed,
                    "CRITICAL",
                    passed ? "数据库已应用当前程序要求的全部 Flyway 迁移" : "存在待执行或失败的 Flyway 迁移",
                    Map.of(
                            "pendingMigrations", pending.length,
                            "failedMigrations", failed,
                            "currentVersion", currentVersion
                    )
            );
        } catch (Exception exception) {
            return failed("flyway", "数据库迁移", "CRITICAL", exception);
        }
    }

    private Map<String, Object> checkArchiveForeignKeys() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT conname, convalidated
                    FROM pg_constraint
                    WHERE conname IN (
                        'fk_mr_statistics_archive',
                        'fk_mr_scan_archive',
                        'fk_archive_box_record_archive'
                    )
                    ORDER BY conname
                    """);
            long invalid = rows.stream().filter(row -> !Boolean.TRUE.equals(row.get("convalidated"))).count();
            boolean passed = rows.size() == 3 && invalid == 0;
            return check(
                    "archive-foreign-keys",
                    "病案主数据外键",
                    passed,
                    "WARNING",
                    passed ? "病案关联外键均已验证" : "仍有未建立或未验证的病案关联外键",
                    Map.of("expected", 3, "found", rows.size(), "notValidated", invalid)
            );
        } catch (Exception exception) {
            return failed("archive-foreign-keys", "病案主数据外键", "WARNING", exception);
        }
    }

    /**
     * 检查病案影像表的编号查询索引是否就绪。
     *
     * <p>这些索引不随 Flyway 创建，缺失时系统不会报错，只会让查询退化为全表扫描。
     * 因此这里把它变成可见的告警：只在表规模超过阈值时提示，避免在开发机和小站点
     * 上产生噪音。</p>
     *
     * <p>公开可见是为了让集成测试能在真实数据库上验证目录查询 SQL；业务代码统一
     * 通过 {@link #getSnapshot()} 读取结果，不应单独调用本方法。</p>
     */
    public Map<String, Object> checkScanCodeKeyIndexes() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT c.relname AS index_name, i.indisvalid AS valid
                    FROM pg_index i
                    JOIN pg_class c ON c.oid = i.indexrelid
                    JOIN pg_namespace n ON n.oid = c.relnamespace
                    WHERE n.nspname = 'app'
                      AND c.relname IN ('idx_mr_scan_bah_numeric_cursor', 'idx_mr_scan_sjh_numeric_cursor')
                    ORDER BY c.relname
                    """);

            List<String> valid = new ArrayList<>();
            List<String> invalid = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String name = String.valueOf(row.get("index_name"));
                if (Boolean.TRUE.equals(row.get("valid"))) {
                    valid.add(name);
                } else {
                    invalid.add(name);
                }
            }
            List<String> missing = SCAN_CODE_KEY_INDEXES.stream()
                    .filter(name -> !valid.contains(name) && !invalid.contains(name))
                    .toList();

            long estimatedRows = estimatedScanRows();
            boolean needed = estimatedRows >= scanIndexMinimumRows;
            boolean passed = (missing.isEmpty() && invalid.isEmpty()) || !needed;
            String state = !missing.isEmpty() || !invalid.isEmpty()
                    ? (needed ? "MISSING" : "BELOW_THRESHOLD")
                    : "READY";

            String message;
            if (passed && "READY".equals(state)) {
                message = "病案影像表的编号查询索引已就绪";
            } else if (passed) {
                message = "病案影像表规模较小（约 " + estimatedRows + " 行），暂不需要编号查询索引";
            } else {
                message = "病案影像表约 " + estimatedRows + " 行，但缺少编号查询索引，"
                        + "按病案号/上架号查询会退化为全表扫描";
            }

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("state", state);
            details.put("expected", SCAN_CODE_KEY_INDEXES);
            details.put("present", List.copyOf(valid));
            details.put("missing", List.copyOf(missing));
            details.put("invalid", List.copyOf(invalid));
            details.put("estimatedRows", estimatedRows);
            details.put("minimumRows", scanIndexMinimumRows);
            details.put("script", SCAN_CODE_KEY_INDEX_SCRIPT);

            logScanIndexStateChange(state, missing, invalid, estimatedRows);
            return check("scan-code-key-indexes", "病案影像查询索引", passed, "WARNING", message, details);
        } catch (Exception exception) {
            return failed("scan-code-key-indexes", "病案影像查询索引", "WARNING", exception);
        }
    }

    /**
     * 读取 {@code mr_scan} 的行数估计值。
     *
     * <p>使用 {@code pg_class.reltuples}（统计信息里的估计值）而不是 {@code count(*)}：
     * 这张表约三千万行，精确计数本身就是一次全表扫描，绝不能放进每 30 秒执行一次的就绪检查。</p>
     */
    private long estimatedScanRows() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT COALESCE(c.reltuples, 0)::bigint AS estimated_rows
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'app' AND c.relname = 'mr_scan'
                """);
        if (rows.isEmpty()) {
            return 0L;
        }
        Object value = rows.get(0).get("estimated_rows");
        return value instanceof Number number ? Math.max(0L, number.longValue()) : 0L;
    }

    private void logScanIndexStateChange(String state, List<String> missing, List<String> invalid, long estimatedRows) {
        if (state.equals(lastScanIndexState)) {
            return;
        }
        lastScanIndexState = state;
        if ("MISSING".equals(state)) {
            logger.warn(
                    "病案影像表约 {} 行但缺少编号查询索引（缺失={}，无效={}），"
                            + "按病案号查询将全表扫描；请在批准的窗口执行 {}",
                    estimatedRows, missing, invalid, SCAN_CODE_KEY_INDEX_SCRIPT);
        } else if ("READY".equals(state)) {
            logger.info("病案影像表编号查询索引已就绪");
        }
    }

    private Map<String, Object> checkTemporaryDirectory() {        try {
            Path directory = Path.of(exportProperties.getTempDirectory()).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            FileStore store = Files.getFileStore(directory);
            long usable = store.getUsableSpace();
            boolean writable = Files.isWritable(directory);
            boolean passed = writable && usable >= minimumFreeBytes;
            return check(
                    "temporary-directory",
                    "导出临时目录",
                    passed,
                    "CRITICAL",
                    passed ? "临时目录可写且空间充足" : "临时目录不可写或剩余空间不足",
                    Map.of(
                            "path", directory.toString(),
                            "writable", writable,
                            "usableBytes", usable,
                            "minimumFreeBytes", minimumFreeBytes
                    )
            );
        } catch (Exception exception) {
            return failed("temporary-directory", "导出临时目录", "CRITICAL", exception);
        }
    }

    private Map<String, Object> checkNginxSources() {
        Map<String, String> configured = new LinkedHashMap<>();
        addSource(configured, "default", imageProperties.getServerUrlDefault());
        addSource(configured, "ba01", imageProperties.getServerUrlBa01());
        addSource(configured, "ba02", imageProperties.getServerUrlBa02());
        addSource(configured, "ba03", imageProperties.getServerUrlBa03());
        addSource(configured, "fallback", imageProperties.getUrl());

        boolean localPreferred = "local".equalsIgnoreCase(imageUrlService.getEffectiveImageSource());
        if (configured.isEmpty()) {
            return check(
                    "nginx-images",
                    "Nginx 图片源",
                    !localPreferred,
                    localPreferred ? "CRITICAL" : "WARNING",
                    localPreferred ? "当前使用本地图片，但没有配置 Nginx 图片地址" : "未配置 Nginx 图片地址",
                    Map.of("configured", 0, "reachable", 0, "state", "UNCONFIGURED")
            );
        }

        int reachable = 0;
        List<Map<String, Object>> sources = new ArrayList<>();
        for (Map.Entry<String, String> entry : configured.entrySet()) {
            HttpProbe probe = probeServer(entry.getValue());
            if (probe.reachable()) {
                reachable++;
            }
            sources.add(Map.of(
                    "node", entry.getKey(),
                    "url", entry.getValue(),
                    "reachable", probe.reachable(),
                    "statusCode", probe.statusCode()
            ));
        }

        boolean allReachable = reachable == configured.size();
        boolean noneReachable = reachable == 0;
        String state = allReachable ? "ALL_AVAILABLE" : noneReachable ? "ALL_UNAVAILABLE" : "PARTIAL";
        String severity = localPreferred && noneReachable ? "CRITICAL" : "WARNING";
        boolean passed = allReachable || (!localPreferred && reachable > 0);
        String message = switch (state) {
            case "ALL_AVAILABLE" -> "所有已配置的 Nginx 图片节点均可访问";
            case "PARTIAL" -> "部分 Nginx 图片节点不可访问，对应日期范围的病案可能不可用";
            default -> "所有已配置的 Nginx 图片节点均不可访问";
        };

        return check(
                "nginx-images",
                "Nginx 图片源",
                passed,
                severity,
                message,
                Map.of(
                        "configured", configured.size(),
                        "reachable", reachable,
                        "state", state,
                        "sources", sources
                )
        );
    }

    private Map<String, Object> checkOss() {
        boolean ossPreferred = "oss".equalsIgnoreCase(imageUrlService.getEffectiveImageSource());
        Future<Boolean> future = ossHealthExecutor.submit(
                () -> ossService.browseObjects("medical-records/", null, 1).isConfigured()
        );
        try {
            boolean available = future.get(ossHealthTimeoutSeconds, TimeUnit.SECONDS);
            boolean passed = available || !ossPreferred;
            return check(
                    "oss",
                    "OSS 连接",
                    passed,
                    ossPreferred ? "CRITICAL" : "WARNING",
                    available ? "OSS 客户端可访问" : "OSS 未配置或不可访问",
                    Map.of("preferred", ossPreferred, "available", available)
            );
        } catch (TimeoutException exception) {
            future.cancel(true);
            return check(
                    "oss",
                    "OSS 连接",
                    !ossPreferred,
                    ossPreferred ? "CRITICAL" : "WARNING",
                    "OSS 健康检查超时",
                    Map.of("preferred", ossPreferred, "timeoutSeconds", ossHealthTimeoutSeconds)
            );
        } catch (Exception exception) {
            future.cancel(true);
            return failed("oss", "OSS 连接", ossPreferred ? "CRITICAL" : "WARNING", exception);
        }
    }

    private Map<String, Object> checkBackupAge() {
        try {
            String raw = systemSettingService.getSetting("lastSuccessfulBackupAt");
            if (!StringUtils.hasText(raw)) {
                return check(
                        "backup",
                        "最近备份",
                        false,
                        "WARNING",
                        "尚未记录最近一次成功备份时间",
                        Map.of("maximumAgeHours", maximumBackupAgeHours)
                );
            }
            Instant lastBackup = parseInstant(raw);
            long ageHours = Math.max(0L, Duration.between(lastBackup, Instant.now()).toHours());
            boolean passed = ageHours <= maximumBackupAgeHours;
            return check(
                    "backup",
                    "最近备份",
                    passed,
                    "WARNING",
                    passed ? "最近备份时间在允许范围内" : "最近备份已经超过允许时间",
                    Map.of(
                            "lastSuccessfulBackupAt", lastBackup.toString(),
                            "ageHours", ageHours,
                            "maximumAgeHours", maximumBackupAgeHours
                    )
            );
        } catch (Exception exception) {
            return failed("backup", "最近备份", "WARNING", exception);
        }
    }

    private void addSource(Map<String, String> sources, String node, String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            return;
        }
        String normalized = rawUrl.trim();
        if (sources.containsValue(normalized)) {
            return;
        }
        sources.put(node, normalized);
    }

    private HttpProbe probeServer(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            return new HttpProbe(false, 0);
        }
        try {
            URI uri = URI.create(rawUrl.endsWith("/") ? rawUrl : rawUrl + "/");
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(3))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            int status = httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            return new HttpProbe(isServerReachableStatus(status), status);
        } catch (Exception exception) {
            logger.debug("部署就绪检查无法访问图片源 {}: {}", rawUrl, exception.getMessage());
            return new HttpProbe(false, 0);
        }
    }

    static boolean isServerReachableStatus(int status) {
        return status >= 100 && status < 500;
    }

    private Instant parseInstant(String value) {
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException ignored) {
            return OffsetDateTime.parse(value.trim()).withOffsetSameInstant(ZoneOffset.UTC).toInstant();
        }
    }

    private Map<String, Object> failed(String code, String name, String severity, Exception exception) {
        return check(code, name, false, severity, safeMessage(exception), Map.of());
    }

    private Map<String, Object> check(
            String code,
            String name,
            boolean passed,
            String severity,
            String message,
            Object details
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("name", name);
        result.put("passed", passed);
        result.put("severity", severity);
        result.put("message", message);
        result.put("details", details == null ? Map.of() : details);
        return Map.copyOf(result);
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (!StringUtils.hasText(message)) {
            return exception.getClass().getSimpleName();
        }
        return message.length() <= 300 ? message : message.substring(0, 300);
    }

    private record HttpProbe(boolean reachable, int statusCode) {
    }
}
