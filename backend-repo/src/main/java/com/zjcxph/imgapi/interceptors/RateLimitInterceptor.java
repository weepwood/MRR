package com.zjcxph.imgapi.interceptors;

import com.zjcxph.imgapi.security.ApiRateLimiter;
import com.zjcxph.imgapi.utils.IpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * API 限流拦截器 — 对敏感接口按 IP 进行频率限制。
 * <p>
 * 仅对 {@link #RATE_LIMITED_PATHS} 中列出的路径生效，
 * 其他请求直接放行（不影响普通 API 调用）。
 * </p>
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitInterceptor.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static final Set<String> EXACT_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/password/edit",
            "/api/v1/search/getBAHByEncryptID",
            "/api/v1/search/getBAHByEncryptIDLegacy",
            "/api/v1/search/getBAHByID",
            "/api/v1/oss/upload",
            "/api/v1/statistics/export/csv",
            "/api/v1/logs/retention/export",
            "/api/v1/monitoring/pressure-tests/run",
            "/api/v1/testing/api-test"
    );

    static final Set<String> PREFIX_PATHS = Set.of(
            "/api/v1/img/download",
            "/api/v1/scan/batch-download"
    );

    private final ApiRateLimiter apiRateLimiter;

    public RateLimitInterceptor(ApiRateLimiter apiRateLimiter) {
        this.apiRateLimiter = apiRateLimiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        if (!isRateLimited(path)) {
            return true;
        }

        String clientIp = IpUtil.getClientIp(request);
        if (!apiRateLimiter.tryAcquire(clientIp)) {
            writeRateLimitResponse(response);
            return false;
        }
        return true;
    }

    private boolean isRateLimited(String path) {
        return EXACT_PATHS.contains(path)
                || PREFIX_PATHS.stream().anyMatch(path::startsWith);
    }

    private void writeRateLimitResponse(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        OBJECT_MAPPER.writeValue(response.getWriter(), Map.of("code", 429, "message", "请求过于频繁，请稍后重试"));
    }
}
