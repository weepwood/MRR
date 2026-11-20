package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.common.ResultCode;
import com.zjcxph.imgapi.service.QueryDiagnosticsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/operations/query-diagnostics")
@RequirePermissions({"system:manage", "record:read"})
public class QueryDiagnosticsController {
    private final QueryDiagnosticsService service;
    private final boolean enabled;

    public QueryDiagnosticsController(QueryDiagnosticsService service,
                                      @Value("${app.operations.query-diagnostics-enabled:true}") boolean enabled) {
        this.service = service;
        this.enabled = enabled;
    }

    public record Query(String bah, String sjh, String requestId) { }

    @PostMapping
    @RequirePermissions({"system:manage", "record:read"})
    public Result<Map<String, Object>> diagnose(@RequestBody Query query) {
        if (!enabled) {
            return Result.fail(ResultCode.NOT_FOUND.getCode(), "当前环境未提供病案查询诊断");
        }
        // 原请求编号仅用于管理员交叉核对，不据此授权读取其他人的日志。
        return Result.success(service.diagnose(query.bah(), query.sjh(), query.requestId()));
    }
}
