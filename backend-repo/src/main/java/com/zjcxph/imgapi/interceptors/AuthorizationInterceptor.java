package com.zjcxph.imgapi.interceptors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.annotation.AuthenticatedOnly;
import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.security.ApiAccessPolicy;
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
    private static final Logger logger = LoggerFactory.getLogger(AuthorizationInterceptor.class);
    public static final String AUTH_SESSION_ATTRIBUTE = "AUTH_SESSION";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        AccessDeclaration declaration = resolveDeclaration(request, handlerMethod);
        if (!declaration.isDeclared()) {
            logger.error("拒绝未声明访问策略的 API: method={}, uri={}, handler={}",
                    request.getMethod(), request.getRequestURI(), handlerMethod.getShortLogMessage());
            writeJsonResponse(response, 403, "接口未配置访问策略");
            return false;
        }
        if (declaration.isConflicting() || declaration.hasEmptyPermissions()) {
            logger.error("API 访问策略配置冲突: method={}, uri={}, handler={}",
                    request.getMethod(), request.getRequestURI(), handlerMethod.getShortLogMessage());
            writeJsonResponse(response, 500, "API 安全策略配置错误");
            return false;
        }

        AuthSession session = (AuthSession) request.getAttribute(AUTH_SESSION_ATTRIBUTE);
        if (session == null) {
            writeJsonResponse(response, 401, "Please login first");
            return false;
        }
        if (declaration.authenticatedOnly()) {
            return true;
        }
        if (isAdmin(session)) {
            return true;
        }

        String[] requiredPermissions = declaration.requiredPermissions();
        List<String> permissions = session.getPermissions() == null
                ? java.util.Collections.emptyList()
                : session.getPermissions();
        boolean allowed = Arrays.stream(requiredPermissions)
                .allMatch(permission -> PermissionResolver.hasPermission(permissions, permission));
        if (!allowed) {
            writeJsonResponse(response, 403, "No permission");
            return false;
        }

        logger.debug("permission granted for {} -> {}", session.getUsername(), Arrays.toString(requiredPermissions));
        return true;
    }

    private AccessDeclaration resolveDeclaration(HttpServletRequest request, HandlerMethod handlerMethod) {
        String[] override = ApiAccessPolicy.requiredPermissionOverride(request.getMethod(), request.getRequestURI());
        if (override != null) {
            return AccessDeclaration.forOverride(override);
        }

        RequirePermissions methodPermissions = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(), RequirePermissions.class);
        boolean methodAuthenticatedOnly = AnnotatedElementUtils.hasAnnotation(
                handlerMethod.getMethod(), AuthenticatedOnly.class);
        if (methodPermissions != null || methodAuthenticatedOnly) {
            return new AccessDeclaration(methodPermissions, methodAuthenticatedOnly, null);
        }

        RequirePermissions classPermissions = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getBeanType(), RequirePermissions.class);
        boolean classAuthenticatedOnly = AnnotatedElementUtils.hasAnnotation(
                handlerMethod.getBeanType(), AuthenticatedOnly.class);
        return new AccessDeclaration(classPermissions, classAuthenticatedOnly, null);
    }

    private void writeJsonResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        OBJECT_MAPPER.writeValue(response.getWriter(), Map.of("code", status, "message", message));
    }

    private boolean isAdmin(AuthSession session) {
        return "ADMIN".equalsIgnoreCase(session.getRoleCode());
    }

    private record AccessDeclaration(
            RequirePermissions permissions,
            boolean authenticatedOnly,
            String[] overridePermissions
    ) {
        private static AccessDeclaration forOverride(String[] permissions) {
            return new AccessDeclaration(null, false, permissions == null ? null : permissions.clone());
        }

        private boolean isDeclared() {
            return permissions != null || authenticatedOnly || overridePermissions != null;
        }

        private boolean isConflicting() {
            return permissions != null && authenticatedOnly;
        }

        private boolean hasEmptyPermissions() {
            return (permissions != null && permissions.value().length == 0)
                    || (overridePermissions != null && overridePermissions.length == 0);
        }

        private String[] requiredPermissions() {
            if (overridePermissions != null) {
                return overridePermissions.clone();
            }
            return permissions == null ? new String[0] : permissions.value();
        }
    }
}
