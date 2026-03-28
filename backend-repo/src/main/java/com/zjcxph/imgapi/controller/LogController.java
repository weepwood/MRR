package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.config.LogRetentionProperties;
import com.zjcxph.imgapi.pojo.Log;
import com.zjcxph.imgapi.pojo.LogRetentionCleanupResult;
import com.zjcxph.imgapi.pojo.Result;
import com.zjcxph.imgapi.mapper.LogMapper;
import com.zjcxph.imgapi.scheduler.LogRetentionCleaner;
import com.zjcxph.imgapi.service.LogService;
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
@RequestMapping({"/v2/logs", "/v1/logs-api"})
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
        return Result.<LogRetentionCleanupResult>success(message).data(cleanupResult);
    }

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
                writer.write("id,client_ip,request_uri,method,user_agent,access_time,query_string,request_body,response_status,execute_time,referer");
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

    private String toCsvRow(Log log) {
        return String.join(",",
                csvCell(log.getId()),
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
