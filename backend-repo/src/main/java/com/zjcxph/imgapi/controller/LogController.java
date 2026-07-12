package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.config.LogRetentionProperties;
import com.zjcxph.imgapi.dto.resp.ImageAuditAnalyticsDTO;
import com.zjcxph.imgapi.entity.Log;
import com.zjcxph.imgapi.dto.resp.LogRetentionCleanupResult;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.dto.resp.PageResult;
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
import java.util.ArrayList;
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
    private final LogRetentionProperties logRetentionProperties;

    public LogController(
            LogService logService,
            LogRetentionCleaner logRetentionCleaner,
            LogRetentionProperties logRetentionProperties
    ) {
        this.logService = logService;
        this.logRetentionCleaner = logRetentionCleaner;
        this.logRetentionProperties = logRetentionProperties;
    }

    @Operation(summary = "根据ID获取日志详情")
    @GetMapping("/{id}")
    public Result<Log> getLogById(@PathVariable Long id) {
        Log log = logService.getLogById(id);
        if (log == null) {
            return Result.fail("日志不存在");
        }
        return Result.<Log>success().data(log);
    }

    @Operation(summary = "分页获取所有日志")
    @GetMapping
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
        int total = logService.countOlderThan(cutoff);
        String fileName = "access-log-retention-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now()) + ".csv";

        StreamingResponseBody body = outputStream -> {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
                writer.write('\ufeff');
                writer.write("id,username,client_ip,request_uri,method,user_agent,access_time,query_string,request_body,response_status,execute_time,referer");
                writer.newLine();

                int offset = 0;
                while (true) {
                    List<Log> logs = logService.findOlderThan(cutoff, batchSize, offset);
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
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime cursorAccessTime,
            @RequestParam(required = false) Long cursorId
    ) {
        PaginationUtils.validatePageParams(page, size);
        validateCursor(cursorAccessTime, cursorId);
        int safeSize = Math.min(size, MAX_PAGE_SIZE);

        String normalizedKeyword = normalize(keyword);
        String normalizedUsername = normalize(username);
        String normalizedClientIp = normalize(clientIp);
        String normalizedRequestUri = normalize(requestUri);
        String normalizedMethod = normalize(method);
        String normalizedResponseStatus = normalize(responseStatus);
        String startTimeText = formatDateTime(startTime);
        String endTimeText = formatDateTime(endTime);

        boolean cursorMode = cursorAccessTime != null;
        List<Log> fetched = cursorMode
                ? logService.searchLogs(normalizedKeyword, normalizedUsername, normalizedClientIp, normalizedRequestUri,
                        normalizedMethod, normalizedResponseStatus, startTimeText, endTimeText, page, safeSize + 1,
                        cursorAccessTime, cursorId)
                : logService.searchLogs(normalizedKeyword, normalizedUsername, normalizedClientIp, normalizedRequestUri,
                        normalizedMethod, normalizedResponseStatus, startTimeText, endTimeText, page, safeSize);
        boolean hasMore = cursorMode && fetched.size() > safeSize;
        List<Log> list = hasMore ? new ArrayList<>(fetched.subList(0, safeSize)) : fetched;
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
        if (!list.isEmpty() && (hasMore || (!cursorMode && (long) page * safeSize < total))) {
            Log last = list.getLast();
            pageResult.withNextCursor(formatCursorAccessTime(last), last.getId());
        }
        return Result.<PageResult<Log>>success().data(pageResult);
    }

    public Result<PageResult<Log>> searchLogs(
            int page, int size, String keyword, String username, String clientIp, String requestUri,
            String method, String responseStatus, LocalDateTime startTime, LocalDateTime endTime) {
        return searchLogs(page, size, keyword, username, clientIp, requestUri, method, responseStatus,
                startTime, endTime, null, null);
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

        String normalizedKeyword = normalize(keyword);
        String normalizedUsername = normalize(username);
        String normalizedClientIp = normalize(clientIp);
        String normalizedAuditAction = normalize(auditAction);
        String normalizedResponseStatus = normalize(responseStatus);
        String startTimeText = formatDateTime(startTime);
        String endTimeText = formatDateTime(endTime);
        List<Log> list = logService.searchImageAuditLogs(
                normalizedKeyword,
                normalizedUsername,
                normalizedClientIp,
                normalizedAuditAction,
                normalizedResponseStatus,
                startTimeText,
                endTimeText,
                page,
                safeSize
        );
        list.forEach(this::decorateAuditLog);
        int total = logService.countImageAuditLogs(
                normalizedKeyword,
                normalizedUsername,
                normalizedClientIp,
                normalizedAuditAction,
                normalizedResponseStatus,
                startTimeText,
                endTimeText
        );
        return Result.<PageResult<Log>>success().data(PageResult.of(list, total, page, safeSize));
    }

    @Operation(summary = "获取图片访问审计分析数据")
    @GetMapping("/audit/images/analytics")
    public Result<ImageAuditAnalyticsDTO> getImageAuditAnalytics(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String clientIp,
            @RequestParam(required = false) String auditAction,
            @RequestParam(required = false) String responseStatus,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime
    ) {
        ImageAuditAnalyticsDTO analytics = logService.getImageAuditAnalytics(
                normalize(keyword),
                normalize(username),
                normalize(clientIp),
                normalize(auditAction),
                normalize(responseStatus),
                formatDateTime(startTime),
                formatDateTime(endTime)
        );
        return Result.<ImageAuditAnalyticsDTO>success().data(analytics);
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

    private void validateCursor(LocalDateTime cursorAccessTime, Long cursorId) {
        if ((cursorAccessTime == null) != (cursorId == null)) {
            throw new IllegalArgumentException("游标时间与游标 ID 必须成对传入");
        }
        if (cursorId != null && cursorId <= 0) {
            throw new IllegalArgumentException("游标 ID 必须大于 0");
        }
    }

    private String formatCursorAccessTime(Log log) {
        return LocalDateTime.ofInstant(log.getAccessTime().toInstant(), ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private void decorateAuditLog(Log log) {
        String uri = log.getRequestUri();
        if (uri == null || !uri.startsWith("/api/v1/img/") || uri.contains("/hello")) {
            return;
        }

        String action;
        String target;
        String description;
        if (uri.contains("/download/")) {
            action = "DOWNLOAD";
            target = uri.substring(uri.lastIndexOf('/') + 1);
            description = "下载病案图片压缩包";
        } else if (uri.contains("/oss-image/")) {
            action = "VIEW_OSS_IMAGE";
            target = uri.substring(uri.lastIndexOf('/') + 1);
            description = "查看 OSS 病案图片";
        } else if (uri.startsWith("/api/v1/img/image/")) {
            action = "VIEW_IMAGE";
            String[] parts = uri.split("/");
            target = parts.length > 5 ? parts[5] : uri;
            description = "查看本地病案图片";
        } else {
            action = "LIST";
            target = uri.substring(uri.lastIndexOf('/') + 1);
            description = "查询病案图片列表";
        }

        if (isBlank(log.getAuditAction())) {
            log.setAuditAction(action);
        }
        if (isBlank(log.getAuditTarget())) {
            log.setAuditTarget(target);
        }
        if (isBlank(log.getAuditDescription())) {
            log.setAuditDescription(description);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
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
