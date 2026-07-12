package com.zjcxph.imgapi.interceptors;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.entity.Log;
import com.zjcxph.imgapi.interceptors.AuthorizationInterceptor;
import com.zjcxph.imgapi.service.AsyncLogService;
import com.zjcxph.imgapi.utils.AuthContext;
import com.zjcxph.imgapi.utils.IpUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class LogInterceptor implements HandlerInterceptor {

    private static final String AUTH_SESSION_ATTR = "AUTH_SESSION";
    private static final String START_TIME_ATTR = "startTime";
    private static final String REQUEST_ID_ATTR = "requestId";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String ENDPOINT_TEMPLATE_HEADER = "X-Endpoint-Template";

    private final AsyncLogService asyncLogService;
    private final MeterRegistry meterRegistry;

    public LogInterceptor(AsyncLogService asyncLogService, MeterRegistry meterRegistry) {
        this.asyncLogService = asyncLogService;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        if (shouldSkipLogging(request, handler)) {
            return true;
        }

        // 生成请求ID并设置到 MDC 上下文，用于日志追踪
        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        MDC.put("requestId", requestId);
        MDC.put("clientIp", IpUtil.getClientIp(request));
        
        // 从请求头或 session 中获取用户信息（如果已认证）
        String userId = request.getHeader("X-User-Id");
        String userRole = request.getHeader("X-User-Role");
        if (userId != null && !userId.isEmpty()) {
            MDC.put("userId", userId);
        }
        if (userRole != null && !userRole.isEmpty()) {
            MDC.put("userRole", userRole);
        }

        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        request.setAttribute(REQUEST_ID_ATTR, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        response.setHeader(ENDPOINT_TEMPLATE_HEADER, endpointTemplate(request));
        return true;
    }

    @Override
    public void postHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, ModelAndView modelAndView) {
        Long startTime = (Long) request.getAttribute(START_TIME_ATTR);
        long duration = startTime == null ? 0 : Math.max(0, System.currentTimeMillis() - startTime);
        response.setHeader("Server-Timing", "app;dur=" + duration);
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, Exception ex) {
        if (shouldSkipLogging(request, handler)) {
            return;
        }

        Long startTime = (Long) request.getAttribute(START_TIME_ATTR);
        long executeTime = startTime != null ? System.currentTimeMillis() - startTime : 0;

        Log log = new Log();

        // 捕获当前登录用户：优先从 request attribute 获取（LoginInterceptor 设置），ThreadLocal 兜底
        AuthSession currentUser = (AuthSession) request.getAttribute(AUTH_SESSION_ATTR);
        if (currentUser == null) {
            currentUser = AuthContext.getCurrentUser();
        }
        if (currentUser != null && currentUser.getUsername() != null) {
            log.setUsername(currentUser.getUsername());
        }

        log.setClientIp(IpUtil.getClientIp(request));
        log.setRequestUri(endpointTemplate(request));
        log.setMethod(request.getMethod());
        log.setUserAgent(request.getHeader("User-Agent"));
        log.setAccessTime(new Date());
        log.setQueryString(redactQueryString(request.getQueryString()));
        log.setRequestBody(getRequestBody(request));
        log.setResponseStatus(String.valueOf(response.getStatus()));
        log.setExecuteTime(executeTime);
        log.setReferer(request.getHeader("Referer") == null ? null : "[REDACTED]");

        enrichAuditFields(log, request);
        log.setAuditTarget(pseudonymizeAuditTarget(log.getAuditTarget()));

        // 异步保存日志,不阻塞请求响应
        asyncLogService.saveLogAsync(log);

        // Micrometer 指标：请求计数 + 耗时分布
        Counter.builder("http.requests.total")
                .tag("method", request.getMethod())
                .tag("status", String.valueOf(response.getStatus()))
                .description("HTTP 请求总数（按方法和状态码）")
                .register(meterRegistry)
                .increment();
        Timer.builder("http.requests.duration")
                .tag("method", request.getMethod())
                .tag("uri", endpointTemplate(request))
                .description("HTTP 请求耗时分布")
                .register(meterRegistry)
                .record(executeTime, java.util.concurrent.TimeUnit.MILLISECONDS);
        
        // 清理 MDC 上下文，防止内存泄漏
        MDC.clear();
    }

    private String endpointTemplate(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern == null ? "UNKNOWN" : pattern.toString();
    }

    private boolean shouldSkipLogging(HttpServletRequest request, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (handler instanceof ResourceHttpRequestHandler) {
            return true;
        }

        String uri = request.getRequestURI();
        return uri != null && (
                uri.startsWith("/docs/")
                        || uri.startsWith("/swagger-ui/")
                        || uri.startsWith("/v3/api-docs/")
                        || uri.startsWith("/actuator/")
                        || "/favicon.ico".equals(uri)
                        || "/error".equals(uri)
        );
    }

    /**
     * 根据请求 URI 和方法 enrich 审计字段（auditAction/auditTarget/auditDescription）。
     * 覆盖：用户管理、角色管理、权限变更、图片访问等敏感操作。
     */
    private void enrichAuditFields(Log log, HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        if (uri == null) {
            return;
        }

        if (uri.startsWith("/api/v1/img/")) {
            enrichImageAudit(log, uri);
        } else if (uri.startsWith("/api/v1/auth/users")) {
            enrichUserManagementAudit(log, uri, method);
        } else if (uri.startsWith("/api/v1/auth/roles")) {
            enrichRoleManagementAudit(log, uri, method);
        } else if (uri.startsWith("/api/v1/auth/password/edit")) {
            log.setAuditAction("CHANGE_PASSWORD");
            log.setAuditTarget(currentUserUsername(request));
            log.setAuditDescription("修改密码");
        } else if (uri.startsWith("/api/v1/oss/") && ("POST".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method))) {
            enrichOssAudit(log, uri, method);
        }
    }

    private void enrichImageAudit(Log log, String uri) {
        String[] parts = uri.split("/");
        if (uri.contains("/download/")) {
            log.setAuditAction("DOWNLOAD");
            log.setAuditTarget(uri.substring(uri.lastIndexOf('/') + 1));
            log.setAuditDescription("下载病案图片压缩包");
        } else if (uri.contains("/oss-image/")) {
            log.setAuditAction("VIEW_OSS_IMAGE");
            log.setAuditTarget(uri.substring(uri.lastIndexOf('/') + 1));
            log.setAuditDescription("查看 OSS 病案图片");
        } else if (uri.startsWith("/api/v1/img/image/")) {
            log.setAuditAction("VIEW_IMAGE");
            log.setAuditTarget(parts.length > 5 ? parts[5] : uri);
            log.setAuditDescription("查看本地病案图片");
        } else if (uri.startsWith("/api/v1/img/") && !uri.contains("/hello")) {
            log.setAuditAction("LIST");
            log.setAuditTarget(uri.substring(uri.lastIndexOf('/') + 1));
            log.setAuditDescription("查询病案图片列表");
        }
    }

    private void enrichUserManagementAudit(Log log, String uri, String method) {
        String target = uri.startsWith("/api/v1/auth/users/") ? uri.substring("/api/v1/auth/users/".length()) : "list";
        if ("DELETE".equalsIgnoreCase(method)) {
            log.setAuditAction("DISABLE_USER");
            log.setAuditTarget(target);
            log.setAuditDescription("禁用用户");
        } else if ("PUT".equalsIgnoreCase(method)) {
            log.setAuditAction("UPDATE_USER");
            log.setAuditTarget(target);
            log.setAuditDescription("更新用户信息");
        } else if ("GET".equalsIgnoreCase(method)) {
            log.setAuditAction("LIST_USERS");
            log.setAuditTarget("all");
            log.setAuditDescription("查询用户列表");
        }
    }

    private void enrichRoleManagementAudit(Log log, String uri, String method) {
        String target = uri.startsWith("/api/v1/auth/roles/") ? uri.substring("/api/v1/auth/roles/".length()) : "all";
        if ("PUT".equalsIgnoreCase(method)) {
            log.setAuditAction("UPDATE_ROLE");
            log.setAuditTarget(target);
            log.setAuditDescription("更新角色权限配置");
        } else if ("GET".equalsIgnoreCase(method)) {
            log.setAuditAction("LIST_ROLES");
            log.setAuditTarget("all");
            log.setAuditDescription("查询角色列表");
        }
    }

    private void enrichOssAudit(Log log, String uri, String method) {
        if ("DELETE".equalsIgnoreCase(method)) {
            log.setAuditAction("DELETE_OSS_OBJECT");
            log.setAuditTarget(uri.substring(uri.lastIndexOf('/') + 1));
            log.setAuditDescription("删除 OSS 对象");
        } else if (uri.contains("/upload")) {
            log.setAuditAction("OSS_UPLOAD");
            log.setAuditTarget(uri);
            log.setAuditDescription("上传图片到 OSS");
        }
    }

    private String currentUserUsername(HttpServletRequest request) {
        AuthSession session = (AuthSession) request.getAttribute(AUTH_SESSION_ATTR);
        return session != null ? session.getUsername() : "unknown";
    }

    /**
     * 从 ContentCachingRequestWrapper 中读取请求体
     */
    private String getRequestBody(HttpServletRequest request) {
        if (!(request instanceof ContentCachingRequestWrapper)) {
            return "";
        }

        ContentCachingRequestWrapper wrapper = (ContentCachingRequestWrapper) request;
        byte[] content = wrapper.getContentAsByteArray();

        if (content.length == 0) {
            return "";
        }

        return "[OMITTED " + content.length + " bytes]";
    }

    private String redactQueryString(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return queryString;
        }
        return Stream.of(queryString.split("&", -1))
                .map(parameter -> {
                    int separator = parameter.indexOf('=');
                    String name = separator < 0 ? parameter : parameter.substring(0, separator);
                    return name + "=[REDACTED]";
                })
                .collect(Collectors.joining("&"));
    }

    private String pseudonymizeAuditTarget(String target) {
        if (target == null || target.isBlank()) {
            return target;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(target.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
