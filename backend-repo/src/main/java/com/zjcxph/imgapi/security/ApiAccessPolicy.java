package com.zjcxph.imgapi.security;

import org.springframework.util.AntPathMatcher;

import java.util.List;

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

    private static final List<PermissionRule> PERMISSION_OVERRIDES = List.of(
            rule("PUT", "/api/v1/img/updateImageType/*", "record:edit"),
            rule("POST", "/api/v1/scan/condition", "record:read"),
            rule("POST", "/api/v1/scan/page/condition", "record:read"),
            rule("POST", "/api/v1/search/archive-cases", "search:read"),
            rule("POST", "/api/v1/search/getBAHByID", "search:read"),
            rule("POST", "/api/v1/archive-search-history", "record:read"),
            rule("PUT", "/api/v1/archive-search-history/*/favorite", "record:read"),
            rule("POST", "/api/v1/archive-exports/jobs", "record:read"),
            rule("POST", "/api/v1/archive-exports/jobs/*/cancel", "record:read"),
            rule("POST", "/api/v1/system/data-quality/run", "system:manage"),
            rule("POST", "/api/v1/logs/retention/cleanup", "system:manage")
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
        if (method == null || path == null) return null;
        return PERMISSION_OVERRIDES.stream()
                .filter(rule -> rule.method().equalsIgnoreCase(method)
                        && PATH_MATCHER.match(rule.pathPattern(), path))
                .findFirst()
                .map(rule -> rule.permissions().clone())
                .orElse(null);
    }

    public static boolean hasPermissionOverride(String method, String path) {
        return requiredPermissionOverride(method, path) != null;
    }

    private static boolean matches(List<String> patterns, String path) {
        if (path == null || path.isBlank()) return false;
        return patterns.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    private static PermissionRule rule(String method, String pathPattern, String... permissions) {
        return new PermissionRule(method, pathPattern, permissions);
    }

    private record PermissionRule(String method, String pathPattern, String[] permissions) {
    }
}
