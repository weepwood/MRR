package com.zjcxph.imgapi.interceptors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.common.Permissions;
import com.zjcxph.imgapi.entity.AuthUser;
import com.zjcxph.imgapi.mapper.AuthUserMapper;
import com.zjcxph.imgapi.security.TokenBlacklist;
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

/**
 * 登录拦截器。
 *
 * <p>除显式公开接口外，所有请求都必须携带有效的 Bearer access token。</p>
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(LoginInterceptor.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TokenBlacklist tokenBlacklist;
    private final AuthUserMapper authUserMapper;

    public LoginInterceptor(TokenBlacklist tokenBlacklist, AuthUserMapper authUserMapper) {
        this.tokenBlacklist = tokenBlacklist;
        this.authUserMapper = authUserMapper;
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
            writeUnauthorized(response, "请先登录");
            return false;
        }

        try {
            String tokenType = JwtUtil.getTokenType(token);
            if (!JwtUtil.ACCESS_TOKEN_TYPE.equals(tokenType)) {
                writeUnauthorized(response, "Token 类型无效");
                return false;
            }

            String jti = JwtUtil.getJti(token);
            if (tokenBlacklist.isRevoked(jti)) {
                writeUnauthorized(response, "Token 已失效，请重新登录");
                return false;
            }

            AuthSession tokenSession = JwtUtil.parseToken(token);
            if (tokenSession.getId() == null || !StringUtils.hasText(tokenSession.getUsername())) {
                writeUnauthorized(response, "Token 用户信息无效");
                return false;
            }

            AuthUser currentUser = authUserMapper.findById(tokenSession.getId());
            if (currentUser == null || !StringUtils.hasText(currentUser.getUsername())
                    || !currentUser.getUsername().equals(tokenSession.getUsername())) {
                writeUnauthorized(response, "账号不存在或 Token 已失效");
                return false;
            }
            if (!"active".equalsIgnoreCase(currentUser.getStatus())) {
                writeUnauthorized(response, "账号已被禁用");
                return false;
            }

            AuthSession session = toCurrentSession(currentUser);
            AuthContext.setCurrentUser(session);
            request.setAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE, session);
            return true;
        } catch (Exception exception) {
            logger.debug("JWT verification failed: {}", exception.getMessage());
            writeUnauthorized(response, "Token 无效或已过期");
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }

    private AuthSession toCurrentSession(AuthUser user) {
        AuthSession session = new AuthSession();
        session.setId(user.getId());
        session.setUsername(user.getUsername());
        session.setDisplayName(StringUtils.hasText(user.getDisplayName()) ? user.getDisplayName() : user.getUsername());
        session.setRoleCode(user.getRoleCode());
        session.setRoleName(StringUtils.hasText(user.getRoleName()) ? user.getRoleName() : user.getRoleCode());
        session.setStatus(user.getStatus());
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

    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        OBJECT_MAPPER.writeValue(response.getWriter(), Map.of("code", 401, "message", message));
    }
}
