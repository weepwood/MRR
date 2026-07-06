package com.zjcxph.imgapi.interceptors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.common.Permissions;
import com.zjcxph.imgapi.security.TokenBlacklist;
import com.zjcxph.imgapi.utils.AuthContext;
import com.zjcxph.imgapi.utils.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * 登录拦截器 — 开发模式（dev-no-login 分支）。
 *
 * <p><b>重要：本拦截器当前屏蔽了 JWT Token 验证。</b></p>
 *
 * <p>
 * 在 dev-no-login 分支中，该拦截器不再校验客户端传递的 Bearer Token，
 * 而是直接注入一个具备 ADMIN 角色和全部权限的虚拟开发会话。
 * 这意味着所有 API 请求均以管理员身份通过，无需登录。
 * </p>
 *
 * <p>注入的虚拟会话属性：</p>
 * <ul>
 *   <li>userId: 1 (dev)</li>
 *   <li>username: "dev"</li>
 *   <li>roleCode: "ADMIN"</li>
 *   <li>permissions: ALL_PERMISSIONS（全部权限）</li>
 * </ul>
 *
 * <p>
 * 恢复登录验证：将本拦截器的 preHandle 逻辑改为从 Authorization Header
 * 提取并验证 JWT Token，将解析后的用户信息注入 AuthSession。
 * 原始 JWT 验证逻辑参见该文件的 git history（本分支之前）。
 * </p>
 *
 * @see AuthorizationInterceptor 后续鉴权拦截器
 * @see AuthContext 线程级用户会话
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(LoginInterceptor.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TokenBlacklist tokenBlacklist;

    public LoginInterceptor(TokenBlacklist tokenBlacklist) {
        this.tokenBlacklist = tokenBlacklist;
    }

    private String extractToken(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // ============================================================
        // dev-no-login 模式：跳过 Token 校验，注入虚拟开发管理员会话
        // 如需恢复登录验证，用 JwtUtil 校验 Authorization Header 中的 Bearer Token
        // ============================================================
        AuthSession session = new AuthSession();
        session.setId(1L);
        session.setUsername("dev");
        session.setDisplayName("Dev User");
        session.setRoleCode("ADMIN");
        session.setRoleName("Administrator");
        session.setStatus("active");
        session.setPermissions(Permissions.ALL_PERMISSIONS);
        AuthContext.setCurrentUser(session);
        request.setAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE, session);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(401);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        OBJECT_MAPPER.writeValue(response.getWriter(), Map.of("code", 401, "message", message));
    }
}
