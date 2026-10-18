package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.service.QueryDiagnosticsService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="app.operations.query-diagnostics-enabled", havingValue="true", matchIfMissing=true)
@RestController
@RequestMapping("/api/v1/operations/query-diagnostics")
@RequirePermissions({"system:manage", "record:read"})
public class QueryDiagnosticsController {
    private final QueryDiagnosticsService service;
    public QueryDiagnosticsController(QueryDiagnosticsService service) { this.service = service; }
    public record Query(String bah, String sjh, String requestId) { }
    @PostMapping
    @RequirePermissions({"system:manage", "record:read"})
    public Result<Map<String, Object>> diagnose(@RequestBody Query query) {
        // 原请求编号仅用于管理员交叉核对，不据此授权读取其他人的日志。
        return Result.success(service.diagnose(query.bah(), query.sjh(), query.requestId()));
    }
}
