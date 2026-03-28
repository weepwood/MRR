package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.pojo.Log;
import com.zjcxph.imgapi.pojo.LogRetentionCleanupResult;
import com.zjcxph.imgapi.pojo.Result;
import com.zjcxph.imgapi.scheduler.LogRetentionCleaner;
import com.zjcxph.imgapi.service.LogService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/v2/logs", "/v1/logs-api"})
public class LogController {

    private static final int MAX_PAGE_SIZE = 200;
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LogService logService;
    private final LogRetentionCleaner logRetentionCleaner;

    public LogController(LogService logService, LogRetentionCleaner logRetentionCleaner) {
        this.logService = logService;
        this.logRetentionCleaner = logRetentionCleaner;
    }

    @GetMapping("/{id}")
    public Result<Log> getLogById(@PathVariable Long id) {
        Log log = logService.getLogById(id);
        if (log == null) {
            return Result.fail("log not found");
        }
        return Result.<Log>success("success").data(log);
    }

    @GetMapping("/")
    public Result<List<Log>> getAllLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<Log> logs = logService.getAllLogs(page, size);
        int total = logService.getTotalLogCount();
        return new Result<>(200, "success", logs, total);
    }

    @GetMapping("/ip/{ip}")
    public Result<List<Log>> getLogsByClientIp(
            @PathVariable String ip,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<Log> logs = logService.getLogsByClientIp(ip, page, size);
        int total = logService.getLogCountByClientIp(ip);
        return new Result<>(200, "success", logs, total);
    }

    @GetMapping("/uri")
    public Result<List<Log>> getLogsByRequestUri(
            @RequestParam String uri,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<Log> logs = logService.getLogsByRequestUri(uri, page, size);
        int total = logService.getLogCountByRequestUri(uri);
        return new Result<>(200, "success", logs, total);
    }

    @PostMapping("/retention/cleanup")
    public Result<LogRetentionCleanupResult> cleanupRetentionLogs() {
        LogRetentionCleanupResult cleanupResult = logRetentionCleaner.cleanupNow();
        String message = cleanupResult.getMessage();
        if (message == null || message.isBlank()) {
            message = cleanupResult.isSuccess() ? "log retention cleanup executed" : "log retention cleanup skipped";
        }
        return Result.<LogRetentionCleanupResult>success(message).data(cleanupResult);
    }

    @GetMapping("/search")
    public Result<Map<String, Object>> searchLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String clientIp,
            @RequestParam(required = false) String requestUri,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String responseStatus,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime
    ) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));

        String normalizedKeyword = normalize(keyword);
        String normalizedClientIp = normalize(clientIp);
        String normalizedRequestUri = normalize(requestUri);
        String normalizedMethod = normalize(method);
        String normalizedResponseStatus = normalize(responseStatus);
        String startTimeText = formatDateTime(startTime);
        String endTimeText = formatDateTime(endTime);

        List<Log> list = logService.searchLogs(
                normalizedKeyword,
                normalizedClientIp,
                normalizedRequestUri,
                normalizedMethod,
                normalizedResponseStatus,
                startTimeText,
                endTimeText,
                safePage,
                safeSize
        );
        int total = logService.countSearchLogs(
                normalizedKeyword,
                normalizedClientIp,
                normalizedRequestUri,
                normalizedMethod,
                normalizedResponseStatus,
                startTimeText,
                endTimeText
        );

        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", total);
        data.put("page", safePage);
        data.put("size", safeSize);
        data.put("totalPages", (total + safeSize - 1) / safeSize);

        return Result.<Map<String, Object>>success("success").data(data).code(200);
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
}
