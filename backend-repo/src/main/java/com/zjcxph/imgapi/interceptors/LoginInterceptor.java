package com.zjcxph.imgapi.interceptors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.security.TokenBlacklist;
import com.zjcxph.imgapi.utils.AuthContext;
import com.zjcxph.imgapi.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

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

    public LoginInterceptor(TokenBlacklist tokenBlacklist) {
        this.tokenBlacklist = tokenBlacklist;
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

            AuthSession session = JwtUtil.parseToken(token);
            if (session.getUsername() == null || session.getUsername().isBlank()) {
                writeUnauthorized(response, "Token 用户信息无效");
                return false;
            }
            if (session.getStatus() != null && !"active".equalsIgnoreCase(session.getStatus())) {
                writeUnauthorized(response, "账号已被禁用");
                return false;
            }

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

    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        OBJECT_MAPPER.writeValue(response.getWriter(), Map.of("code", 401, "message", message));
    }
}
