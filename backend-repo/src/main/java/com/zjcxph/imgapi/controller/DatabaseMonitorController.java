package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.service.DatabaseMonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system/database")
@Tag(name = "Database Monitoring", description = "PostgreSQL、连接池和慢查询监控")
@RequirePermissions({"system:read"})
public class DatabaseMonitorController {

    private final DatabaseMonitorService databaseMonitorService;

    public DatabaseMonitorController(DatabaseMonitorService databaseMonitorService) {
        this.databaseMonitorService = databaseMonitorService;
    }

    @Operation(summary = "获取数据库运行概览")
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.<Map<String, Object>>success("获取数据库运行概览成功")
                .data(databaseMonitorService.getOverview());
    }

    @Operation(summary = "获取累计耗时最高的 SQL 模板")
    @GetMapping("/slow-queries")
    public Result<List<Map<String, Object>>> slowQueries(@RequestParam(defaultValue = "20") int limit) {
        return Result.<List<Map<String, Object>>>success("获取慢查询成功")
                .data(databaseMonitorService.getSlowQueries(limit));
    }

    @Operation(summary = "获取占用空间最大的表")
    @GetMapping("/tables")
    public Result<List<Map<String, Object>>> tables(@RequestParam(defaultValue = "20") int limit) {
        return Result.<List<Map<String, Object>>>success("获取表空间统计成功")
                .data(databaseMonitorService.getLargestTables(limit));
    }
}
