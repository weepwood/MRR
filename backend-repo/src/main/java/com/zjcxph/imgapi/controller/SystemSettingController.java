package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.service.SystemSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 系统设置 REST 控制器。
 * <p>
 * 提供系统级配置的读写能力，替代前端 localStorage 草稿模式。
 * 所有变更需 system:read 权限（读）或 system:manage 权限（写）。
 * </p>
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "System Settings", description = "系统设置管理接口")
public class SystemSettingController {

    private static final Logger logger = LoggerFactory.getLogger(SystemSettingController.class);

    private final SystemSettingService systemSettingService;

    public SystemSettingController(SystemSettingService systemSettingService) {
        this.systemSettingService = systemSettingService;
    }

    @Operation(summary = "获取全部系统设置（键值对）")
    @GetMapping("/settings")
    @RequirePermissions("system:read")
    public Result<Map<String, String>> getAllSettings() {
        logger.debug("获取全部系统设置");
        return Result.success(systemSettingService.getAllSettings());
    }

    @Operation(summary = "获取单个设置值")
    @GetMapping("/settings/{key}")
    @RequirePermissions("system:read")
    public Result<String> getSetting(@PathVariable String key) {
        String value = systemSettingService.getSetting(key);
        if (value == null) {
            return Result.fail(404, "设置项不存在: " + key);
        }
        return Result.<String>successWithData(value);
    }

    @Operation(summary = "批量保存系统设置")
    @PutMapping("/settings")
    @RequirePermissions("system:manage")
    public Result<Void> saveSettings(@RequestBody Map<String, String> settings) {
        if (settings == null || settings.isEmpty()) {
            return Result.fail(400, "设置内容不能为空");
        }
        systemSettingService.saveSettings(settings, null);
        logger.info("系统设置已更新: {} 项", settings.size());
        return Result.<Void>success(null).message("设置已保存");
    }

    @Operation(summary = "保存单个设置值")
    @PutMapping("/settings/{key}")
    @RequirePermissions("system:manage")
    public Result<Void> setSetting(@PathVariable String key, @RequestBody Map<String, String> body) {
        String value = body != null ? body.get("value") : null;
        if (value == null) {
            return Result.fail(400, "value 不能为空");
        }
        systemSettingService.setSetting(key, value, null);
        return Result.<Void>success(null).message("设置已更新");
    }

    @Operation(summary = "删除单个设置")
    @DeleteMapping("/settings/{key}")
    @RequirePermissions("system:manage")
    public Result<Void> deleteSetting(@PathVariable String key) {
        systemSettingService.deleteSetting(key);
        return Result.<Void>success(null).message("设置已删除");
    }
}
