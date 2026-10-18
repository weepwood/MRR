package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.service.RuntimeLogJournalService;
import com.zjcxph.imgapi.service.RuntimeLogQueryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/runtime-logs")
@RequirePermissions({"system:manage", "log:read"})
@ConditionalOnProperty(name = "app.runtime-logs.enabled", havingValue = "true", matchIfMissing = false)
public class RuntimeLogController {
    private final RuntimeLogJournalService service;
    private final RuntimeLogQueryService queries;
    public RuntimeLogController(RuntimeLogJournalService service, RuntimeLogQueryService queries) { this.service = service; this.queries = queries; }

    @ModelAttribute
    public void privateResponse(jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store, max-age=0");
        response.setHeader("Pragma", "no-cache");
    }

    @GetMapping("/health") public Result<Map<String, Object>> health() { return Result.success(service.health()); }
    @GetMapping("/files") public Result<List<Map<String, Object>>> files() { return Result.success(service.files()); }

    public record ImportRequest(String fileName) { }
    @PostMapping("/imports")
    @RequirePermissions({"system:manage", "log:read", "system:log:import"})
    public Result<Void> importFile(@RequestBody ImportRequest request) {
        service.approveImport(request.fileName());
        return Result.<Void>success().message("历史日志已登记，后台将按批次补录");
    }

    @GetMapping
    public Result<Map<String, Object>> search(@RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to, @RequestParam(required = false) String level,
            @RequestParam(required = false) String requestId, @RequestParam(required = false) String errorId,
            @RequestParam(required = false) String logger, @RequestParam(required = false) Instant cursorTime,
            @RequestParam(required = false) UUID cursorId, @RequestParam(defaultValue = "50") int size) {
        return Result.success(queries.search(from, to, level, requestId, errorId, logger, cursorTime, cursorId, size));
    }
}
