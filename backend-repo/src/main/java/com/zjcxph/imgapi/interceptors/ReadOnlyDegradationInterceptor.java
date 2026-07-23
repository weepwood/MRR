package com.zjcxph.imgapi.interceptors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.service.DeploymentReadinessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Map;

/**
 * 关键依赖异常时阻止业务写入和迁移，同时保留登录、查询与诊断能力。
 */
@Component
public class ReadOnlyDegradationInterceptor implements HandlerInterceptor {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final List<String> READ_ONLY_POST_ALLOWLIST = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/logout",
            "/api/v1/auth/password/**",
            "/api/v1/search/**",
            "/api/v1/scan/page/condition",
            "/api/v1/archive-exports/plan/**",
            "/api/v1/integration/archive/tickets",
            "/api/v1/external/archive/session",
            "/api/v1/external/archive/logout"
    );

    private final DeploymentReadinessService readinessService;
    private final ObjectMapper objectMapper;

    public ReadOnlyDegradationInterceptor(
            DeploymentReadinessService readinessService,
            ObjectMapper objectMapper
    ) {
        this.readinessService = readinessService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (isSafeMethod(request.getMethod()) || isAllowedReadOnlyPost(request)) {
            return true;
        }
        if (!readinessService.isReadOnly()) {
            return true;
        }

        Map<String, Object> snapshot = readinessService.getSnapshot();
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Retry-After", "30");
        objectMapper.writeValue(response.getWriter(), Map.of(
                "code", "READ_ONLY_DEGRADED",
                "message", "系统关键依赖异常，当前处于只读降级模式，写入和迁移操作已暂停",
                "mode", snapshot.getOrDefault("mode", "READ_ONLY_DEGRADED"),
                "checkedAt", snapshot.getOrDefault("checkedAt", "")
        ));
        return false;
    }

    private boolean isSafeMethod(String method) {
        return "GET".equalsIgnoreCase(method)
                || "HEAD".equalsIgnoreCase(method)
                || "OPTIONS".equalsIgnoreCase(method);
    }

    private boolean isAllowedReadOnlyPost(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        String normalizedPath = path;
        return READ_ONLY_POST_ALLOWLIST.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, normalizedPath));
    }
}
