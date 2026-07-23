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
            @Value("${app.readiness.oss-health-timeout-seconds:5}") long ossHealthTimeoutSeconds
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

    private Map<String, Object> checkTemporaryDirectory() {
        try {
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
            boolean reachable = (status >= 200 && status < 400) || status == 401 || status == 403;
            return new HttpProbe(reachable, status);
        } catch (Exception exception) {
            logger.debug("部署就绪检查无法访问图片源 {}: {}", rawUrl, exception.getMessage());
            return new HttpProbe(false, 0);
        }
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
