package com.zjcxph.imgapi.interceptors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.common.Permissions;
import com.zjcxph.imgapi.entity.AuthUser;
import com.zjcxph.imgapi.mapper.AuthUserMapper;
import com.zjcxph.imgapi.security.TokenBlacklist;
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

    private static final Logger logger = LoggerFactory.getLogger(LoginInterceptor.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TokenBlacklist tokenBlacklist;
    private final AuthUserMapper authUserMapper;
    private final DeveloperModeService developerModeService;

    public LoginInterceptor(TokenBlacklist tokenBlacklist,
                            AuthUserMapper authUserMapper,
                            DeveloperModeService developerModeService) {
        this.tokenBlacklist = tokenBlacklist;
        this.authUserMapper = authUserMapper;
        this.developerModeService = developerModeService;
    }

    private String extractToken(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        return null;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = extractToken(request.getHeader("Authorization"));
        if (token == null || token.isBlank()) {
            return allowDeveloperModeOrReject(request, response, "请先登录", "missing_token");
        }

        try {
            String tokenType = JwtUtil.getTokenType(token);
            if (!JwtUtil.ACCESS_TOKEN_TYPE.equals(tokenType)) {
                return allowDeveloperModeOrReject(request, response, "Token 类型无效", "invalid_token_type");
            }

            String jti = JwtUtil.getJti(token);
            if (tokenBlacklist.isRevoked(jti)) {
                return allowDeveloperModeOrReject(request, response, "Token 已失效，请重新登录", "revoked_token");
            }

            AuthSession tokenSession = JwtUtil.parseToken(token);
            if (tokenSession.getId() == null || !StringUtils.hasText(tokenSession.getUsername())) {
                return allowDeveloperModeOrReject(request, response, "Token 用户信息无效", "invalid_token_user");
            }

            AuthUser currentUser = authUserMapper.findById(tokenSession.getId());
            if (currentUser == null || !StringUtils.hasText(currentUser.getUsername())
                    || !currentUser.getUsername().equals(tokenSession.getUsername())) {
                return allowDeveloperModeOrReject(request, response, "账号不存在或 Token 已失效", "missing_token_user");
            }
            if (!"active".equalsIgnoreCase(currentUser.getStatus())) {
                return allowDeveloperModeOrReject(request, response, "账号已被禁用", "disabled_user");
            }
            if (tokenSession.effectivePasswordVersion() != currentUser.effectivePasswordVersion()) {
                writeUnauthorized(response, "账号凭据已发生变化，请重新登录", "AUTH_CREDENTIAL_CHANGED");
                return false;
            }

            AuthSession session = toCurrentSession(currentUser);
            installSession(request, session);
            return true;
        } catch (Exception exception) {
            logger.debug("JWT verification failed: {}", exception.getMessage());
            return allowDeveloperModeOrReject(request, response, "Token 无效或已过期", "invalid_or_expired_token");
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }

    private boolean allowDeveloperModeOrReject(HttpServletRequest request,
                                               HttpServletResponse response,
                                               String unauthorizedMessage,
                                               String reason) throws Exception {
        if (!developerModeService.isEnabled()) {
            writeUnauthorized(response, unauthorizedMessage, "UNAUTHORIZED");
            return false;
        }

        AuthSession developerSession = createDeveloperSession();
        installSession(request, developerSession);
        request.setAttribute(DEVELOPER_MODE_ATTRIBUTE, Boolean.TRUE);
        request.setAttribute(DEVELOPER_MODE_REASON_ATTRIBUTE, reason);
        response.setHeader("X-MRR-Developer-Mode", "enabled");
        logger.warn(
                "Developer mode authentication bypass: method={}, path={}, remoteIp={}, reason={}",
                request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), reason);
        return true;
    }

    private void installSession(HttpServletRequest request, AuthSession session) {
        AuthContext.setCurrentUser(session);
        request.setAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE, session);
    }

    private AuthSession createDeveloperSession() {
        AuthSession session = new AuthSession();
        session.setId(1L);
        session.setUsername("dev");
        session.setDisplayName("Developer Mode");
        session.setRoleCode("ADMIN");
        session.setRoleName("Administrator");
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
}
