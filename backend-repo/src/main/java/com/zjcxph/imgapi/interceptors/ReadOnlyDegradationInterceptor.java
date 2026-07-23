package com.zjcxph.imgapi.interceptors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.service.DeploymentReadinessService;
import com.zjcxph.imgapi.service.MaintenanceModeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Map;

/**
 * 关键依赖异常或管理员主动维护时阻止业务写入，同时保留登录、查询与诊断能力。
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
            "/api/v1/operations/readiness/refresh",
            "/api/v1/operations/diagnostics/run",
            "/api/v1/operations/maintenance/**",
            "/api/v1/integration/archive/tickets",
            "/api/v1/external/archive/session",
            "/api/v1/external/archive/logout"
    );

    private final DeploymentReadinessService readinessService;
    private final MaintenanceModeService maintenanceModeService;
    private final ObjectMapper objectMapper;

    public ReadOnlyDegradationInterceptor(
            DeploymentReadinessService readinessService,
            MaintenanceModeService maintenanceModeService,
            ObjectMapper objectMapper
    ) {
        this.readinessService = readinessService;
        this.maintenanceModeService = maintenanceModeService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (isSafeMethod(request.getMethod()) || isAllowedReadOnlyPost(request)) {
            return true;
        }

        boolean automaticDegradation = readinessService.isReadOnly();
        boolean maintenanceMode = maintenanceModeService.isEnabled();
        if (!automaticDegradation && !maintenanceMode) {
            return true;
        }

        Map<String, Object> readiness = readinessService.getSnapshot();
        Map<String, Object> maintenance = maintenanceModeService.getStatus();
        String mode = automaticDegradation ? "READ_ONLY_DEGRADED" : "READ_ONLY_MAINTENANCE";
        String code = automaticDegradation ? "READ_ONLY_DEGRADED" : "MAINTENANCE_MODE";
        String message = automaticDegradation
                ? "系统关键依赖异常，当前处于只读降级模式，写入和迁移操作已暂停"
                : "系统正在进行计划维护，写入、导入、导出创建和迁移操作已暂停";

        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Retry-After", "30");
        objectMapper.writeValue(response.getWriter(), Map.of(
                "code", code,
                "message", message,
                "mode", mode,
                "checkedAt", readiness.getOrDefault("checkedAt", ""),
                "maintenance", maintenance
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
