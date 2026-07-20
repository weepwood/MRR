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
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/v1/logs/audit/images")
@Tag(name = "Image Audit Export", description = "病案图片访问审计导出")
@RequirePermissions({"log:read"})
public class ImageAuditExportController {

    private static final int EXPORT_BATCH_SIZE = 200;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
                writer.write("访问时间,用户,病历号,访问动作,客户端IP,请求URI,状态码,耗时毫秒,Request ID");
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
                        writer.write(csv(formatTime(log)) + ','
                                + csv(log.getUsername()) + ','
                                + csv(log.getAuditTarget()) + ','
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
}
