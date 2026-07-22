package com.zjcxph.imgapi.interceptors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.annotation.AuthenticatedOnly;
import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.security.ApiAccessPolicy;
import com.zjcxph.imgapi.service.DeveloperApiAccessService;
import com.zjcxph.imgapi.utils.PermissionResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public class AuthorizationInterceptor implements HandlerInterceptor {

    public static final String AUTH_SESSION_ATTRIBUTE = "AUTH_SESSION";

    private static final Logger logger = LoggerFactory.getLogger(AuthorizationInterceptor.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DeveloperApiAccessService developerApiAccessService;

    public AuthorizationInterceptor(DeveloperApiAccessService developerApiAccessService) {
        this.developerApiAccessService = developerApiAccessService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        String path = normalizeRequestPath(request);
        if (ApiAccessPolicy.isPublicApiPath(path)) {
            return true;
        }

        String[] requiredPermissions = ApiAccessPolicy.requiredPermissionOverride(request.getMethod(), path);
        RequirePermissions permissionAnnotation = null;
        boolean authenticatedOnly = false;

        if (requiredPermissions == null) {
            RequirePermissions methodPermission = AnnotatedElementUtils.findMergedAnnotation(
                    handlerMethod.getMethod(), RequirePermissions.class);
            boolean methodAuthenticated = AnnotatedElementUtils.hasAnnotation(
                    handlerMethod.getMethod(), AuthenticatedOnly.class);

            if (methodPermission != null || methodAuthenticated) {
                permissionAnnotation = methodPermission;
                authenticatedOnly = methodAuthenticated;
            } else {
                permissionAnnotation = AnnotatedElementUtils.findMergedAnnotation(
                        handlerMethod.getBeanType(), RequirePermissions.class);
                authenticatedOnly = AnnotatedElementUtils.hasAnnotation(
                        handlerMethod.getBeanType(), AuthenticatedOnly.class);
            }
        }

        if (requiredPermissions == null && permissionAnnotation == null && !authenticatedOnly) {
            logger.error("拒绝未声明访问策略的 API: method={}, uri={}, handler={}",
                    request.getMethod(), request.getRequestURI(), handlerMethod.getShortLogMessage());
            writeJsonResponse(response, 403, "接口未配置访问策略");
            return false;
        }
        if (permissionAnnotation != null && authenticatedOnly) {
            logger.error("API 同时声明了仅登录与业务权限: handler={}", handlerMethod.getShortLogMessage());
            writeJsonResponse(response, 500, "API 安全策略配置错误");
            return false;
        }
        if (permissionAnnotation != null) {
            requiredPermissions = permissionAnnotation.value();
        }
        if (!authenticatedOnly && (requiredPermissions == null || requiredPermissions.length == 0)) {
            logger.error("API 权限声明为空: handler={}", handlerMethod.getShortLogMessage());
            writeJsonResponse(response, 500, "API 安全策略配置错误");
            return false;
        }

        AuthSession session = (AuthSession) request.getAttribute(AUTH_SESSION_ATTRIBUTE);
        if (session == null) {
            writeJsonResponse(response, 401, "请先登录");
            return false;
        }
        if (authenticatedOnly || session.isAdmin()) {
            return true;
        }
        if (developerApiAccessService.isPermissionBypassAllowed(request)) {
            response.setHeader("X-MRR-Developer-Mode", "enabled");
            response.setHeader("X-MRR-Access-Mode", DeveloperApiAccessService.API_PERMISSION_BYPASS_MODE);
            logger.warn("Developer API permission bypass: user={}, method={}, path={}, remoteIp={}",
                    session.getUsername(), request.getMethod(), path, request.getRemoteAddr());
            return true;
        }

        List<String> userPermissions = session.getPermissions() == null
                ? List.of()
                : session.getPermissions();
        boolean allowed = Arrays.stream(requiredPermissions)
                .allMatch(permission -> PermissionResolver.hasPermission(userPermissions, permission));
        if (!allowed) {
            writeJsonResponse(response, 403, "没有接口访问权限");
            return false;
        }

        logger.debug("权限校验通过: user={}, permissions={}",
                session.getUsername(), Arrays.toString(requiredPermissions));
        return true;
    }

    private String normalizeRequestPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private void writeJsonResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        OBJECT_MAPPER.writeValue(response.getWriter(), Map.of("code", status, "message", message));
    }
}
