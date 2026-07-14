package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.utils.JwtUtil;
import com.zjcxph.imgapi.utils.PermissionResolver;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentationAccessService {

    public static final String COOKIE_NAME = "MRR_DOCS_ACCESS";
    public static final long SESSION_EXPIRE_SECONDS = 30L * 60L;

    private static final List<String> ALLOWED_PREFIXES = List.of(
            "/docs",
            "/api-docs",
            "/swagger-ui",
            "/swagger-ui.html",
            "/v3/api-docs",
            "/v3/api-docs.yaml"
    );

    private static final List<String> INTERNAL_PREFIXES = List.of(
            "/docs/internal",
            "/api-docs",
            "/swagger-ui",
            "/swagger-ui.html",
            "/v3/api-docs",
            "/v3/api-docs.yaml"
    );

    public enum AccessDecision {
        ALLOWED,
        UNAUTHORIZED,
        FORBIDDEN,
        INVALID_TARGET
    }

    public String issueToken(AuthSession session) {
        if (session == null) {
            throw new IllegalArgumentException("session must not be null");
        }
        return JwtUtil.getToken(
                session,
                SESSION_EXPIRE_SECONDS * 1000L,
                JwtUtil.DOCUMENTATION_TOKEN_TYPE
        );
    }

    public AccessDecision authorizeSession(AuthSession session, String target) {
        String normalizedTarget = normalizeTarget(target);
        if (!matchesAnyPrefix(normalizedTarget, ALLOWED_PREFIXES)) {
            return AccessDecision.INVALID_TARGET;
        }
        if (session == null || session.getUsername() == null || session.getUsername().isBlank()) {
            return AccessDecision.UNAUTHORIZED;
        }
        if (session.getStatus() != null && !session.getStatus().isBlank()
                && !"active".equalsIgnoreCase(session.getStatus())) {
            return AccessDecision.UNAUTHORIZED;
        }
        if (!matchesAnyPrefix(normalizedTarget, INTERNAL_PREFIXES)) {
            return AccessDecision.ALLOWED;
        }
        if ("ADMIN".equalsIgnoreCase(session.getRoleCode())) {
            return AccessDecision.ALLOWED;
        }
        return PermissionResolver.hasPermission(session.getPermissions(), "system:read")
                ? AccessDecision.ALLOWED
                : AccessDecision.FORBIDDEN;
    }

    public AccessDecision authorizeToken(String token, String target) {
        if (token == null || token.isBlank()) {
            return AccessDecision.UNAUTHORIZED;
        }
        try {
            if (!JwtUtil.DOCUMENTATION_TOKEN_TYPE.equals(JwtUtil.getTokenType(token))) {
                return AccessDecision.UNAUTHORIZED;
            }
            return authorizeSession(JwtUtil.parseToken(token), target);
        } catch (RuntimeException ex) {
            return AccessDecision.UNAUTHORIZED;
        }
    }

    public String findAccessToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public String normalizeTarget(String target) {
        if (target == null || target.isBlank()) {
            return "/docs/";
        }
        int queryIndex = target.indexOf('?');
        String path = queryIndex >= 0 ? target.substring(0, queryIndex) : target;
        return path.startsWith("/") ? path : "/" + path;
    }

    private boolean matchesAnyPrefix(String target, List<String> prefixes) {
        return prefixes.stream().anyMatch(prefix -> matchesPrefix(target, prefix));
    }

    private boolean matchesPrefix(String target, String prefix) {
        return target.equals(prefix) || target.startsWith(prefix + "/");
    }
}
