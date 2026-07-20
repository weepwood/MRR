package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.service.DeveloperModeService;
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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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
    private static final Set<String> RUNTIME_SECURITY_KEYS = Set.of(DeveloperModeService.SETTING_KEY);

    private final SystemSettingService systemSettingService;

    public SystemSettingController(SystemSettingService systemSettingService) {
        this.systemSettingService = systemSettingService;
    }

    @Operation(summary = "获取全部系统设置（键值对）")
    @GetMapping("/settings")
    @RequirePermissions("system:read")
    public Result<Map<String, String>> getAllSettings() {
        logger.debug("获取全部系统设置");
        Map<String, String> visibleSettings = new LinkedHashMap<>(systemSettingService.getAllSettings());
        RUNTIME_SECURITY_KEYS.forEach(visibleSettings::remove);
        return Result.success(visibleSettings);
    }

    @Operation(summary = "获取单个设置值")
    @GetMapping("/settings/{key}")
    @RequirePermissions("system:read")
    public Result<String> getSetting(@PathVariable String key) {
        if (RUNTIME_SECURITY_KEYS.contains(key)) {
            return Result.fail(404, "设置项不存在: " + key);
        }
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

        Map<String, String> writableSettings = new LinkedHashMap<>(settings);
        RUNTIME_SECURITY_KEYS.forEach(key -> {
            if (writableSettings.containsKey(key)) {
                writableSettings.remove(key);
                logger.warn("忽略批量设置请求中的安全敏感配置: key={}", key);
            }
        });
        if (writableSettings.isEmpty()) {
            return Result.fail(400, "没有可写入的系统设置");
        }

        systemSettingService.saveSettings(writableSettings, null);
        logger.info("系统设置已更新: {} 项", writableSettings.size());
        return Result.<Void>success(null).message("设置已保存");
    }

    @Operation(summary = "保存单个设置值")
    @PutMapping("/settings/{key}")
    @RequirePermissions("system:manage")
    public Result<Void> setSetting(@PathVariable String key, @RequestBody Map<String, String> body) {
        if (RUNTIME_SECURITY_KEYS.contains(key)) {
            return restrictedSettingResult(key);
        }
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
        if (RUNTIME_SECURITY_KEYS.contains(key)) {
            return restrictedSettingResult(key);
        }
        systemSettingService.deleteSetting(key);
        return Result.<Void>success(null).message("设置已删除");
    }

    private Result<Void> restrictedSettingResult(String key) {
        logger.warn("拒绝通过运行时设置接口修改安全敏感配置: key={}", key);
        return Result.fail(400, "安全敏感配置不允许通过系统设置接口修改: " + key);
    }
}
