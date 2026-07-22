package com.zjcxph.imgapi.interceptors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.common.Permissions;
import com.zjcxph.imgapi.entity.AuthUser;
import com.zjcxph.imgapi.mapper.AuthUserMapper;
import com.zjcxph.imgapi.security.ApiAccessPolicy;
import com.zjcxph.imgapi.security.TokenBlacklist;
import com.zjcxph.imgapi.service.DeveloperApiAccessService;
import com.zjcxph.imgapi.service.DeveloperModeService;
import com.zjcxph.imgapi.utils.AuthContext;
import com.zjcxph.imgapi.utils.JwtUtil;
import com.zjcxph.imgapi.utils.PermissionResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public class LoginInterceptor implements HandlerInterceptor {

    public static final String DEVELOPER_MODE_ATTRIBUTE = "MRR_DEVELOPER_MODE";
    public static final String DEVELOPER_MODE_REASON_ATTRIBUTE = "MRR_DEVELOPER_MODE_REASON";
    public static final String ACCESS_MODE_ATTRIBUTE = "MRR_ACCESS_MODE";
    public static final String ARCHIVE_LEGACY_ACCESS_MODE = DeveloperModeService.ARCHIVE_LEGACY_ACCESS_MODE;
    public static final String API_FULL_ACCESS_MODE = DeveloperApiAccessService.API_FULL_ACCESS_MODE;

    private static final Logger logger = LoggerFactory.getLogger(LoginInterceptor.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<String> ARCHIVE_LEGACY_PERMISSIONS = List.of("record:read", "search:read");

    private final TokenBlacklist tokenBlacklist;
    private final AuthUserMapper authUserMapper;
    private final DeveloperModeService developerModeService;
    private final DeveloperApiAccessService developerApiAccessService;

    public LoginInterceptor(TokenBlacklist tokenBlacklist,
                            AuthUserMapper authUserMapper,
                            DeveloperModeService developerModeService,
                            DeveloperApiAccessService developerApiAccessService) {
        this.tokenBlacklist = tokenBlacklist;
        this.authUserMapper = authUserMapper;
        this.developerModeService = developerModeService;
        this.developerApiAccessService = developerApiAccessService;
    }

    private String extractToken(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        return null;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || isPublicAuthenticationRequest(request)) {
            return true;
        }

        String authorization = request.getHeader("Authorization");
        if (!StringUtils.hasText(authorization)) {
            return allowDeveloperModeOrReject(request, response);
        }

        String token = extractToken(authorization);
        if (!StringUtils.hasText(token)) {
            writeUnauthorized(response, "Authorization 格式无效", "INVALID_AUTHORIZATION_HEADER");
            return false;
        }

        AuthSession tokenSession;
        String jti;
        try {
            String tokenType = JwtUtil.getTokenType(token);
            if (!JwtUtil.ACCESS_TOKEN_TYPE.equals(tokenType)) {
                writeUnauthorized(response, "Token 类型无效", "INVALID_TOKEN_TYPE");
                return false;
            }
            jti = JwtUtil.getJti(token);
            tokenSession = JwtUtil.parseToken(token);
        } catch (Exception exception) {
            logger.debug("JWT verification failed: {}", exception.getMessage());
            writeUnauthorized(response, "Token 无效或已过期", "INVALID_OR_EXPIRED_TOKEN");
            return false;
        }

        try {
            if (tokenBlacklist.isRevoked(jti)) {
                writeUnauthorized(response, "Token 已失效，请重新登录", "REVOKED_TOKEN");
                return false;
            }
        } catch (Exception exception) {
            logger.error("Token revocation store unavailable", exception);
            writeServiceUnavailable(response, "认证状态服务暂时不可用，请稍后重试");
            return false;
        }

        if (tokenSession.getId() == null || !StringUtils.hasText(tokenSession.getUsername())) {
            writeUnauthorized(response, "Token 用户信息无效", "INVALID_TOKEN_USER");
            return false;
        }

        AuthUser currentUser;
        try {
            currentUser = authUserMapper.findById(tokenSession.getId());
        } catch (Exception exception) {
            logger.error("Authentication user store unavailable: userId={}", tokenSession.getId(), exception);
            writeServiceUnavailable(response, "认证用户服务暂时不可用，请稍后重试");
            return false;
        }

        if (currentUser == null || !StringUtils.hasText(currentUser.getUsername())
                || !currentUser.getUsername().equals(tokenSession.getUsername())) {
            writeUnauthorized(response, "账号不存在或 Token 已失效", "MISSING_TOKEN_USER");
            return false;
        }
        if (!"active".equalsIgnoreCase(currentUser.getStatus())) {
            writeUnauthorized(response, "账号已被禁用", "DISABLED_USER");
            return false;
        }
        if (tokenSession.effectivePasswordVersion() != currentUser.effectivePasswordVersion()) {
            writeUnauthorized(response, "账号凭据已发生变化，请重新登录", "AUTH_CREDENTIAL_CHANGED");
            return false;
        }

        AuthSession session = toCurrentSession(currentUser);
        installSession(request, session);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }

    private boolean isPublicAuthenticationRequest(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return ApiAccessPolicy.isAuthenticationExcluded(path)
                && ("/api/v1/auth/login".equals(path) || ApiAccessPolicy.REGISTRATION_PATH.equals(path));
    }

    private boolean allowDeveloperModeOrReject(HttpServletRequest request,
                                                HttpServletResponse response) throws Exception {
        if (developerApiAccessService.isRequestAllowed(request)) {
            installDeveloperSession(request, response, createFullApiDeveloperSession(),
                    "missing_token_api_full", API_FULL_ACCESS_MODE);
            return true;
        }

        if (developerModeService.isArchiveLegacyRequestAllowed(request)) {
            installDeveloperSession(request, response, createArchiveLegacySession(),
                    "missing_token_archive_legacy", ARCHIVE_LEGACY_ACCESS_MODE);
            return true;
        }

        writeUnauthorized(response, "请先登录", "UNAUTHORIZED");
        return false;
    }

    private void installDeveloperSession(HttpServletRequest request,
                                         HttpServletResponse response,
                                         AuthSession session,
                                         String reason,
                                         String accessMode) {
        installSession(request, session);
        request.setAttribute(DEVELOPER_MODE_ATTRIBUTE, Boolean.TRUE);
        request.setAttribute(DEVELOPER_MODE_REASON_ATTRIBUTE, reason);
        request.setAttribute(ACCESS_MODE_ATTRIBUTE, accessMode);
        response.setHeader("X-MRR-Developer-Mode", "enabled");
        response.setHeader("X-MRR-Access-Mode", accessMode);
        logger.warn("Developer mode access: mode={}, method={}, path={}, remoteIp={}",
                accessMode, request.getMethod(), request.getRequestURI(), request.getRemoteAddr());
    }

    private void installSession(HttpServletRequest request, AuthSession session) {
        AuthContext.setCurrentUser(session);
        request.setAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE, session);
    }

    private AuthSession createArchiveLegacySession() {
        AuthSession session = new AuthSession();
        session.setId(-1L);
        session.setUsername("developer-archive");
        session.setDisplayName("Developer Archive Legacy");
        session.setRoleCode("DEVELOPER_ARCHIVE");
        session.setRoleName("Archive Legacy Reader");
        session.setStatus("active");
        session.setMustChangePassword(false);
        session.setPasswordVersion(1);
        session.setPermissions(new ArrayList<>(PermissionResolver.resolve(ARCHIVE_LEGACY_PERMISSIONS)));
        return session;
    }

    private AuthSession createFullApiDeveloperSession() {
        AuthSession session = new AuthSession();
        session.setId(-2L);
        session.setUsername("developer-api");
        session.setDisplayName("Developer API");
        session.setRoleCode("DEVELOPER_API");
        session.setRoleName("Developer Full API");
        session.setStatus("active");
        session.setMustChangePassword(false);
        session.setPasswordVersion(1);
        session.setPermissions(new ArrayList<>(PermissionResolver.resolve(Permissions.ALL_PERMISSIONS)));
        return session;
    }

    private AuthSession toCurrentSession(AuthUser user) {
        AuthSession session = new AuthSession();
        session.setId(user.getId());
        session.setUsername(user.getUsername());
        session.setDisplayName(StringUtils.hasText(user.getDisplayName()) ? user.getDisplayName() : user.getUsername());
        session.setRoleCode(user.getRoleCode());
        session.setRoleName(StringUtils.hasText(user.getRoleName()) ? user.getRoleName() : user.getRoleCode());
        session.setStatus(user.getStatus());
        session.setMustChangePassword(user.isPasswordChangeRequired());
        session.setPasswordVersion(user.effectivePasswordVersion());
        session.setTemporaryPasswordExpiresAt(user.getTemporaryPasswordExpiresAt());
        session.setLastLoginAt(user.getLastLoginAt());
        session.setPermissions(resolvePermissions(user));
        return session;
    }

    private List<String> resolvePermissions(AuthUser user) {
        if ("ADMIN".equalsIgnoreCase(user.getRoleCode())) {
            return new ArrayList<>(PermissionResolver.resolve(Permissions.ALL_PERMISSIONS));
        }
        if (!StringUtils.hasText(user.getPermissionsCsv())) {
            return List.of();
        }
        List<String> configured = Arrays.stream(user.getPermissionsCsv().split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        return new ArrayList<>(PermissionResolver.resolve(configured));
    }

    private void writeUnauthorized(HttpServletResponse response, String message, String code) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        OBJECT_MAPPER.writeValue(response.getWriter(), Map.of("code", code, "message", message));
    }

    private void writeServiceUnavailable(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        OBJECT_MAPPER.writeValue(response.getWriter(), Map.of(
                "code", "AUTH_SERVICE_UNAVAILABLE", "message", message));
    }
}
