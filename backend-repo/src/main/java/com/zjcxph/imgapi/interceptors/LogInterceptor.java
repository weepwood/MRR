package com.zjcxph.imgapi.interceptors;

import com.zjcxph.imgapi.common.ArchiveAccessAttributes;
import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.entity.Log;
import com.zjcxph.imgapi.service.AsyncLogService;
import com.zjcxph.imgapi.service.ReliableAuditService;
import com.zjcxph.imgapi.utils.AuthContext;
import com.zjcxph.imgapi.utils.IpUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;
import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class LogInterceptor implements HandlerInterceptor {

    private static final String AUTH_SESSION_ATTR = "AUTH_SESSION";
    private static final String START_TIME_ATTR = "startTime";
    private static final String REQUEST_ID_ATTR = "requestId";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String ERROR_CODE_HEADER = "X-Error-Code";
    private static final String ENDPOINT_TEMPLATE_HEADER = "X-Endpoint-Template";

    private final AsyncLogService asyncLogService;
    private final ReliableAuditService reliableAuditService;
    private final MeterRegistry meterRegistry;
    private final String auditHmacSecret;

    @Autowired
    public LogInterceptor(AsyncLogService asyncLogService,
                          ReliableAuditService reliableAuditService,
                          MeterRegistry meterRegistry,
                          @Value("${app.audit.hmac-secret:}") String auditHmacSecret) {
        this.asyncLogService = asyncLogService;
        this.reliableAuditService = reliableAuditService;
        this.meterRegistry = meterRegistry;
        this.auditHmacSecret = auditHmacSecret == null ? "" : auditHmacSecret;
    }

    /** Test-compatible constructor. Production uses the autowired constructor above. */
    public LogInterceptor(AsyncLogService asyncLogService, MeterRegistry meterRegistry) {
        this(asyncLogService, null, meterRegistry, "");
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        if (shouldSkipLogging(request, handler)) {
            return true;
        }

        String requestId = randomId();
        MDC.put("requestId", requestId);
        MDC.put("clientIp", IpUtil.getClientIp(request));

        String traceId = MDC.get("traceId");
        if (traceId == null || traceId.isBlank()) {
            traceId = requestId;
            MDC.put("traceId", traceId);
        }

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
    public void postHandle(@NonNull HttpServletRequest request,
                           @NonNull HttpServletResponse response,
                           @NonNull Object handler,
                           ModelAndView modelAndView) {
        Long startTime = (Long) request.getAttribute(START_TIME_ATTR);
        long duration = startTime == null ? 0 : Math.max(0, System.currentTimeMillis() - startTime);
        response.setHeader("Server-Timing", "app;dur=" + duration);
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler,
                                Exception ex) {
        if (shouldSkipLogging(request, handler)) {
            return;
        }

        try {
            Long startTime = (Long) request.getAttribute(START_TIME_ATTR);
            long executeTime = startTime != null ? System.currentTimeMillis() - startTime : 0;
            Log log = new Log();

            log.setEventId(randomId());
            log.setRequestId(stringAttribute(request, REQUEST_ID_ATTR));
            log.setTraceId(firstNonBlank(MDC.get("traceId"), log.getRequestId()));
            log.setErrorCode(response.getHeader(ERROR_CODE_HEADER));
            log.setAuditResult(response.getStatus() < 400 ? "SUCCESS" : "FAILED");

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
            if (stringAttribute(request, ArchiveAccessAttributes.AUDIT_TARGET) == null) {
                log.setAuditTarget(pseudonymizeAuditTarget(log.getAuditTarget()));
            }

            if (log.getAuditAction() != null && reliableAuditService != null) {
                reliableAuditService.persist(log);
                Counter.builder("mrr.audit.events.total")
                        .tag("action", log.getAuditAction())
                        .tag("result", log.getAuditResult())
                        .description("Security-sensitive audit events")
                        .register(meterRegistry)
                        .increment();
            } else {
                asyncLogService.saveLogAsync(log);
            }

            Counter.builder("http.requests.total")
                    .tag("method", request.getMethod())
                    .tag("status", String.valueOf(response.getStatus()))
                    .description("HTTP requests by method and status")
                    .register(meterRegistry)
                    .increment();
            Timer.builder("http.requests.duration")
                    .tag("method", request.getMethod())
                    .tag("uri", endpointTemplate(request))
                    .description("HTTP request duration")
                    .publishPercentileHistogram()
                    .register(meterRegistry)
                    .record(executeTime, TimeUnit.MILLISECONDS);
        } finally {
            clearRequestMdc();
        }
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
                        || "/livez".equals(uri)
                        || "/readyz".equals(uri)
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
        } else if (uri.startsWith("/api/v1/auth/password/edit")) {
            log.setAuditAction("CHANGE_PASSWORD");
            log.setAuditTarget(currentUserUsername(request));
            log.setAuditDescription("修改密码");
        } else if (uri.startsWith("/api/v1/oss/")
                && ("POST".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method))) {
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
        } else if (!uri.contains("/hello")) {
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
        String target = uri.startsWith("/api/v1/auth/users/")
                ? uri.substring("/api/v1/auth/users/".length()) : "list";
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
        String target = uri.startsWith("/api/v1/auth/roles/")
                ? uri.substring("/api/v1/auth/roles/".length()) : "all";
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
        return content.length == 0 ? "" : "[OMITTED " + content.length + " bytes]";
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
            if (!auditHmacSecret.isBlank()) {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(auditHmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                byte[] digest = mac.doFinal(target.getBytes(StandardCharsets.UTF_8));
                return "hmac256:" + HexFormat.of().formatHex(digest, 0, 16);
            }
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(target.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest, 0, 16);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to pseudonymize audit target", exception);
        }
    }

    private void clearRequestMdc() {
        MDC.remove("requestId");
        MDC.remove("clientIp");
        MDC.remove("userId");
        MDC.remove("userRole");
        MDC.remove("errorCode");
    }

    private String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String firstNonBlank(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback;
    }
}
