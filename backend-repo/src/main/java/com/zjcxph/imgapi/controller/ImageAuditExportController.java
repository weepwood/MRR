package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.entity.Log;
import com.zjcxph.imgapi.service.LogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/logs/audit/images")
@Tag(name = "Image Audit Export", description = "病案图片访问审计导出")
@RequirePermissions({"log:read"})
public class ImageAuditExportController {

    private static final int EXPORT_BATCH_SIZE = 200;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern JSON_STRING_FIELD = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"");

    private final LogService logService;

    public ImageAuditExportController(LogService logService) {
        this.logService = logService;
    }

    @Operation(summary = "导出病案图片访问审计 CSV")
    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(
            @RequestParam(defaultValue = "all") String scope,
            @RequestParam(required = false) String value,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String clientIp,
            @RequestParam(required = false) String auditAction,
            @RequestParam(required = false) String responseStatus,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime
    ) {
        String normalizedScope = normalizeScope(scope);
        String effectiveUsername = "user".equals(normalizedScope) ? normalize(value) : normalize(username);
        String effectiveKeyword = "target".equals(normalizedScope) ? normalize(value) : normalize(keyword);
        String target = "target".equals(normalizedScope) ? normalize(value) : null;
        String fileName = "image-audit-" + normalizedScope + "-" + FILE_TIME.format(LocalDateTime.now()) + ".csv";

        StreamingResponseBody body = outputStream -> {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
                writer.write('\ufeff');
                writer.write("访问时间,用户,病案号,上架号,身份证号,访问动作,客户端IP,请求URI,状态码,耗时毫秒,Request ID");
                writer.newLine();

                int page = 1;
                while (true) {
                    List<Log> batch = logService.searchImageAuditLogs(
                            effectiveKeyword, effectiveUsername, normalize(clientIp), normalize(auditAction),
                            normalize(responseStatus), normalize(startTime), normalize(endTime), page, EXPORT_BATCH_SIZE);
                    if (batch.isEmpty()) {
                        break;
                    }
                    for (Log log : batch) {
                        if (target != null && !target.equals(log.getAuditTarget())) {
                            continue;
                        }
                        AuditIdentifiers identifiers = parseIdentifiers(log);
                        writer.write(csv(formatTime(log)) + ','
                                + csv(log.getUsername()) + ','
                                + csv(identifiers.bah()) + ','
                                + csv(identifiers.sjh()) + ','
                                + csv(identifiers.idCard()) + ','
                                + csv(log.getAuditDescription() != null ? log.getAuditDescription() : log.getAuditAction()) + ','
                                + csv(log.getClientIp()) + ','
                                + csv(log.getRequestUri()) + ','
                                + csv(log.getResponseStatus()) + ','
                                + csv(log.getExecuteTime()) + ','
                                + csv(log.getRequestId()));
                        writer.newLine();
                    }
                    if (batch.size() < EXPORT_BATCH_SIZE) {
                        break;
                    }
                    page++;
                }
                writer.flush();
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName)
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }

    private AuditIdentifiers parseIdentifiers(Log log) {
        String bah = queryValue(log.getQueryString(), "bah");
        String sjh = queryValue(log.getQueryString(), "sjh");
        String idCard = firstNonBlank(
                queryValue(log.getQueryString(), "idCard"),
                queryValue(log.getQueryString(), "idcard"),
                queryValue(log.getQueryString(), "patientId"),
                queryValue(log.getQueryString(), "patientid"),
                jsonValue(log.getRequestBody(), "idCard"),
                jsonValue(log.getRequestBody(), "idcard"),
                jsonValue(log.getRequestBody(), "patientId"),
                jsonValue(log.getRequestBody(), "patientid")
        );

        String target = normalize(log.getAuditTarget());
        if (target != null && !"search".equalsIgnoreCase(target)) {
            if (target.startsWith("sjh:")) {
                sjh = firstNonBlank(sjh, target.substring(4));
            } else if (target.contains(":")) {
                String[] parts = target.split(":", 2);
                bah = firstNonBlank(bah, parts[0]);
                sjh = firstNonBlank(sjh, parts[1]);
            } else {
                bah = firstNonBlank(bah, target);
            }
        }
        return new AuditIdentifiers(blankToEmpty(bah), blankToEmpty(sjh), blankToEmpty(idCard));
    }

    private String queryValue(String queryString, String expectedName) {
        if (queryString == null || queryString.isBlank()) {
            return null;
        }
        for (String pair : queryString.split("&")) {
            int separator = pair.indexOf('=');
            if (separator < 0) {
                continue;
            }
            String name = decode(pair.substring(0, separator));
            if (expectedName.equals(name)) {
                return normalize(decode(pair.substring(separator + 1)));
            }
        }
        return null;
    }

    private String jsonValue(String body, String expectedName) {
        if (body == null || body.isBlank() || !body.trim().startsWith("{")) {
            return null;
        }
        Matcher matcher = JSON_STRING_FIELD.matcher(body);
        while (matcher.find()) {
            if (expectedName.equals(matcher.group(1))) {
                return normalize(matcher.group(2));
            }
        }
        return null;
    }

    private String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return value;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = normalize(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String normalizeScope(String scope) {
        if ("user".equalsIgnoreCase(scope)) {
            return "user";
        }
        if ("target".equalsIgnoreCase(scope)) {
            return "target";
        }
        return "all";
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String formatTime(Log log) {
        if (log.getAccessTime() == null) {
            return "";
        }
        return LocalDateTime.ofInstant(log.getAccessTime().toInstant(), ZoneId.systemDefault()).format(DISPLAY_TIME);
    }

    private String csv(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    private record AuditIdentifiers(String bah, String sjh, String idCard) {
    }
}
