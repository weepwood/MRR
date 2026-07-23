package com.zjcxph.imgapi.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运维诊断中心第一阶段聚合服务。
 *
 * <p>负责把已有的就绪检查、完整性快照、后台导出任务和访问审计组织成
 * “发现问题、给出建议、跳转处理、重新验证”的运维视图。</p>
 */
@Service
public class OperationsCenterService {

    private static final int MAX_AUDIT_LIMIT = 200;
    private static final int EXPORT_BACKLOG_WARNING = 20;
    private static final int RECENT_ERROR_WARNING = 10;

    private final JdbcTemplate jdbcTemplate;
    private final DeploymentReadinessService readinessService;
    private final MaintenanceModeService maintenanceModeService;
    private final IntegrityDiagnosticsService integrityDiagnosticsService;
    private final OperationsDiagnosticsService diagnosticsService;

    public OperationsCenterService(
            JdbcTemplate jdbcTemplate,
            DeploymentReadinessService readinessService,
            MaintenanceModeService maintenanceModeService,
            IntegrityDiagnosticsService integrityDiagnosticsService,
            OperationsDiagnosticsService diagnosticsService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.readinessService = readinessService;
        this.maintenanceModeService = maintenanceModeService;
        this.integrityDiagnosticsService = integrityDiagnosticsService;
        this.diagnosticsService = diagnosticsService;
    }

    public Map<String, Object> overview() {
        Map<String, Object> readiness = effectiveReadiness(readinessService.getSnapshot());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generatedAt", Instant.now().toString());
        result.put("readiness", readiness);
        result.put("maintenance", maintenanceModeService.getStatus());
        result.put("runtime", runtimeInfo());
        result.put("taskSummary", taskSummary());
        result.put("recentServerErrors", recentServerErrorCount());
        result.put("latestOperation", latestOperation());
        result.put("quickLinks", quickLinks());
        return Map.copyOf(result);
    }

    public Map<String, Object> readiness() {
        return effectiveReadiness(readinessService.getSnapshot());
    }

    public Map<String, Object> refreshReadiness() {
        return effectiveReadiness(readinessService.refreshSnapshot());
    }

    public Map<String, Object> runFullDiagnostics() {
        Instant startedAt = Instant.now();
        Map<String, Object> readiness = refreshReadiness();
        List<Map<String, Object>> checks = new ArrayList<>();
        for (Map<String, Object> item : castChecks(readiness.get("checks"))) {
            checks.add(enrichReadinessCheck(item));
        }
        checks.add(runtimeCheck());
        checks.add(exportBacklogCheck());
        checks.add(recentErrorsCheck());
        checks.add(maintenanceCheck());

        long failed = checks.stream().filter(item -> !Boolean.TRUE.equals(item.get("passed"))).count();
        long critical = checks.stream().filter(item -> !Boolean.TRUE.equals(item.get("passed"))
                && "CRITICAL".equals(item.get("severity"))).count();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", checks.size());
        summary.put("passed", checks.size() - failed);
        summary.put("failed", failed);
        summary.put("critical", critical);
        summary.put("warnings", Math.max(0L, failed - critical));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("startedAt", startedAt.toString());
        result.put("completedAt", Instant.now().toString());
        result.put("summary", Map.copyOf(summary));
        result.put("mode", readiness.getOrDefault("mode", "READ_ONLY_DEGRADED"));
        result.put("readOnly", readiness.getOrDefault("readOnly", true));
        result.put("checks", List.copyOf(checks));
        return Map.copyOf(result);
    }

    public Map<String, Object> diagnosticReport() {
        Map<String, Object> permissionMatrix = diagnosticsService.permissionMatrix(false);
        List<?> endpoints = permissionMatrix.get("endpoints") instanceof List<?> list ? list : List.of();
        List<?> roles = permissionMatrix.get("roles") instanceof List<?> list ? list : List.of();

        Map<String, Object> permissionSummary = new LinkedHashMap<>();
        permissionSummary.put("generatedAt", permissionMatrix.getOrDefault("generatedAt", ""));
        permissionSummary.put("endpointCount", endpoints.size());
        permissionSummary.put("roleCount", roles.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reportVersion", 1);
        result.put("generatedAt", Instant.now().toString());
        result.put("overview", overview());
        result.put("diagnostics", runFullDiagnostics());
        result.put("integrity", integrityDiagnosticsService.getSnapshot());
        result.put("permissionSummary", Map.copyOf(permissionSummary));
        result.put("recentOperations", recentOperations(50));
        result.put("environment", environmentInfo());
        result.put("privacy", Map.of(
                "patientDataIncluded", false,
                "credentialsIncluded", false,
                "description", "诊断报告不包含患者图片、身份证号、密码、令牌或完整密钥"
        ));
        return Map.copyOf(result);
    }

    public List<Map<String, Object>> recentOperations(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_AUDIT_LIMIT));
        try {
            return jdbcTemplate.queryForList("""
                    SELECT id, request_id, username, client_ip, request_uri, method,
                           response_status, execute_time, access_time, error_message
                    FROM app.access_log
                    WHERE request_uri LIKE '/api/v1/operations/%'
                      AND (method <> 'GET' OR response_status LIKE '4%' OR response_status LIKE '5%')
                    ORDER BY access_time DESC, id DESC
                    LIMIT ?
                    """, safeLimit);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private Map<String, Object> effectiveReadiness(Map<String, Object> base) {
        boolean automaticReadOnly = Boolean.TRUE.equals(base.get("readOnly"));
        boolean maintenanceReadOnly = maintenanceModeService.isEnabled();
        String mode = automaticReadOnly
                ? "READ_ONLY_DEGRADED"
                : maintenanceReadOnly ? "READ_ONLY_MAINTENANCE" : "READ_WRITE";

        Map<String, Object> result = new LinkedHashMap<>(base);
        result.put("automaticReadOnly", automaticReadOnly);
        result.put("maintenanceReadOnly", maintenanceReadOnly);
        result.put("readOnly", automaticReadOnly || maintenanceReadOnly);
        result.put("mode", mode);
        result.put("maintenance", maintenanceModeService.getStatus());
        return Map.copyOf(result);
    }

    private Map<String, Object> runtimeInfo() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memory.getHeapMemoryUsage();
        Runtime javaRuntime = Runtime.getRuntime();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uptimeMs", runtime.getUptime());
        result.put("startedAt", Instant.ofEpochMilli(runtime.getStartTime()).toString());
        result.put("javaVersion", System.getProperty("java.version", ""));
        result.put("processors", javaRuntime.availableProcessors());
        result.put("heapUsedBytes", Math.max(0L, heap.getUsed()));
        result.put("heapCommittedBytes", Math.max(0L, heap.getCommitted()));
        result.put("heapMaxBytes", Math.max(0L, heap.getMax()));
        result.put("applicationVersion", implementationVersion());
        return Map.copyOf(result);
    }

    private Map<String, Object> environmentInfo() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("osName", System.getProperty("os.name", ""));
        result.put("osVersion", System.getProperty("os.version", ""));
        result.put("osArchitecture", System.getProperty("os.arch", ""));
        result.put("javaVendor", System.getProperty("java.vendor", ""));
        result.put("javaVersion", System.getProperty("java.version", ""));
        result.put("timezone", System.getProperty("user.timezone", ""));
        result.put("applicationVersion", implementationVersion());
        result.put("gitSha", firstNonBlank(System.getenv("MRR_GIT_SHA"), System.getProperty("mrr.git.sha")));
        result.put("buildTime", firstNonBlank(System.getenv("MRR_BUILD_TIME"), System.getProperty("mrr.build.time")));
        return Map.copyOf(result);
    }

    private Map<String, Object> taskSummary() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String status : List.of("PENDING", "PROCESSING", "SUCCESS", "FAILED", "CANCELLED", "EXPIRED")) {
            counts.put(status, 0L);
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT status, COUNT(*) AS count
                    FROM app.archive_export_job
                    GROUP BY status
                    """);
            for (Map<String, Object> row : rows) {
                String status = String.valueOf(row.getOrDefault("status", ""));
                Object rawCount = row.get("count");
                if (counts.containsKey(status) && rawCount instanceof Number number) {
                    counts.put(status, number.longValue());
                }
            }
        } catch (Exception ignored) {
            // 旧库尚未创建任务表时返回零值快照。
        }
        long active = counts.get("PENDING") + counts.get("PROCESSING");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("counts", Map.copyOf(counts));
        result.put("active", active);
        result.put("failed", counts.get("FAILED"));
        return Map.copyOf(result);
    }

    private long recentServerErrorCount() {
        try {
            Long value = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM app.access_log
                    WHERE access_time >= CURRENT_TIMESTAMP - INTERVAL '24 hours'
                      AND response_status LIKE '5%'
                    """, Long.class);
            return value == null ? 0L : value;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private Map<String, Object> latestOperation() {
        List<Map<String, Object>> rows = recentOperations(1);
        return rows.isEmpty() ? Map.of() : new LinkedHashMap<>(rows.getFirst());
    }

    private Map<String, Object> runtimeCheck() {
        Map<String, Object> runtime = runtimeInfo();
        long heapUsed = numberValue(runtime.get("heapUsedBytes"));
        long heapMax = numberValue(runtime.get("heapMaxBytes"));
        double ratio = heapMax > 0 ? (double) heapUsed / heapMax : 0D;
        boolean passed = heapMax <= 0 || ratio < 0.90D;
        return diagnosticCheck(
                "runtime-memory",
                "JVM 运行与内存",
                passed,
                "WARNING",
                passed ? "JVM 正常运行，堆内存使用未达到警戒线" : "JVM 堆内存使用已超过 90%",
                runtime,
                "持续超过阈值时检查大对象、导出并发和堆配置，必要时在维护窗口重启服务。",
                "查看系统监控",
                "/monitoring"
        );
    }

    private Map<String, Object> exportBacklogCheck() {
        Map<String, Object> summary = taskSummary();
        long active = numberValue(summary.get("active"));
        long failed = numberValue(summary.get("failed"));
        boolean passed = active < EXPORT_BACKLOG_WARNING && failed == 0;
        return diagnosticCheck(
                "background-tasks",
                "后台导出任务",
                passed,
                "WARNING",
                passed ? "导出任务没有明显积压或失败" : "存在失败任务或任务积压",
                summary,
                "查看失败原因，取消无效任务，并确认临时目录空间和图片来源可用。",
                "查看任务中心",
                "/operations-center?tab=tasks"
        );
    }

    private Map<String, Object> recentErrorsCheck() {
        long errors = recentServerErrorCount();
        boolean passed = errors <= RECENT_ERROR_WARNING;
        return diagnosticCheck(
                "recent-server-errors",
                "近 24 小时服务端错误",
                passed,
                "WARNING",
                passed ? "近 24 小时服务端错误数量在允许范围内" : "近 24 小时服务端错误数量偏高",
                Map.of("count", errors, "warningThreshold", RECENT_ERROR_WARNING),
                "结合 requestId、错误日志和接口响应分析定位集中失败的接口。",
                "查看日志",
                "/logs"
        );
    }

    private Map<String, Object> maintenanceCheck() {
        Map<String, Object> maintenance = maintenanceModeService.getStatus();
        boolean enabled = Boolean.TRUE.equals(maintenance.get("enabled"));
        return diagnosticCheck(
                "maintenance-mode",
                "主动维护模式",
                !enabled,
                "WARNING",
                enabled ? "系统由管理员主动置为维护只读" : "系统未启用主动维护模式",
                maintenance,
                enabled ? "维护完成后先重新执行全面体检，确认自动检查通过，再关闭维护模式。" : "无需处理。",
                "维护模式",
                "/operations-center?tab=maintenance"
        );
    }

    private Map<String, Object> enrichReadinessCheck(Map<String, Object> source) {
        String code = String.valueOf(source.getOrDefault("code", "unknown"));
        Recommendation recommendation = recommendation(code);
        Map<String, Object> result = new LinkedHashMap<>(source);
        result.put("suggestion", recommendation.suggestion());
        result.put("actionLabel", recommendation.actionLabel());
        result.put("actionPath", recommendation.actionPath());
        return Map.copyOf(result);
    }

    private Recommendation recommendation(String code) {
        return switch (code) {
            case "database" -> new Recommendation(
                    "检查 PostgreSQL 服务、连接参数、网络和连接池，恢复后重新体检。",
                    "查看数据库监控", "/monitoring");
            case "flyway" -> new Recommendation(
                    "不要跳过失败迁移；核对当前版本和迁移校验值，在维护窗口处理。",
                    "查看部署检查", "/operations-center?tab=diagnostics");
            case "archive-foreign-keys" -> new Recommendation(
                    "先统计未关联记录，再补齐 archive_id 或验证外键，避免直接强制建约束。",
                    "查看数据关系", "/data-relations");
            case "temporary-directory" -> new Recommendation(
                    "检查目录权限和磁盘空间，清理已过期导出文件后重新体检。",
                    "查看任务中心", "/operations-center?tab=tasks");
            case "nginx-images" -> new Recommendation(
                    "逐个检查图片节点地址、Nginx 服务和静态目录映射。",
                    "打开 Nginx 浏览", "/nginx-browser");
            case "oss" -> new Recommendation(
                    "检查 OSS 配置、网络、Bucket 和 medical-records/ 前缀访问权限。",
                    "打开 OSS 浏览", "/oss-browser");
            case "backup" -> new Recommendation(
                    "确认备份文件真实存在并可被 pg_restore 读取，不要只更新时间字段。",
                    "查看维护状态", "/operations-center?tab=maintenance");
            default -> new Recommendation("根据检查详情处理后重新执行全面体检。", "重新检查", "/operations-center?tab=diagnostics");
        };
    }

    private Map<String, Object> diagnosticCheck(
            String code,
            String name,
            boolean passed,
            String severity,
            String message,
            Object details,
            String suggestion,
            String actionLabel,
            String actionPath
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("name", name);
        result.put("passed", passed);
        result.put("severity", severity);
        result.put("message", message);
        result.put("details", details == null ? Map.of() : details);
        result.put("suggestion", suggestion);
        result.put("actionLabel", actionLabel);
        result.put("actionPath", actionPath);
        return Map.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castChecks(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
    }

    private long numberValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private String implementationVersion() {
        Package pkg = OperationsCenterService.class.getPackage();
        return pkg == null || pkg.getImplementationVersion() == null ? "development" : pkg.getImplementationVersion();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null ? "" : second;
    }

    private List<Map<String, String>> quickLinks() {
        return List.of(
                Map.of("label", "系统监控", "path", "/monitoring"),
                Map.of("label", "日志管理", "path", "/logs"),
                Map.of("label", "接口响应分析", "path", "/response-analysis"),
                Map.of("label", "Nginx 文件浏览", "path", "/nginx-browser"),
                Map.of("label", "OSS 文件浏览", "path", "/oss-browser"),
                Map.of("label", "OSS 迁移管理", "path", "/oss-migration")
        );
    }

    private record Recommendation(String suggestion, String actionLabel, String actionPath) {
    }
}
