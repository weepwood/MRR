package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.AuthenticatedOnly;
import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.service.SystemSettingService;
import com.zjcxph.imgapi.setting.RuntimeSettingStore;
import com.zjcxph.imgapi.setting.SettingDefinition;
import com.zjcxph.imgapi.setting.SettingRegistry;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 系统设置 REST 控制器。
 * <p>
 * 提供系统级配置的读写能力，替代前端 localStorage 草稿模式。
 * 大部分端点需 {@code system:read} 权限（读）或 {@code system:manage} 权限（写）。
 * </p>
 * <p>
 * 例外：{@code GET /api/v1/settings/ui} 只承载界面外观与导航外链这类
 * 需要被全体登录用户同步的展示型配置，因此仅要求登录会话（{@link AuthenticatedOnly}），
 * 不要求 {@code system:read}，否则普通医生账号无法同步全站统一外观。
 * 该端点只返回 {@link #UI_SETTINGS_KEY} 一个键，不暴露任何安全或运维参数。
 * </p>
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "System Settings", description = "系统设置管理接口")
public class SystemSettingController {

    /**
     * 界面外观与导航外链的存储键。前端把全局外观配置序列化为 JSON 存于此键下。
     */
    public static final String UI_SETTINGS_KEY = "adminAppSettings";

    private static final Logger logger = LoggerFactory.getLogger(SystemSettingController.class);

    /** 分组展示名。未列出的分组直接使用分组键本身。 */
    private static final Map<String, String> GROUP_LABELS = Map.of(
            SettingRegistry.GROUP_SECURITY, "安全策略",
            SettingRegistry.GROUP_OPS, "日志留存与清理"
    );

    private final SystemSettingService systemSettingService;
    private final SettingRegistry settingRegistry;
    private final RuntimeSettingStore runtimeSettingStore;

    public SystemSettingController(SystemSettingService systemSettingService,
                                   SettingRegistry settingRegistry,
                                   RuntimeSettingStore runtimeSettingStore) {
        this.systemSettingService = systemSettingService;
        this.settingRegistry = settingRegistry;
        this.runtimeSettingStore = runtimeSettingStore;
    }

    @Operation(summary = "获取可配置项的元数据（供前端动态渲染）")
    @GetMapping("/settings/schema")
    @RequirePermissions("system:read")
    public Result<Map<String, Object>> getSettingsSchema() {
        List<Map<String, Object>> items = settingRegistry.all().stream()
                .map(this::toSchemaItem)
                .toList();

        List<Map<String, Object>> groups = new ArrayList<>();
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (SettingDefinition definition : settingRegistry.all()) {
            if (seen.putIfAbsent(definition.group(), Boolean.TRUE) == null) {
                groups.add(Map.of(
                        "key", definition.group(),
                        "label", GROUP_LABELS.getOrDefault(definition.group(), definition.group())));
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("groups", groups);
        payload.put("items", items);
        return Result.success(payload);
    }

    private Map<String, Object> toSchemaItem(SettingDefinition definition) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", definition.key());
        item.put("group", definition.group());
        item.put("type", definition.type().name().toLowerCase(Locale.ROOT));
        item.put("label", definition.label());
        item.put("description", definition.description());
        item.put("unit", definition.unit());
        item.put("min", definition.min());
        item.put("max", definition.max());
        item.put("allowedValues", definition.allowedValues());
        item.put("effect", definition.effect().name());
        item.put("sensitive", definition.sensitive());
        item.put("defaultValue", definition.defaultRawValue());
        item.put("overrideValue", runtimeSettingStore.readOverride(definition.key()));
        item.put("effectiveValue", runtimeSettingStore.resolveRaw(definition.key()));
        return item;
    }

    @Operation(summary = "获取全部系统设置（键值对）")
    @GetMapping("/settings")
    @RequirePermissions("system:read")
    public Result<Map<String, String>> getAllSettings() {
        logger.debug("获取全部系统设置");
        return Result.success(systemSettingService.getAllSettings());
    }

    @Operation(summary = "获取全局界面外观与导航外链设置（仅需登录）")
    @GetMapping("/settings/ui")
    @AuthenticatedOnly
    public Result<Map<String, String>> getUiSettings() {
        Map<String, String> payload = new LinkedHashMap<>();
        String value = systemSettingService.getSetting(UI_SETTINGS_KEY);
        if (value != null && !value.isBlank()) {
            payload.put(UI_SETTINGS_KEY, value);
        }
        return Result.success(payload);
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
