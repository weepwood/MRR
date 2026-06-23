package com.zjcxph.imgapi.interceptors;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.utils.PermissionResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        HandlerMethod handlerMethod = (HandlerMethod) handler;

        RequirePermissions annotation = handlerMethod.getMethodAnnotation(RequirePermissions.class);
        if (annotation == null) {
            annotation = handlerMethod.getBeanType().getAnnotation(RequirePermissions.class);
        }
        if (annotation == null || annotation.value().length == 0) {
            return true;
        }

        AuthSession session = (AuthSession) request.getAttribute(AUTH_SESSION_ATTRIBUTE);
        if (session == null) {
            writeJsonResponse(response, 401, "Please login first");
            return false;
        }

        if (isAdmin(session)) {
            return true;
        }

        List<String> permissions = session.getPermissions() == null ? java.util.Collections.emptyList() : session.getPermissions();
        boolean allowed = Arrays.stream(annotation.value()).allMatch(p -> PermissionResolver.hasPermission(permissions, p));
        if (!allowed) {
            writeJsonResponse(response, 403, "No permission");
            return false;
        }

        logger.debug("permission granted for {} -> {}", session.getUsername(), Arrays.toString(annotation.value()));
        return true;
    }

    private void writeJsonResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        OBJECT_MAPPER.writeValue(response.getWriter(), Map.of("code", status, "message", message));
    }

    private boolean isAdmin(AuthSession session) {
        return "ADMIN".equalsIgnoreCase(session.getRoleCode());
    }
}
