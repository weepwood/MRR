package com.zjcxph.imgapi.interceptors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.common.ArchiveAccessAttributes;
import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.entity.Log;
import com.zjcxph.imgapi.service.AsyncLogService;
import com.zjcxph.imgapi.setting.SettingDefinition;
import com.zjcxph.imgapi.setting.SettingRegistry;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class LogInterceptor implements HandlerInterceptor {

    /** MDC 键名，与 {@code logback-spring.xml} 的 {@code includeMdcKeyName} 一一对应。 */
    public static final String MDC_USER_ID = "userId";
    public static final String MDC_USER_ROLE = "userRole";

    private static final String AUTH_SESSION_ATTR = "AUTH_SESSION";
    private static final String START_TIME_ATTR = "startTime";
    private static final String REQUEST_ID_ATTR = "requestId";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String ENDPOINT_TEMPLATE_HEADER = "X-Endpoint-Template";
    private static final String SETTINGS_PATH = "/api/v1/settings";
    private static final String SETTINGS_KEY_PREFIX = "/api/v1/settings/";
    private static final String DATA_EXCHANGE_PREFIX = "/api/v1/data-exchange";
    private static final String DATA_EXCHANGE_EXPORT_MARKER = "/export/";
    private static final int MAX_REFERER_LENGTH = 4096;
    private static final int MAX_REQUEST_BODY_LENGTH = 16384;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 2048;
    private static final int MAX_AUDIT_TARGET_LENGTH = 512;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 需要脱敏的字段名（比较前会去掉 {@code -}、{@code _}、{@code []} 并转小写）。
     *
     * <p>身份证明与联系方式类字段必须在此登记：{@code idCard} 曾以明文写入
     * {@code app.access_log.request_body}，并随数据库备份、导出与保留期长期留存，
     * 违反项目隐私红线。注意不要加入语义有歧义的短名（例如 {@code name}、{@code sjh}），
     * 它们在部分 DTO 中表示角色名或上架号，脱敏后反而会降低审计可用性。</p>
     */
    private static final Set<String> SENSITIVE_PARAMETER_NAMES = Set.of(
            "password", "oldpassword", "newpassword", "confirmpassword",
            "token", "accesstoken", "refreshtoken", "authorization",
            "secret", "clientsecret", "signature", "sign", "ticket",
            "apikey", "api_key", "privatekey", "private_key",
            "idcard", "idcardno", "idcardnumber", "idno", "idnumber", "idcode",
            "identitycard", "identityno", "certno", "certificateno", "certificatenumber",
            "patientid", "patientidcard", "contactinfo", "phone", "mobile", "telephone"
    );

    /**
     * 路径段中的身份证形态（15 位或 18 位）兜底识别。
     *
     * <p>{@code @PathVariable String idCard} 这类端点会把身份证号放进 URL，进而被
     * {@code requestUri}、浏览器历史、{@code Referer} 与 Nginx access.log 记录。
     * 变量名匹配是首选手段，本规则只作为「处理映射信息缺失」时的兜底。</p>
     */
    private static final Pattern ID_CARD_PATH_SEGMENT = Pattern.compile("\\d{15}|\\d{17}[\\dXx]");

    private static final Pattern JSON_STRING_FIELD = Pattern.compile(
            "(\"([^\"]+)\"\\s*:\\s*\")([^\"]*)(\")"
    );

    private final AsyncLogService asyncLogService;
    private final MeterRegistry meterRegistry;
    private final SettingRegistry settingRegistry;

    public LogInterceptor(AsyncLogService asyncLogService, MeterRegistry meterRegistry,
                          SettingRegistry settingRegistry) {
        this.asyncLogService = asyncLogService;
        this.meterRegistry = meterRegistry;
        this.settingRegistry = settingRegistry;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        if (shouldSkipLogging(request, handler)) {
            return true;
        }

        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        MDC.put("requestId", requestId);
        MDC.put("clientIp", IpUtil.getClientIp(request));
        // 注意：这里刻意不读取 X-User-Id / X-User-Role。这两个请求头完全由客户端控制，
        // 写入 MDC 会让调用方通过伪造请求头污染日志身份。日志身份统一由认证拦截器
        // 在会话建立后通过 bindAuthenticatedUser 绑定。

        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        request.setAttribute(REQUEST_ID_ATTR, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        response.setHeader(ENDPOINT_TEMPLATE_HEADER, endpointTemplate(request));
        return true;
    }

    /**
     * 把已认证会话写入 MDC，供 logback 的 {@code %X{userId}} / {@code %X{userRole}} 输出。
     *
     * <p>必须由认证拦截器在解析出会话后调用（见 {@code LoginInterceptor#installSession}）。
     * 之所以放在此处而不是 {@link #preHandle}：{@code LogInterceptor} 注册在认证拦截器之前
     * （这样认证失败的请求也会留痕），其 {@code preHandle} 执行时还没有会话可用。</p>
     */
    public static void bindAuthenticatedUser(AuthSession session) {
        if (session == null) {
            return;
        }
        if (session.getUsername() != null && !session.getUsername().isBlank()) {
            MDC.put(MDC_USER_ID, session.getUsername());
        }
        if (session.getRoleCode() != null && !session.getRoleCode().isBlank()) {
            MDC.put(MDC_USER_ROLE, session.getRoleCode());
        }
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
        log.setRequestUri(sanitizeRequestUri(request));
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

    /**
     * 脱敏请求 URI 中的敏感路径变量。
     *
     * <p>查询串与请求体已经有脱敏逻辑，但路径参数此前会被原样落库。
     * {@code GET /api/v1/search/getBAHByID/{idCard}} 这类端点会把身份证号写进
     * {@code requestUri}，因此这里按两条规则处理：</p>
     * <ol>
     *   <li>优先用 Spring 解析出的路径变量名判断（变量名敏感即脱敏），精确且不会误伤；</li>
     *   <li>再用身份证号形态兜底，覆盖映射信息缺失（例如路径未匹配到处理器）的场景。</li>
     * </ol>
     *
     * <p>审计字段（{@code audit_target} 等）仍取自原始 URI，不在此处改写，
     * 以免破坏病案号的可追溯性。</p>
     */
    private String sanitizeRequestUri(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || uri.isEmpty()) {
            return uri;
        }

        Set<String> sensitiveVariableValues = sensitivePathVariableValues(request);
        String[] segments = uri.split("/", -1);
        boolean masked = false;
        for (int index = 0; index < segments.length; index += 1) {
            String segment = segments[index];
            if (segment.isEmpty()) {
                continue;
            }
            if (sensitiveVariableValues.contains(segment)
                    || ID_CARD_PATH_SEGMENT.matcher(segment).matches()) {
                segments[index] = hashForAudit(segment);
                masked = true;
            }
        }
        return masked ? String.join("/", segments) : uri;
    }

    private Set<String> sensitivePathVariableValues(HttpServletRequest request) {
        Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(attribute instanceof Map<?, ?> variables) || variables.isEmpty()) {
            return Set.of();
        }
        return variables.entrySet().stream()
                .filter(entry -> isSensitiveName(String.valueOf(entry.getKey())))
                .map(entry -> entry.getValue() == null ? null : String.valueOf(entry.getValue()))
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.toSet());
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
        } else if (uri.startsWith(DATA_EXCHANGE_PREFIX) && uri.contains(DATA_EXCHANGE_EXPORT_MARKER)) {
            enrichDataExchangeExportAudit(log, uri);
        } else if (uri.startsWith(SETTINGS_PATH)) {
            enrichSettingAudit(log, request, uri, method);
        }
    }

    /**
     * 批量数据导出审计。
     *
     * <p>数据交换导出会一次性输出至多十万行患者与病案数据，属于高敏感批量操作。
     * 此前这类请求只落通用访问日志，无法按"导出"动作检索，事后回答不了
     * "谁在什么时候导出了哪份数据集"。这里把动作固定为 {@code EXPORT_CSV}，
     * 目标固定为数据集名，使审计查询可以按动作聚合。</p>
     *
     * <p>即使请求因权限不足被拒绝（403），审计仍会记录该动作，
     * 因为被拒绝的批量导出尝试本身也是需要留痕的安全事件。</p>
     */
    private void enrichDataExchangeExportAudit(Log log, String uri) {
        String remainder = uri.substring(DATA_EXCHANGE_PREFIX.length());
        int markerIndex = remainder.indexOf(DATA_EXCHANGE_EXPORT_MARKER);
        String dataset = markerIndex > 0 ? remainder.substring(1, markerIndex) : remainder;
        log.setAuditAction("EXPORT_CSV");
        log.setAuditTarget(truncate(dataset, MAX_AUDIT_TARGET_LENGTH));
        log.setAuditDescription("导出数据集 " + dataset + " 为 CSV");
    }

    /**
     * 运行参数变更审计。
     *
     * <p>只审计 {@link SettingRegistry} 中已注册的键。未注册的键（例如前端同步的
     * {@code adminAppSettings} 界面外观配置）不产生审计记录，避免外观同步写满审计表；
     * 这也与配置文档对「限流、会话与留存类参数写入运维审计」的承诺一致。</p>
     *
     * <p>敏感键数量单独计入描述，使「谁把登录失败上限从 5 改成 20」可以直接查证。</p>
     */
    private void enrichSettingAudit(Log log, HttpServletRequest request, String uri, String method) {
        List<String> registeredKeys = resolveChangedSettingKeys(request, uri, method).stream()
                .filter(key -> settingRegistry.find(key).isPresent())
                .sorted()
                .toList();
        if (registeredKeys.isEmpty()) {
            return;
        }

        long sensitiveCount = registeredKeys.stream()
                .filter(key -> settingRegistry.find(key)
                        .map(SettingDefinition::sensitive)
                        .orElse(false))
                .count();

        log.setAuditAction("DELETE".equalsIgnoreCase(method) ? "SETTING_DELETE" : "SETTING_UPDATE");
        log.setAuditTarget(truncate(String.join(",", registeredKeys), MAX_AUDIT_TARGET_LENGTH));
        String scope = sensitiveCount > 0
                ? "修改运行参数（含 " + sensitiveCount + " 项敏感参数）: "
                : "修改运行参数: ";
        log.setAuditDescription(truncate(
                scope + String.join(", ", registeredKeys), MAX_ERROR_MESSAGE_LENGTH));
    }

    /**
     * 解析本次请求涉及的设置键。
     * 支持 {@code PUT /api/v1/settings}（批量，取请求体顶层键）与
     * {@code PUT|DELETE /api/v1/settings/{key}}（单个）。
     */
    private List<String> resolveChangedSettingKeys(HttpServletRequest request, String uri, String method) {
        if (uri.startsWith(SETTINGS_KEY_PREFIX)) {
            String key = uri.substring(SETTINGS_KEY_PREFIX.length());
            // 只有 {key} 这一层是设置键；更深的路径不参与审计。
            return key.isBlank() || key.contains("/") ? List.of() : List.of(key);
        }
        if (SETTINGS_PATH.equals(uri) && "PUT".equalsIgnoreCase(method)) {
            return extractTopLevelJsonKeys(rawRequestBody(request));
        }
        return List.of();
    }

    private String rawRequestBody(HttpServletRequest request) {
        if (!(request instanceof ContentCachingRequestWrapper wrapper)) {
            return "";
        }
        return new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    private List<String> extractTopLevelJsonKeys(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            if (root == null || !root.isObject()) {
                return List.of();
            }
            List<String> keys = new ArrayList<>();
            root.fieldNames().forEachRemaining(keys::add);
            return keys;
        } catch (JsonProcessingException exception) {
            return List.of();
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
