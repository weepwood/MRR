package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.config.ArchiveExportProperties;
import com.zjcxph.imgapi.config.ImageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 汇总部署前置条件，并在关键依赖异常时提供自动只读降级判定。
 */
@Service
public class DeploymentReadinessService {

    private static final Logger logger = LoggerFactory.getLogger(DeploymentReadinessService.class);
    private static final long CACHE_TTL_NANOS = TimeUnit.SECONDS.toNanos(30);

    private final JdbcTemplate jdbcTemplate;
    private final ImageProperties imageProperties;
    private final ArchiveExportProperties exportProperties;
    private final ImageUrlService imageUrlService;
    private final OssService ossService;
    private final SystemSettingService systemSettingService;
    private final HttpClient httpClient;
    private final long minimumFreeBytes;
    private final long maximumBackupAgeHours;

    private volatile Map<String, Object> cachedSnapshot = Map.of(
            "ready", false,
            "readOnly", true,
            "checkedAt", Instant.EPOCH.toString(),
            "checks", List.of()
    );
    private volatile long cacheExpiresAtNanos;

    public DeploymentReadinessService(
            JdbcTemplate jdbcTemplate,
            ImageProperties imageProperties,
            ArchiveExportProperties exportProperties,
            ImageUrlService imageUrlService,
            OssService ossService,
            SystemSettingService systemSettingService,
            @Value("${app.readiness.minimum-free-bytes:5368709120}") long minimumFreeBytes,
            @Value("${app.readiness.maximum-backup-age-hours:48}") long maximumBackupAgeHours
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.imageProperties = imageProperties;
        this.exportProperties = exportProperties;
        this.imageUrlService = imageUrlService;
        this.ossService = ossService;
        this.systemSettingService = systemSettingService;
        this.minimumFreeBytes = Math.max(0L, minimumFreeBytes);
        this.maximumBackupAgeHours = Math.max(1L, maximumBackupAgeHours);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public Map<String, Object> getSnapshot() {
        long now = System.nanoTime();
        if (now < cacheExpiresAtNanos) {
            return cachedSnapshot;
        }
        synchronized (this) {
            now = System.nanoTime();
            if (now < cacheExpiresAtNanos) {
                return cachedSnapshot;
            }
            cachedSnapshot = buildSnapshot();
            cacheExpiresAtNanos = now + CACHE_TTL_NANOS;
            return cachedSnapshot;
        }
    }

    public boolean isReadOnly() {
        return Boolean.TRUE.equals(getSnapshot().get("readOnly"));
    }

    public void invalidate() {
        cacheExpiresAtNanos = 0L;
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
        snapshot.put("checks", checks);
        return snapshot;
    }

    private Map<String, Object> checkDatabase() {
        try {
            Integer value = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return check("database", "数据库连接", true, "CRITICAL", "数据库连接正常", value);
        } catch (Exception exception) {
            return failed("database", "数据库连接", "CRITICAL", safeMessage(exception));
        }
    }

    private Map<String, Object> checkFlyway() {
        try {
            Integer failed = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM app.flyway_schema_history WHERE success = FALSE",
                    Integer.class
            );
            String latest = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(MAX(version), '') FROM app.flyway_schema_history WHERE success = TRUE",
                    String.class
            );
            boolean passed = failed != null && failed == 0;
            return check(
                    "flyway",
                    "数据库迁移",
                    passed,
                    "CRITICAL",
                    passed ? "Flyway 迁移记录正常" : "存在失败的 Flyway 迁移",
                    Map.of("failedMigrations", failed == null ? -1 : failed, "latestVersion", latest == null ? "" : latest)
            );
        } catch (Exception exception) {
            return failed("flyway", "数据库迁移", "CRITICAL", safeMessage(exception));
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
            return failed("archive-foreign-keys", "病案主数据外键", "WARNING", safeMessage(exception));
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
            return failed("temporary-directory", "导出临时目录", "CRITICAL", safeMessage(exception));
        }
    }

    private Map<String, Object> checkNginxSources() {
        List<String> configured = List.of(
                imageProperties.getServerUrlDefault(),
                imageProperties.getServerUrlBa01(),
                imageProperties.getServerUrlBa02(),
                imageProperties.getServerUrlBa03()
        ).stream().filter(StringUtils::hasText).distinct().toList();

        if (configured.isEmpty()) {
            boolean localPreferred = "local".equalsIgnoreCase(imageUrlService.getEffectiveImageSource());
            return check(
                    "nginx-images",
                    "Nginx 图片源",
                    !localPreferred,
                    localPreferred ? "CRITICAL" : "WARNING",
                    localPreferred ? "当前使用本地图片，但没有配置 Nginx 图片地址" : "未配置 Nginx 图片地址",
                    Map.of("configured", 0, "reachable", 0)
            );
        }

        int reachable = 0;
        List<Map<String, Object>> sources = new ArrayList<>();
        for (String url : configured) {
            boolean available = probe(url);
            if (available) {
                reachable++;
            }
            sources.add(Map.of("url", url, "reachable", available));
        }
        boolean localPreferred = "local".equalsIgnoreCase(imageUrlService.getEffectiveImageSource());
        boolean passed = reachable > 0 || !localPreferred;
        return check(
                "nginx-images",
                "Nginx 图片源",
                passed,
                localPreferred ? "CRITICAL" : "WARNING",
                passed ? "至少一个 Nginx 图片源可访问" : "所有 Nginx 图片源均不可访问",
                Map.of("configured", configured.size(), "reachable", reachable, "sources", sources)
        );
    }

    private Map<String, Object> checkOss() {
        boolean ossPreferred = "oss".equalsIgnoreCase(imageUrlService.getEffectiveImageSource());
        try {
            boolean configured = ossService.browseObjects("medical-records/", null, 1).isConfigured();
            boolean passed = configured || !ossPreferred;
            return check(
                    "oss",
                    "OSS 连接",
                    passed,
                    ossPreferred ? "CRITICAL" : "WARNING",
                    configured ? "OSS 客户端可访问" : "OSS 未配置或不可访问",
                    Map.of("preferred", ossPreferred, "configured", configured)
            );
        } catch (Exception exception) {
            return failed("oss", "OSS 连接", ossPreferred ? "CRITICAL" : "WARNING", safeMessage(exception));
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
                    Map.of("lastSuccessfulBackupAt", lastBackup.toString(), "ageHours", ageHours,
                            "maximumAgeHours", maximumBackupAgeHours)
            );
        } catch (Exception exception) {
            return failed("backup", "最近备份", "WARNING", safeMessage(exception));
        }
    }

    private boolean probe(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl.endsWith("/") ? rawUrl : rawUrl + "/");
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(5))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            int status = httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            return status >= 200 && status < 500;
        } catch (Exception exception) {
            logger.debug("部署就绪检查无法访问图片源 {}: {}", rawUrl, exception.getMessage());
            return false;
        }
    }

    private Instant parseInstant(String value) {
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException ignored) {
            return OffsetDateTime.parse(value.trim()).withOffsetSameInstant(ZoneOffset.UTC).toInstant();
        }
    }

    private Map<String, Object> failed(String code, String name, String severity, String message) {
        return check(code, name, false, severity, message, Map.of());
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
        result.put("details", details);
        return result;
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (!StringUtils.hasText(message)) {
            return exception.getClass().getSimpleName();
        }
        return message.length() <= 300 ? message : message.substring(0, 300);
    }
}
