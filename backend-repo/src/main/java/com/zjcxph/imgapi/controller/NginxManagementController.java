package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.service.NginxManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * MRR-Gateway 运维只读接口；不开放配置写入、reload 或服务启停。
 */
@RestController
@RequestMapping("/api/v1/nginx/management")
@Tag(name = "Nginx Management", description = "Windows MRR-Gateway 受控运维与配置检测")
@RequirePermissions({"system:read"})
public class NginxManagementController {
    private final NginxManagementService service;

    public NginxManagementController(NginxManagementService service) {
        this.service = service;
    }

    @GetMapping("/status")
    @Operation(summary = "读取 MRR-Gateway 状态")
    public Result<Map<String, Object>> status() {
        return Result.success(service.status());
    }

    @GetMapping("/config/files")
    @RequirePermissions({"system:manage"})
    @Operation(summary = "列出受管 Nginx 配置文件的元数据，不返回配置内容")
    public Result<List<Map<String, Object>>> configFiles() {
        try {
            return Result.success(service.configFiles());
        } catch (IllegalStateException exception) {
            return Result.fail(exception.getMessage());
        }
    }

    @PostMapping("/config/test")
    @RequirePermissions({"system:manage"})
    @Operation(summary = "只运行固定 nginx.exe -t，不修改配置或重载进程")
    public Result<Map<String, Object>> testConfig() {
        try {
            return Result.success(service.testConfig());
        } catch (IllegalStateException exception) {
            return Result.fail(exception.getMessage());
        }
    }

    @GetMapping("/logs")
    @RequirePermissions({"system:read", "log:read"})
    @Operation(summary = "分页上限内读取脱敏 Nginx 日志尾部")
    public Result<Map<String, Object>> logs(
            @RequestParam(defaultValue = "access") String type,
            @RequestParam(defaultValue = "100") int lines
    ) {
        try {
            return Result.success(service.tailLog(type, lines));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return Result.fail(exception.getMessage());
        }
    }
}
