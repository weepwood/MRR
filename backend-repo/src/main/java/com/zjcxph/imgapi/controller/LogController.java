package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.config.LogRetentionProperties;
import com.zjcxph.imgapi.entity.Log;
import com.zjcxph.imgapi.dto.resp.LogRetentionCleanupResult;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.dto.resp.PageResult;
import com.zjcxph.imgapi.mapper.LogMapper;
import com.zjcxph.imgapi.scheduler.LogRetentionCleaner;
import com.zjcxph.imgapi.service.LogService;
import com.zjcxph.imgapi.utils.PaginationUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/logs")
@Tag(name = "Log Management", description = "访问日志查询、审计与清理接口")
@RequirePermissions({"log:read"})
public class LogController {

    private static final int MAX_PAGE_SIZE = 200;
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LogService logService;
    private final LogRetentionCleaner logRetentionCleaner;
    private final LogMapper logMapper;
    private final LogRetentionProperties logRetentionProperties;

    public LogController(
            LogService logService,
            LogRetentionCleaner logRetentionCleaner,
            LogMapper logMapper,
            LogRetentionProperties logRetentionProperties
    ) {
        this.logService = logService;
        this.logRetentionCleaner = logRetentionCleaner;
        this.logMapper = logMapper;
        this.logRetentionProperties = logRetentionProperties;
    }

    @Operation(summary = "根据ID获取日志详情")
    @GetMapping("/{id}")
    public Result<Log> getLogById(@PathVariable Long id) {
        Log log = logService.getLogById(id);
        if (log == null) {
            return Result.fail("log not found");
        }
        return Result.<Log>success().data(log);
    }

    @Operation(summary = "分页获取所有日志")
    @GetMapping("/")
    public Result<PageResult<Log>> getAllLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        PaginationUtils.validatePageParams(page, size);
        List<Log> logs = logService.getAllLogs(page, size);
        int total = logService.getTotalLogCount();
        PageResult<Log> pageResult = PageResult.of(logs, total, page, size);
        return Result.<PageResult<Log>>success().data(pageResult);
    }

    @Operation(summary = "按客户端IP分页查询日志")
    @GetMapping("/ip/{ip}")
    public Result<PageResult<Log>> getLogsByClientIp(
            @PathVariable String ip,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        PaginationUtils.validatePageParams(page, size);
        List<Log> logs = logService.getLogsByClientIp(ip, page, size);
        int total = logService.getLogCountByClientIp(ip);
        PageResult<Log> pageResult = PageResult.of(logs, total, page, size);
        return Result.<PageResult<Log>>success().data(pageResult);
    }

    @Operation(summary = "按请求URI分页查询日志")
    @GetMapping("/uri")
    public Result<PageResult<Log>> getLogsByRequestUri(
            @RequestParam String uri,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        PaginationUtils.validatePageParams(page, size);
        List<Log> logs = logService.getLogsByRequestUri(uri, page, size);
        int total = logService.getLogCountByRequestUri(uri);
        PageResult<Log> pageResult = PageResult.of(logs, total, page, size);
        return Result.<PageResult<Log>>success().data(pageResult);
    }

    @Operation(summary = "手动触发日志保留清理")
    @PostMapping("/retention/cleanup")
    public Result<LogRetentionCleanupResult> cleanupRetentionLogs(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime cutoff
    ) {
        LogRetentionCleanupResult cleanupResult = cutoff == null
                ? logRetentionCleaner.cleanupNow()
                : logRetentionCleaner.cleanupNow(cutoff);
        String message = cleanupResult.getMessage();
        if (message == null || message.isBlank()) {
            message = cleanupResult.isSuccess() ? "log retention cleanup executed" : "log retention cleanup skipped";
        }
        return Result.<LogRetentionCleanupResult>success().data(cleanupResult);
    }

    @Operation(summary = "导出过期日志为CSV")
    @GetMapping("/retention/export")
    public ResponseEntity<StreamingResponseBody> exportRetentionLogs() {
        int retentionDays = logRetentionProperties.getRetentionDays();
        if (retentionDays <= 0) {
            return ResponseEntity.badRequest().build();
        }

        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int batchSize = Math.max(1, logRetentionProperties.getBatchSize());
        int total = logMapper.countOlderThan(cutoff);
        String fileName = "access-log-retention-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now()) + ".csv";

        StreamingResponseBody body = outputStream -> {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
                writer.write('\ufeff');
                writer.write("id,username,client_ip,request_uri,method,user_agent,access_time,query_string,request_body,response_status,execute_time,referer");
                writer.newLine();

                int offset = 0;
                while (true) {
                    List<Log> logs = logMapper.findOlderThan(cutoff, batchSize, offset);
                    if (logs.isEmpty()) {
                        break;
                    }
                    for (Log log : logs) {
                        writer.write(toCsvRow(log));
                        writer.newLine();
                    }
                    offset += logs.size();
                }
                writer.flush();
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName)
                .header("X-Export-Total", String.valueOf(total))
                .header("X-Export-Cutoff", cutoff.toString())
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(body);
    }

    @Operation(summary = "高级日志搜索（多条件组合）")
    @GetMapping("/search")
    public Result<PageResult<Log>> searchLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String clientIp,
            @RequestParam(required = false) String requestUri,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String responseStatus,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime
    ) {
        PaginationUtils.validatePageParams(page, size);
        int safeSize = Math.min(size, MAX_PAGE_SIZE);

        String normalizedKeyword = normalize(keyword);
        String normalizedUsername = normalize(username);
        String normalizedClientIp = normalize(clientIp);
        String normalizedRequestUri = normalize(requestUri);
        String normalizedMethod = normalize(method);
        String normalizedResponseStatus = normalize(responseStatus);
        String startTimeText = formatDateTime(startTime);
        String endTimeText = formatDateTime(endTime);

        List<Log> list = logService.searchLogs(
                normalizedKeyword,
                normalizedUsername,
                normalizedClientIp,
                normalizedRequestUri,
                normalizedMethod,
                normalizedResponseStatus,
                startTimeText,
                endTimeText,
                page,
                safeSize
        );
        int total = logService.countSearchLogs(
                normalizedKeyword,
                normalizedUsername,
                normalizedClientIp,
                normalizedRequestUri,
                normalizedMethod,
                normalizedResponseStatus,
                startTimeText,
                endTimeText
        );

        PageResult<Log> pageResult = PageResult.of(list, total, page, safeSize);
        return Result.<PageResult<Log>>success().data(pageResult);
    }


    @Operation(summary = "查询图片访问审计日志")
    @GetMapping("/audit/images")
    public Result<PageResult<Log>> searchImageAuditLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String clientIp,
            @RequestParam(required = false) String auditAction,
            @RequestParam(required = false) String responseStatus,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime
    ) {
        PaginationUtils.validatePageParams(page, size);
        int safeSize = Math.min(size, MAX_PAGE_SIZE);

        String startTimeText = formatDateTime(startTime);
        String endTimeText = formatDateTime(endTime);
        List<Log> list = logService.searchImageAuditLogs(
                normalize(keyword),
                normalize(username),
                normalize(clientIp),
                normalize(auditAction),
                normalize(responseStatus),
                startTimeText,
                endTimeText,
                page,
                safeSize
        );
        list.forEach(this::decorateAuditLog);
        int total = logService.countImageAuditLogs(
                normalize(keyword),
                normalize(username),
                normalize(clientIp),
                normalize(auditAction),
                normalize(responseStatus),
                startTimeText,
                endTimeText
        );
        return Result.<PageResult<Log>>success().data(PageResult.of(list, total, page, safeSize));
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(DATETIME_FORMATTER);
    }

    private void decorateAuditLog(Log log) {
        String uri = log.getRequestUri() == null ? "" : log.getRequestUri();
        if (uri.matches("^/api/v1/img/download/\\d{8}$")) {
            log.setAuditAction("DOWNLOAD");
            log.setAuditTarget(uri.substring(uri.lastIndexOf('/') + 1));
            log.setAuditDescription("下载病案图片压缩包");
        } else if (uri.startsWith("/api/v1/img/image/")) {
            log.setAuditAction("VIEW_IMAGE");
            String[] parts = uri.split("/");
            log.setAuditTarget(parts.length > 5 ? parts[5] : uri);
            log.setAuditDescription("查看本地病案图片");
        } else if (uri.startsWith("/api/v1/img/oss-image/")) {
            log.setAuditAction("VIEW_OSS_IMAGE");
            log.setAuditTarget(uri.substring(uri.lastIndexOf('/') + 1));
            log.setAuditDescription("查看 OSS 病案图片");
        } else if (uri.matches("^/api/v1/img/\\d{8}$")) {
            log.setAuditAction("LIST");
            log.setAuditTarget(uri.substring(uri.lastIndexOf('/') + 1));
            log.setAuditDescription("查询病案图片列表");
        } else {
            log.setAuditAction("UNKNOWN");
            log.setAuditTarget(uri);
            log.setAuditDescription("敏感病案图片访问");
        }
    }

    private String toCsvRow(Log log) {
        return String.join(",",
                csvCell(log.getId()),
                csvCell(log.getUsername()),
                csvCell(log.getClientIp()),
                csvCell(log.getRequestUri()),
                csvCell(log.getMethod()),
                csvCell(log.getUserAgent()),
                csvCell(formatAccessTime(log.getAccessTime())),
                csvCell(log.getQueryString()),
                csvCell(log.getRequestBody()),
                csvCell(log.getResponseStatus()),
                csvCell(log.getExecuteTime()),
                csvCell(log.getReferer())
        );
    }

    private String csvCell(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        String escaped = text.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private String formatAccessTime(java.util.Date accessTime) {
        if (accessTime == null) {
            return "";
        }
        return LocalDateTime.ofInstant(accessTime.toInstant(), ZoneId.systemDefault()).format(DATETIME_FORMATTER);
    }
}
