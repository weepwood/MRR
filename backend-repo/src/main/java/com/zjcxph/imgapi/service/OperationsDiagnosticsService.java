package com.zjcxph.imgapi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.annotation.AuthenticatedOnly;
import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.entity.AuthRole;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.mapper.AuthRoleMapper;
import com.zjcxph.imgapi.security.ApiAccessPolicy;
import com.zjcxph.imgapi.utils.AuthContext;
import com.zjcxph.imgapi.utils.MedicalRecordCodeUtils;
import com.zjcxph.imgapi.utils.PermissionResolver;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 运维诊断中心的只读聚合服务。
 */
@Service
public class OperationsDiagnosticsService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final JdbcTemplate jdbcTemplate;
    private final ScanService scanService;
    private final ImageUrlService imageUrlService;
    private final AuthRoleMapper authRoleMapper;
    private final RequestMappingHandlerMapping handlerMapping;
    private final ObjectMapper objectMapper;
    private final DeploymentReadinessService readinessService;
    private final HttpClient httpClient;

    public OperationsDiagnosticsService(
            JdbcTemplate jdbcTemplate,
            ScanService scanService,
            ImageUrlService imageUrlService,
            AuthRoleMapper authRoleMapper,
            RequestMappingHandlerMapping handlerMapping,
            ObjectMapper objectMapper,
            DeploymentReadinessService readinessService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.scanService = scanService;
        this.imageUrlService = imageUrlService;
        this.authRoleMapper = authRoleMapper;
        this.handlerMapping = handlerMapping;
        this.objectMapper = objectMapper;
        this.readinessService = readinessService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public Map<String, Object> diagnoseImageSource(String bah, String sjh, Integer imageId) {
        List<Map<String, Object>> steps = new ArrayList<>();
        String normalizedBah = MedicalRecordCodeUtils.normalizeOrEmpty(bah);
        String normalizedSjh = MedicalRecordCodeUtils.normalizeOrEmpty(sjh);

        steps.add(step("INPUT_NORMALIZE", true, "输入编号已规范化", Map.of(
                "bah", normalizedBah,
                "sjh", normalizedSjh,
                "imageId", imageId == null ? "" : imageId
        )));

        if (imageId == null && normalizedBah.isBlank() && normalizedSjh.isBlank()) {
            steps.add(step("INPUT_VALIDATE", false, "病案号、上架号、图片 ID 至少填写一项", Map.of()));
            return diagnosisResult(null, steps, null, null, null);
        }
        if (imageId == null && MedicalRecordCodeUtils.requiresSjhForBah(normalizedBah)
                && normalizedSjh.isBlank()) {
            steps.add(step("ARCHIVE_RULE", false, "病案号达到 10000000 后必须同时提供上架号", Map.of()));
            return diagnosisResult(null, steps, null, null, null);
        }

        Scan scan;
        if (imageId != null) {
            scan = scanService.findById(imageId);
            steps.add(step("IMAGE_LOOKUP", scan != null,
                    scan == null ? "未找到指定图片" : "已按图片 ID 找到扫描记录",
                    scan == null ? Map.of("imageId", imageId) : scanDetails(scan)));
        } else {
            List<Scan> scans = scanService.getImageListByCode(
                    normalizedBah,
                    MedicalRecordCodeUtils.toSearchTerm(bah),
                    normalizedSjh,
                    MedicalRecordCodeUtils.toSearchTerm(sjh)
            );
            scan = scans.isEmpty() ? null : scans.getFirst();
            steps.add(step("ARCHIVE_LOOKUP", scan != null,
                    scan == null ? "未找到匹配的有效图片" : "已找到病案图片，默认诊断第一页",
                    Map.of("matchedImages", scans.size())));
        }

        if (scan == null) {
            return diagnosisResult(null, steps, null, null, null);
        }

        boolean archiveLinked = scan.getArchiveId() != null;
        steps.add(step("ARCHIVE_LINK", archiveLinked,
                archiveLinked ? "图片已关联病案主档" : "图片尚未关联 archive_id，将依赖兼容查询",
                Map.of("archiveId", scan.getArchiveId() == null ? "" : scan.getArchiveId())));

        String localUrl = imageUrlService.buildImageUrl(scan);
        boolean localReachable = probe(localUrl);
        steps.add(step("LOCAL_SOURCE", StringUtils.hasText(localUrl),
                StringUtils.hasText(localUrl) ? "已构造本地/Nginx 图片地址" : "无法构造本地图片地址",
                Map.of("url", localUrl == null ? "" : localUrl, "reachable", localReachable)));

        String preferredSource = imageUrlService.getEffectiveImageSource();
        String selectedUrl = imageUrlService.buildPreferredImageUrl(scan);
        boolean selectedReachable = probe(selectedUrl);
        boolean hasOss = StringUtils.hasText(scan.getOssUrl());
        steps.add(step("OSS_SOURCE", hasOss,
                hasOss ? "扫描记录包含 OSS Key" : "扫描记录没有 OSS Key，将使用本地来源",
                Map.of(
                        "ossKey", scan.getOssUrl() == null ? "" : scan.getOssUrl(),
                        "migrationStatus", scan.getMigrationStatus() == null ? "" : scan.getMigrationStatus()
                )));

        String selectedType = selectedUrl != null && selectedUrl.equals(localUrl) ? "LOCAL" : "OSS";
        String fallbackReason = null;
        if ("oss".equalsIgnoreCase(preferredSource) && "LOCAL".equals(selectedType)) {
            fallbackReason = hasOss ? "OSS_SIGNING_FAILED_OR_EMPTY" : "OSS_KEY_MISSING";
        }
        steps.add(step("FINAL_SELECTION", StringUtils.hasText(selectedUrl),
                StringUtils.hasText(selectedUrl) ? "已确定最终图片来源" : "没有可用的最终图片地址",
                Map.of(
                        "preferredSource", preferredSource,
                        "selectedSource", selectedType,
                        "selectedUrl", selectedUrl == null ? "" : selectedUrl,
                        "reachable", selectedReachable,
                        "fallbackReason", fallbackReason == null ? "" : fallbackReason
                )));

        return diagnosisResult(scan, steps, selectedType, selectedUrl, fallbackReason);
    }

    public Map<String, Object> integritySummary() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> tables = new ArrayList<>();
        tables.add(coverage("mr_scan", "uploadflag <> 0"));
        tables.add(coverage("mr_statistics", "TRUE"));
        tables.add(coverage("mr_archive_box_record", "TRUE"));

        Map<String, Object> scans = jdbcTemplate.queryForMap("""
                SELECT
                    COUNT(*) FILTER (WHERE uploadflag <> 0) AS total,
                    COUNT(*) FILTER (WHERE uploadflag <> 0 AND archive_id IS NOT NULL) AS archive_linked,
                    COUNT(*) FILTER (WHERE uploadflag <> 0 AND NULLIF(BTRIM(oss_url), '') IS NOT NULL) AS oss_linked,
                    COUNT(*) FILTER (WHERE uploadflag <> 0 AND NULLIF(BTRIM(sjh), '') IS NULL) AS missing_sjh
                FROM app.mr_scan
                """);
        long brokenLinks = number(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM app.mr_scan s
                LEFT JOIN app.mr_archive a ON a.id = s.archive_id
                WHERE s.uploadflag <> 0 AND s.archive_id IS NOT NULL AND a.id IS NULL
                """, Long.class));
        long duplicateArchiveGroups = number(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT bah, COALESCE(sjh, ''), COUNT(*)
                    FROM app.mr_archive
                    GROUP BY bah, COALESCE(sjh, '')
                    HAVING COUNT(*) > 1
                ) duplicate_groups
                """, Long.class));
        long totalScans = number(scans.get("total"));
        long archiveLinked = number(scans.get("archive_linked"));
        long ossLinked = number(scans.get("oss_linked"));

        result.put("generatedAt", Instant.now().toString());
        result.put("archiveCoverage", ratio(archiveLinked, totalScans));
        result.put("ossCoverage", ratio(ossLinked, totalScans));
        result.put("missingSjh", number(scans.get("missing_sjh")));
        result.put("brokenLinks", brokenLinks);
        result.put("duplicateArchiveGroups", duplicateArchiveGroups);
        result.put("totalActiveScans", totalScans);
        result.put("tables", tables);
        return result;
    }

    public List<Map<String, Object>> exportCenter(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return jdbcTemplate.queryForList("""
                SELECT id::text, owner_username, format, scope, status, bah, sjh, scan_ids,
                       planned_count, processed_count, failed_count, estimated_bytes, output_bytes,
                       source_summary, file_name, sha256, expires_at, created_at, started_at,
                       completed_at, updated_at, download_count, last_downloaded_at, error_message
                FROM app.archive_export_job
                ORDER BY created_at DESC
                LIMIT ?
                """, safeLimit);
    }

    public Map<String, Object> permissionMatrix(boolean comparePrevious) {
        Map<String, Object> current = currentPermissionMatrix();
        Map<String, Object> result = new LinkedHashMap<>(current);
        Map<String, Object> previous = latestPermissionSnapshot();
        result.put("previousVersion", previous.get("version"));
        result.put("previousCreatedAt", previous.get("createdAt"));
        if (comparePrevious && previous.get("matrix") instanceof Map<?, ?> previousMatrix) {
            result.put("diff", diffPermissionMatrix(castMap(previousMatrix), current));
        } else {
            result.put("diff", Map.of("available", false, "changes", List.of()));
        }
        return result;
    }

    public Map<String, Object> savePermissionSnapshot(String version) {
        String normalizedVersion = StringUtils.hasText(version)
                ? version.trim()
                : "snapshot-" + Instant.now();
        Map<String, Object> matrix = currentPermissionMatrix();
        String actor = AuthContext.getCurrentUser() == null
                ? "unknown"
                : AuthContext.getCurrentUser().getUsername();
        try {
            jdbcTemplate.update("""
                    INSERT INTO app.permission_matrix_snapshot(version, matrix_json, created_by)
                    VALUES (?, CAST(? AS jsonb), ?)
                    """, normalizedVersion, objectMapper.writeValueAsString(matrix), actor);
        } catch (Exception exception) {
            throw new IllegalStateException("保存权限矩阵快照失败: " + exception.getMessage(), exception);
        }
        return Map.of("version", normalizedVersion, "createdBy", actor, "saved", true);
    }

    public Map<String, Object> readiness() {
        return readinessService.getSnapshot();
    }

    private Map<String, Object> currentPermissionMatrix() {
        List<AuthRole> roles = authRoleMapper.findAll();
        List<Map<String, Object>> endpoints = new ArrayList<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            HandlerMethod handler = entry.getValue();
            Set<String> patterns = entry.getKey().getPatternValues();
            if (patterns.stream().noneMatch(path -> path.startsWith("/api/"))) {
                continue;
            }
            Set<RequestMethod> mappedMethods = entry.getKey().getMethodsCondition().getMethods();
            List<String> methods = mappedMethods.isEmpty()
                    ? List.of("ANY")
                    : mappedMethods.stream().map(Enum::name).sorted().toList();

            RequirePermissions permission = AnnotatedElementUtils.findMergedAnnotation(
                    handler.getMethod(), RequirePermissions.class);
            if (permission == null) {
                permission = AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), RequirePermissions.class);
            }
            AuthenticatedOnly authenticatedOnly = AnnotatedElementUtils.findMergedAnnotation(
                    handler.getMethod(), AuthenticatedOnly.class);
            if (authenticatedOnly == null) {
                authenticatedOnly = AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), AuthenticatedOnly.class);
            }

            for (String path : patterns) {
                for (String method : methods) {
                    String[] override = "ANY".equals(method)
                            ? null
                            : ApiAccessPolicy.requiredPermissionOverride(method, path);
                    List<String> required = override != null
                            ? Arrays.asList(override)
                            : permission == null ? List.of() : Arrays.asList(permission.value());
                    String policy = ApiAccessPolicy.isPublicApiPath(path)
                            ? "PUBLIC"
                            : authenticatedOnly != null ? "AUTHENTICATED_ONLY"
                            : required.isEmpty() ? "UNDECLARED" : "PERMISSION";

                    Map<String, Boolean> roleAccess = new LinkedHashMap<>();
                    for (AuthRole role : roles) {
                        roleAccess.put(role.getCode(), roleAllows(role, policy, required));
                    }
                    Map<String, Object> endpoint = new LinkedHashMap<>();
                    endpoint.put("key", method + " " + path);
                    endpoint.put("method", method);
                    endpoint.put("path", path);
                    endpoint.put("operation", handler.getBeanType().getSimpleName() + "." + handler.getMethod().getName());
                    endpoint.put("policy", policy);
                    endpoint.put("requiredPermissions", required);
                    endpoint.put("roleAccess", roleAccess);
                    endpoints.add(endpoint);
                }
            }
        }
        endpoints.sort(Comparator.comparing(item -> String.valueOf(item.get("key"))));

        List<Map<String, Object>> roleRows = roles.stream().map(role -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", role.getCode());
            item.put("name", role.getName());
            item.put("permissions", resolvedPermissions(role));
            return item;
        }).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generatedAt", Instant.now().toString());
        result.put("roles", roleRows);
        result.put("endpoints", endpoints);
        return result;
    }

    private boolean roleAllows(AuthRole role, String policy, List<String> required) {
        if ("PUBLIC".equals(policy) || "AUTHENTICATED_ONLY".equals(policy)) {
            return true;
        }
        if (!"PERMISSION".equals(policy)) {
            return false;
        }
        if ("ADMIN".equalsIgnoreCase(role.getCode())) {
            return true;
        }
        List<String> permissions = resolvedPermissions(role);
        return required.stream().allMatch(permission -> PermissionResolver.hasPermission(permissions, permission));
    }

    private List<String> resolvedPermissions(AuthRole role) {
        if (role == null || !StringUtils.hasText(role.getPermissions())) {
            return List.of();
        }
        List<String> configured = Arrays.stream(role.getPermissions().split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        return new ArrayList<>(PermissionResolver.resolve(configured));
    }

    private Map<String, Object> latestPermissionSnapshot() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT version, matrix_json::text AS matrix_json, created_at
                    FROM app.permission_matrix_snapshot
                    ORDER BY id DESC
                    LIMIT 1
                    """);
            if (rows.isEmpty()) {
                return Map.of("version", "", "createdAt", "", "matrix", Map.of());
            }
            Map<String, Object> row = rows.getFirst();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("version", row.get("version"));
            result.put("createdAt", row.get("created_at"));
            result.put("matrix", objectMapper.readValue(String.valueOf(row.get("matrix_json")), MAP_TYPE));
            return result;
        } catch (Exception exception) {
            return Map.of("version", "", "createdAt", "", "matrix", Map.of());
        }
    }

    private Map<String, Object> diffPermissionMatrix(Map<String, Object> previous, Map<String, Object> current) {
        Map<String, Map<String, Object>> previousEndpoints = indexByKey(previous.get("endpoints"));
        Map<String, Map<String, Object>> currentEndpoints = indexByKey(current.get("endpoints"));
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(previousEndpoints.keySet());
        keys.addAll(currentEndpoints.keySet());

        List<Map<String, Object>> changes = new ArrayList<>();
        for (String key : keys) {
            Map<String, Object> before = previousEndpoints.get(key);
            Map<String, Object> after = currentEndpoints.get(key);
            if (before == null) {
                changes.add(Map.of("key", key, "type", "ADDED", "after", after));
            } else if (after == null) {
                changes.add(Map.of("key", key, "type", "REMOVED", "before", before));
            } else if (!permissionComparable(before).equals(permissionComparable(after))) {
                changes.add(Map.of("key", key, "type", "CHANGED", "before", before, "after", after));
            }
        }
        return Map.of("available", true, "changeCount", changes.size(), "changes", changes);
    }

    private Map<String, Object> permissionComparable(Map<String, Object> value) {
        return Map.of(
                "policy", value.getOrDefault("policy", ""),
                "requiredPermissions", value.getOrDefault("requiredPermissions", List.of()),
                "roleAccess", value.getOrDefault("roleAccess", Map.of())
        );
    }

    private Map<String, Map<String, Object>> indexByKey(Object raw) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        if (!(raw instanceof List<?> list)) {
            return result;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> cast = castMap(map);
                Object key = cast.get("key");
                if (key != null) {
                    result.put(String.valueOf(key), cast);
                }
            }
        }
        return result;
    }

    private Map<String, Object> castMap(Map<?, ?> value) {
        Map<String, Object> result = new LinkedHashMap<>();
        value.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private Map<String, Object> coverage(String table, String filter) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT COUNT(*) AS total, COUNT(*) FILTER (WHERE archive_id IS NOT NULL) AS linked "
                        + "FROM app." + table + " WHERE " + filter
        );
        long total = number(row.get("total"));
        long linked = number(row.get("linked"));
        return Map.of(
                "table", table,
                "total", total,
                "linked", linked,
                "unlinked", Math.max(0L, total - linked),
                "coverage", ratio(linked, total)
        );
    }

    private Map<String, Object> diagnosisResult(
            Scan scan,
            List<Map<String, Object>> steps,
            String selectedSource,
            String selectedUrl,
            String fallbackReason
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("diagnosedAt", Instant.now().toString());
        result.put("found", scan != null);
        result.put("scan", scan == null ? Map.of() : scanDetails(scan));
        result.put("selectedSource", selectedSource == null ? "" : selectedSource);
        result.put("selectedUrl", selectedUrl == null ? "" : selectedUrl);
        result.put("fallbackReason", fallbackReason == null ? "" : fallbackReason);
        result.put("steps", steps);
        return result;
    }

    private Map<String, Object> scanDetails(Scan scan) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("id", scan.getId());
        details.put("archiveId", scan.getArchiveId() == null ? "" : scan.getArchiveId());
        details.put("bah", scan.getBah() == null ? "" : scan.getBah());
        details.put("sjh", scan.getSjh() == null ? "" : scan.getSjh());
        details.put("folder", scan.getFolder() == null ? "" : scan.getFolder());
        details.put("filename", scan.getFilename() == null ? "" : scan.getFilename());
        details.put("sourceType", scan.getSourceType() == null ? "" : scan.getSourceType());
        details.put("sourceNode", scan.getSourceNode() == null ? "" : scan.getSourceNode());
        details.put("sourceRef", scan.getSourceRef() == null ? "" : scan.getSourceRef());
        details.put("ossKey", scan.getOssUrl() == null ? "" : scan.getOssUrl());
        details.put("migrationStatus", scan.getMigrationStatus() == null ? "" : scan.getMigrationStatus());
        return details;
    }

    private Map<String, Object> step(String code, boolean success, String message, Object details) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("code", code);
        step.put("success", success);
        step.put("message", message);
        step.put("details", details);
        return step;
    }

    private boolean probe(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            int status = httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            return status >= 200 && status < 500;
        } catch (Exception exception) {
            return false;
        }
    }

    private double ratio(long value, long total) {
        if (total <= 0) {
            return 1.0d;
        }
        return Math.round((value * 10000.0d / total)) / 10000.0d;
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
