package com.zjcxph.imgapi.security;

import com.zjcxph.imgapi.common.Permissions;
import org.springframework.util.AntPathMatcher;

import java.util.List;

/**
 * 只集中保存两类无法通过 Controller 注解表达的路径规则：
 * 框架排除路径、匿名 API，以及待迁移的少量遗留权限例外。
 */
public final class ApiAccessPolicy {

    public static final String REGISTRATION_PATH = "/api/v1/auth/register";

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private static final List<String> INTERCEPTOR_EXCLUDES = List.of(
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs.yaml",
            "/v3/api-docs/**", "/docs/**", "/api/v1/documentation/access",
            "/api/v1/public/status/**", "/api/v1/public/config/**", "/error", "/actuator/**"
    );

    private static final List<String> ANONYMOUS_API_PATTERNS = List.of(
            "/api/v1/auth/login",
            REGISTRATION_PATH,
            "/api/v1/img/hello",
            "/api/v1/integration/archive/tickets",
            "/api/v1/external/archive/**"
    );

    private static final List<PermissionRule> LEGACY_PERMISSION_OVERRIDES = List.of(
            rule("PUT", "/api/v1/img/updateImageType/*", Permissions.RECORD_EDIT),
            rule("POST", "/api/v1/logs/retention/cleanup", Permissions.SYSTEM_MANAGE)
    );

    private ApiAccessPolicy() {
    }

    public static String[] generalExcludes() {
        return INTERCEPTOR_EXCLUDES.toArray(String[]::new);
    }

    public static String[] authenticationExcludes() {
        return ANONYMOUS_API_PATTERNS.toArray(String[]::new);
    }

    public static boolean isAuthenticationExcluded(String path) {
        return matches(ANONYMOUS_API_PATTERNS, path);
    }

    public static boolean isPublicApiPath(String path) {
        return matches(INTERCEPTOR_EXCLUDES, path)
                || matches(ANONYMOUS_API_PATTERNS, path);
    }

    public static String[] requiredPermissionOverride(String method, String path) {
        if (method == null || path == null) {
            return null;
        }
        return LEGACY_PERMISSION_OVERRIDES.stream()
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
        return path != null
                && !path.isBlank()
                && patterns.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    private static PermissionRule rule(String method, String pathPattern, String... permissions) {
        return new PermissionRule(method, pathPattern, permissions);
    }

    private record PermissionRule(String method, String pathPattern, String[] permissions) {
    }
}
