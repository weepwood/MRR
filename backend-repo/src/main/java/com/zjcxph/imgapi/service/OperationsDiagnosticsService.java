package com.zjcxph.imgapi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.annotation.AuthenticatedOnly;
import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.entity.AuthRole;
import com.zjcxph.imgapi.mapper.AuthRoleMapper;
import com.zjcxph.imgapi.security.ApiAccessPolicy;
import com.zjcxph.imgapi.utils.AuthContext;
import com.zjcxph.imgapi.utils.PermissionResolver;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

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
 * 运维诊断中心的导出、权限与就绪状态聚合服务。
 */
@Service
public class OperationsDiagnosticsService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final JdbcTemplate jdbcTemplate;
    private final AuthRoleMapper authRoleMapper;
    private final RequestMappingHandlerMapping handlerMapping;
    private final ObjectMapper objectMapper;
    private final DeploymentReadinessService readinessService;

    public OperationsDiagnosticsService(
            JdbcTemplate jdbcTemplate,
            AuthRoleMapper authRoleMapper,
            RequestMappingHandlerMapping handlerMapping,
            ObjectMapper objectMapper,
            DeploymentReadinessService readinessService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.authRoleMapper = authRoleMapper;
        this.handlerMapping = handlerMapping;
        this.objectMapper = objectMapper;
        this.readinessService = readinessService;
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
                authenticatedOnly = AnnotatedElementUtils.findMergedAnnotation(
                        handler.getBeanType(), AuthenticatedOnly.class);
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
                    endpoint.put(
                            "operation",
                            handler.getBeanType().getSimpleName() + "." + handler.getMethod().getName()
                    );
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
}
