package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.service.SystemInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 系统信息 Controller — 薄层，仅负责路由与调用 Service。
 * <p>
 * 重构说明：原实现中 getOverview() 通过 this.xxx().getData() 调用本类 7 个方法，
 * 每次调用都重建 Result 包装对象，且组装逻辑难以测试。现已将数据组装逻辑
 * 下沉到 {@link SystemInfoService}，Controller 各端点仅做 Result 包装。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/system")
@Tag(name = "System Info", description = "系统信息和监控接口")
@RequirePermissions({"system:read"})
public class SystemInfoController {

    private static final Logger logger = LoggerFactory.getLogger(SystemInfoController.class);

    private final SystemInfoService systemInfoService;

    public SystemInfoController(SystemInfoService systemInfoService) {
        this.systemInfoService = systemInfoService;
    }

    @Operation(summary = "获取系统基本信息")
    @GetMapping("/info")
    public Result<Map<String, Object>> getSystemInfo() {
        logger.info("获取系统基本信息");
        return Result.<Map<String, Object>>success("获取系统信息成功").data(systemInfoService.getSystemInfo());
    }

    @Operation(summary = "获取内存详细信息")
    @GetMapping("/memory")
    public Result<Map<String, Object>> getMemoryInfo() {
        logger.info("获取内存详细信息");
        return Result.<Map<String, Object>>success("获取内存信息成功").data(systemInfoService.getMemoryInfo());
    }

    @Operation(summary = "获取运行时信息")
    @GetMapping("/runtime")
    public Result<Map<String, Object>> getRuntimeInfo() {
        logger.info("获取运行时信息");
        return Result.<Map<String, Object>>success("获取运行时信息成功").data(systemInfoService.getRuntimeInfo());
    }

    @Operation(summary = "系统健康检查")
    @GetMapping("/health")
    public Result<Map<String, Object>> healthCheck() {
        logger.debug("健康检查");
        return Result.<Map<String, Object>>success("健康检查成功").data(systemInfoService.getHealth());
    }

    @Operation(summary = "获取系统属性")
    @GetMapping("/properties")
    public Result<Map<String, String>> getSystemProperties() {
        logger.info("获取系统属性");
        return Result.<Map<String, String>>success("获取系统属性成功").data(systemInfoService.getSystemProperties());
    }

    @Operation(summary = "获取统一监控数据")
    @GetMapping("/overview")
    public Result<Map<String, Object>> getOverview() {
        // 重构后：直接调用 Service 组装，不再通过 Controller 互调
        return Result.<Map<String, Object>>success("获取统一监控数据成功").data(systemInfoService.getOverview());
    }

    @Operation(summary = "获取 GC 统计信息")
    @GetMapping("/gc")
    public Result<Map<String, Object>> getGcStats() {
        return Result.<Map<String, Object>>success("success").data(systemInfoService.getGcStats());
    }

    @Operation(summary = "获取线程统计信息")
    @GetMapping("/threads")
    public Result<Map<String, Object>> getThreadStats() {
        return Result.<Map<String, Object>>success("success").data(systemInfoService.getThreadStats());
    }
}
