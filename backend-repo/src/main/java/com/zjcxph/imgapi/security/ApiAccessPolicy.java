package com.zjcxph.imgapi.security;

import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.Map;

/**
 * API 公开路径、拦截器排除项和少量遗留接口权限覆盖的单一配置来源。
 */
public final class ApiAccessPolicy {

    public static final String REGISTRATION_PATH = "/api/v1/auth/register";

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private static final List<String> GENERAL_EXCLUDES = List.of(
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs.yaml",
            "/v3/api-docs/**", "/docs/**", "/api/v1/documentation/access",
            "/api/v1/public/status/**", "/api/v1/public/config/**", "/error", "/actuator/**"
    );

    private static final List<String> AUTHENTICATION_EXCLUDES = List.of(
            "/api/v1/auth/login", REGISTRATION_PATH, "/api/v1/img/hello",
            "/api/v1/integration/archive/tickets", "/api/v1/external/archive/**"
    );

    public static final List<String> PUBLIC_API_PATTERNS = List.of(
            "/api/v1/documentation/access", "/api/v1/public/status/**", "/api/v1/public/config/**",
            "/api/v1/auth/login", REGISTRATION_PATH, "/api/v1/img/hello",
            "/api/v1/integration/archive/tickets", "/api/v1/external/archive/**"
    );

    private static final Map<String, String[]> LEGACY_PERMISSION_OVERRIDES = Map.of(
            key("POST", "/api/v1/logs/retention/cleanup"), new String[]{"system:manage"}
    );

    private ApiAccessPolicy() {
    }

    public static String[] generalExcludes() {
        return GENERAL_EXCLUDES.toArray(String[]::new);
    }

    public static String[] authenticationExcludes() {
        return AUTHENTICATION_EXCLUDES.toArray(String[]::new);
    }

    public static boolean isAuthenticationExcluded(String path) {
        return matches(AUTHENTICATION_EXCLUDES, path);
    }

    public static boolean isPublicApiPath(String path) {
        return matches(PUBLIC_API_PATTERNS, path);
    }

    public static String[] requiredPermissionOverride(String method, String path) {
        String[] permissions = LEGACY_PERMISSION_OVERRIDES.get(key(method, path));
        return permissions == null ? null : permissions.clone();
    }

    public static boolean hasPermissionOverride(String method, String path) {
        return requiredPermissionOverride(method, path) != null;
    }

    private static boolean matches(List<String> patterns, String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        return patterns.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    private static String key(String method, String path) {
        String normalizedMethod = method == null ? "" : method.trim().toUpperCase();
        String normalizedPath = path == null ? "" : path.trim();
        return normalizedMethod + " " + normalizedPath;
    }
}
