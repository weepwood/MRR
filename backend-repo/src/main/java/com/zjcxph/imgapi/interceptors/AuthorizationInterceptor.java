package com.zjcxph.imgapi.interceptors;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.AuthSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
public class AuthorizationInterceptor implements HandlerInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(AuthorizationInterceptor.class);
    public static final String AUTH_SESSION_ATTRIBUTE = "AUTH_SESSION";

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
            unauthorized(response, "Please login first");
            return false;
        }

        if (isAdmin(session)) {
            return true;
        }

        List<String> permissions = session.getPermissions() == null ? java.util.Collections.<String>emptyList() : session.getPermissions();
        boolean allowed = Arrays.stream(annotation.value()).allMatch(permissions::contains);
        if (!allowed) {
            forbidden(response, "No permission");
            return false;
        }

        logger.debug("permission granted for {} -> {}", session.getUsername(), Arrays.toString(annotation.value()));
        return true;
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"" + message + "\"}");
    }

    private void forbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(403);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":403,\"message\":\"" + message + "\"}");
    }

    private boolean isAdmin(AuthSession session) {
        if ("ADMIN".equalsIgnoreCase(session.getRoleCode())) {
            return true;
        }
        List<String> permissions = session.getPermissions();
        return permissions != null && (permissions.contains("user:manage") || permissions.contains("role:manage"));
    }
}
