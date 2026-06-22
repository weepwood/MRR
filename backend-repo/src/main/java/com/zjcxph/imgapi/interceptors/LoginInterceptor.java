package com.zjcxph.imgapi.interceptors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.common.AuthSession;
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

        String authorization = extractToken(request.getHeader("Authorization"));
        if (authorization == null) {
            writeUnauthorized(response, "missing token");
            return false;
        }

        try {
            // 检查是否在黑名单中
            if (tokenBlacklist.isRevoked(JwtUtil.getJti(authorization))) {
                writeUnauthorized(response, "token has been revoked");
                return false;
            }

            AuthSession session = JwtUtil.parseToken(authorization);
            AuthContext.setCurrentUser(session);
            request.setAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE, session);
            return true;
        } catch (Exception e) {
            logger.error("token invalid: {}", String.valueOf(e));
            AuthContext.clear();
            writeUnauthorized(response, "token invalid");
            return false;
        }
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
