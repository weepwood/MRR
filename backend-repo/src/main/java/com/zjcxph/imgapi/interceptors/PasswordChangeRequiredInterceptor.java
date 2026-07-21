package com.zjcxph.imgapi.interceptors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.utils.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.Set;

@Component
public class PasswordChangeRequiredInterceptor implements HandlerInterceptor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> ALLOWED_PATHS = Set.of(
            "/api/v1/auth/me",
            "/api/v1/auth/password/required-change",
            "/api/v1/auth/logout"
    );

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        AuthSession session = AuthContext.getCurrentUser();
        if (session == null || !session.isPasswordChangeRequired()) {
            return true;
        }
        if (ALLOWED_PATHS.contains(request.getRequestURI())) {
            return true;
        }

        response.setStatus(428);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        OBJECT_MAPPER.writeValue(response.getWriter(), Map.of(
                "code", "AUTH_PASSWORD_CHANGE_REQUIRED",
                "message", "首次登录或密码已被管理员重置，请先修改密码"
        ));
        return false;
    }
}
