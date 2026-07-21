package com.zjcxph.imgapi.interceptors;

import com.zjcxph.imgapi.common.ArchiveAccessAttributes;
import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.entity.Log;
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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class LogInterceptor implements HandlerInterceptor {

    private static final String AUTH_SESSION_ATTR = "AUTH_SESSION";
    private static final String START_TIME_ATTR = "startTime";
    private static final String REQUEST_ID_ATTR = "requestId";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String ENDPOINT_TEMPLATE_HEADER = "X-Endpoint-Template";
    private static final int MAX_REFERER_LENGTH = 4096;
    private static final int MAX_REQUEST_BODY_LENGTH = 16384;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 2048;

    private static final Set<String> SENSITIVE_PARAMETER_NAMES = Set.of(
            "password", "oldpassword", "newpassword", "confirmpassword",
            "token", "accesstoken", "refreshtoken", "authorization",
            "secret", "clientsecret", "signature", "sign", "ticket",
            "apikey", "api_key", "privatekey", "private_key"
    );

    private static final Pattern JSON_STRING_FIELD = Pattern.compile(
            "(\"([^\"]+)\"\\s*:\\s*\")([^\"]*)(\")"
    );

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

        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        MDC.put("requestId", requestId);
        MDC.put("clientIp", IpUtil.getClientIp(request));

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

        String archiveUserId = stringAttribute(request, ArchiveAccessAttributes.USER_ID);
        if (archiveUserId != null) {
            log.setUsername(archiveUserId);
        } else {
            AuthSession currentUser = (AuthSession) request.getAttribute(AUTH_SESSION_ATTR);
            if (currentUser == null) {
                currentUser = AuthContext.getCurrentUser();
            }
            if (currentUser != null && currentUser.getUsername() != null) {
                log.setUsername(currentUser.getUsername());
            }
        }

        log.setRequestId(stringAttribute(request, REQUEST_ID_ATTR));
        log.setClientIp(IpUtil.getClientIp(request));
        log.setRequestUri(request.getRequestURI());
        log.setEndpointTemplate(endpointTemplate(request));
        log.setMethod(request.getMethod());
        log.setUserAgent(truncate(request.getHeader("User-Agent"), MAX_REFERER_LENGTH));
        log.setAccessTime(new Date());
        log.setQueryString(sanitizeQueryString(request.getQueryString()));
        log.setRequestBody(getRequestBody(request));
        log.setResponseStatus(String.valueOf(response.getStatus()));
        log.setExecuteTime(executeTime);
        log.setReferer(sanitizeReferer(request.getHeader("Referer")));
        log.setErrorMessage(formatError(ex));

        enrichAuditFields(log, request);
        asyncLogService.saveLogAsync(log);

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

    private void enrichAuditFields(Log log, HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        if (uri == null) {
            return;
        }

        if (uri.startsWith("/api/v1/img/")) {
            enrichImageAudit(log, request, uri);
        } else if (uri.startsWith("/api/v1/auth/users")) {
            enrichUserManagementAudit(log, uri, method);
        } else if (uri.startsWith("/api/v1/auth/roles")) {
            enrichRoleManagementAudit(log, uri, method);
        } else if (uri.startsWith("/api/v1/auth/password/required-change")) {
            log.setAuditAction("USER_FIRST_PASSWORD_CHANGED");
            log.setAuditTarget(currentUserUsername(request));
            log.setAuditDescription("首次登录或管理员重置后修改密码");
        } else if (uri.startsWith("/api/v1/auth/password/edit")) {
            log.setAuditAction("USER_PASSWORD_CHANGED");
            log.setAuditTarget(currentUserUsername(request));
            log.setAuditDescription("修改当前用户密码");
        } else if (uri.startsWith("/api/v1/oss/") && ("POST".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method))) {
            enrichOssAudit(log, uri, method);
        }
    }

    private void enrichImageAudit(Log log, HttpServletRequest request, String uri) {
        String[] parts = uri.split("/");
        String archiveTarget = stringAttribute(request, ArchiveAccessAttributes.AUDIT_TARGET);
        String ipAuditNote = stringAttribute(request, ArchiveAccessAttributes.IP_AUDIT_NOTE);

        if (uri.contains("/download/")) {
            log.setAuditAction("DOWNLOAD");
            log.setAuditTarget(archiveTarget != null ? archiveTarget : uri.substring(uri.lastIndexOf('/') + 1));
            log.setAuditDescription(withIpAuditNote("下载病案图片压缩包", ipAuditNote));
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
            log.setAuditTarget(archiveTarget != null ? archiveTarget : uri.substring(uri.lastIndexOf('/') + 1));
            log.setAuditDescription(withIpAuditNote("查询病案图片列表", ipAuditNote));
        }
    }

    private String withIpAuditNote(String description, String note) {
        return note == null ? description : description + "；" + note;
    }

    private String stringAttribute(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private void enrichUserManagementAudit(Log log, String uri, String method) {
        String base = "/api/v1/auth/users";
        String suffix = uri.length() > base.length() ? uri.substring(base.length()) : "";

        if ("POST".equalsIgnoreCase(method) && suffix.isEmpty()) {
            log.setAuditAction("USER_CREATED");
            log.setAuditTarget("new-user");
            log.setAuditDescription("管理员创建用户");
            return;
        }

        if ("POST".equalsIgnoreCase(method) && suffix.endsWith("/password/reset")) {
            String target = suffix.substring(1, suffix.length() - "/password/reset".length());
            log.setAuditAction("USER_PASSWORD_RESET");
            log.setAuditTarget(target);
            log.setAuditDescription("管理员重置用户密码");
            return;
        }

        String target = suffix.startsWith("/") ? suffix.substring(1) : "list";
        if ("DELETE".equalsIgnoreCase(method)) {
            log.setAuditAction("USER_DISABLED");
            log.setAuditTarget(target);
            log.setAuditDescription("禁用用户");
        } else if ("PUT".equalsIgnoreCase(method)) {
            log.setAuditAction("USER_UPDATED");
            log.setAuditTarget(target);
            log.setAuditDescription("更新用户信息、角色或状态");
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

    private String getRequestBody(HttpServletRequest request) {
        if (!(request instanceof ContentCachingRequestWrapper wrapper)) {
            return "";
        }

        byte[] content = wrapper.getContentAsByteArray();
        if (content.length == 0) {
            return "";
        }

        String contentType = request.getContentType();
        String normalizedContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (normalizedContentType.startsWith("multipart/")
                || normalizedContentType.contains("application/octet-stream")
                || normalizedContentType.startsWith("image/")
                || normalizedContentType.startsWith("audio/")
                || normalizedContentType.startsWith("video/")) {
            return "[BINARY OMITTED " + content.length + " bytes; content-type=" + normalizedContentType + "]";
        }

        String body = new String(content, StandardCharsets.UTF_8);
        if (normalizedContentType.contains("application/x-www-form-urlencoded")) {
            return truncate(sanitizeQueryString(body), MAX_REQUEST_BODY_LENGTH);
        }
        return truncate(sanitizeJsonStringFields(body), MAX_REQUEST_BODY_LENGTH);
    }

    private String sanitizeQueryString(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return queryString;
        }
        return Stream.of(queryString.split("&", -1))
                .map(parameter -> {
                    int separator = parameter.indexOf('=');
                    String rawName = separator < 0 ? parameter : parameter.substring(0, separator);
                    if (separator < 0 || !isSensitiveName(decodeParameterName(rawName))) {
                        return parameter;
                    }
                    String rawValue = parameter.substring(separator + 1);
                    return rawName + "=" + hashForAudit(rawValue);
                })
                .collect(Collectors.joining("&"));
    }

    private String sanitizeReferer(String referer) {
        if (referer == null || referer.isBlank()) {
            return referer;
        }
        int fragmentIndex = referer.indexOf('#');
        String withoutFragment = fragmentIndex >= 0 ? referer.substring(0, fragmentIndex) : referer;
        int queryIndex = withoutFragment.indexOf('?');
        if (queryIndex < 0) {
            return truncate(withoutFragment, MAX_REFERER_LENGTH);
        }
        String base = withoutFragment.substring(0, queryIndex);
        String query = withoutFragment.substring(queryIndex + 1);
        return truncate(base + "?" + sanitizeQueryString(query), MAX_REFERER_LENGTH);
    }

    private String sanitizeJsonStringFields(String body) {
        Matcher matcher = JSON_STRING_FIELD.matcher(body);
        StringBuffer sanitized = new StringBuffer();
        while (matcher.find()) {
            if (!isSensitiveName(matcher.group(2))) {
                matcher.appendReplacement(sanitized, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            String replacement = matcher.group(1) + hashForAudit(matcher.group(3)) + matcher.group(4);
            matcher.appendReplacement(sanitized, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sanitized);
        return sanitized.toString();
    }

    private String decodeParameterName(String name) {
        try {
            return URLDecoder.decode(name, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return name;
        }
    }

    private boolean isSensitiveName(String name) {
        if (name == null) {
            return false;
        }
        String normalized = name.toLowerCase(Locale.ROOT)
                .replace("-", "")
                .replace("_", "")
                .replace("[", "")
                .replace("]", "");
        return SENSITIVE_PARAMETER_NAMES.contains(normalized)
                || normalized.endsWith("password")
                || normalized.endsWith("token")
                || normalized.endsWith("secret")
                || normalized.endsWith("signature");
    }

    private String formatError(Exception exception) {
        if (exception == null) {
            return null;
        }
        String message = exception.getClass().getName();
        if (exception.getMessage() != null && !exception.getMessage().isBlank()) {
            message += ": " + exception.getMessage();
        }
        return truncate(message, MAX_ERROR_MESSAGE_LENGTH);
    }

    private String hashForAudit(String value) {
        if (value == null || value.isBlank()) {
            return "[EMPTY]";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...[TRUNCATED]";
    }
}
