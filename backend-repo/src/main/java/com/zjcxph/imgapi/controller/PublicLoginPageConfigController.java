package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.service.SystemSettingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 登录页匿名公开配置。
 *
 * <p>只返回经过白名单筛选的系统标识、登录展示和管理员支持信息，
 * 不暴露开发者模式、图片来源、权限或其他内部系统配置。</p>
 */
@RestController
@RequestMapping("/api/v1/public/config")
public class PublicLoginPageConfigController {

    private static final int MAX_PUBLIC_TEXT_LENGTH = 240;
    private static final Map<String, String> DISPLAY_DEFAULTS = createDisplayDefaults();

    private final SystemSettingService systemSettingService;

    public PublicLoginPageConfigController(SystemSettingService systemSettingService) {
        this.systemSettingService = systemSettingService;
    }

    @GetMapping("/login-page")
    public Result<Map<String, String>> getLoginPageConfig() {
        Map<String, String> config = new LinkedHashMap<>();
        DISPLAY_DEFAULTS.forEach((key, fallback) -> config.put(key, resolveValue(key, fallback, false)));

        boolean featureEnabled = resolveBoolean("loginFeatureEnabled", true);
        config.put("loginFeatureEnabled", Boolean.toString(featureEnabled));

        boolean contactVisible = resolveBoolean("systemAdminContactEnabled", false)
                && resolveBoolean("systemAdminPublicVisible", false);
        config.put("systemAdminContactVisible", Boolean.toString(contactVisible));
        if (contactVisible) {
            config.put("systemAdminDisplayName", resolveValue("systemAdminDisplayName", "系统管理员", false));
            config.put("systemAdminDepartment", resolveValue("systemAdminDepartment", "信息科", false));
            config.put("systemAdminPhone", resolveValue("systemAdminPhone", "", true));
            config.put("systemAdminExtension", resolveValue("systemAdminExtension", "", true));
            config.put("systemAdminEmail", resolveValue("systemAdminEmail", "", true));
            config.put("systemAdminServiceHours", resolveValue("systemAdminServiceHours", "", true));
            config.put("systemAdminDescription", resolveValue("systemAdminDescription", "", true));
        }
        return Result.success(config);
    }

    private String resolveValue(String key, String fallback, boolean allowEmpty) {
        String value = systemSettingService.getSetting(key);
        if (value == null || value.isBlank()) {
            return allowEmpty ? "" : fallback;
        }
        String normalized = value.trim();
        return normalized.length() <= MAX_PUBLIC_TEXT_LENGTH
                ? normalized
                : normalized.substring(0, MAX_PUBLIC_TEXT_LENGTH);
    }

    private boolean resolveBoolean(String key, boolean fallback) {
        String value = systemSettingService.getSetting(key);
        if (value == null || value.isBlank()) return fallback;
        return switch (value.trim().toLowerCase()) {
            case "true", "1", "yes", "on", "enabled" -> true;
            case "false", "0", "no", "off", "disabled" -> false;
            default -> fallback;
        };
    }

    private static Map<String, String> createDisplayDefaults() {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("systemName", "MRR 病案文件管理系统");
        defaults.put("systemShortName", "MRR");
        defaults.put("systemEnglishName", "Medical Record Repository");
        defaults.put("organizationName", "");
        defaults.put("systemDescription", "面向病案影像、档案记录与运行审计的一体化工作平台。");
        defaults.put("loginEnvironmentLabel", "医院内网系统");
        defaults.put("loginFormDescription", "使用管理员分配的账号进入系统工作区。");
        defaults.put("loginHelpText", "账号创建、角色调整或密码问题请联系系统管理员。");
        defaults.put("loginFooterText", "医院内网部署 · 数据由本地服务管理");
        defaults.put("loginFeature1Title", "统一档案管理");
        defaults.put("loginFeature1Description", "集中检索病案、影像和装箱记录。");
        defaults.put("loginFeature2Title", "运行数据可视化");
        defaults.put("loginFeature2Description", "查看扫描、访问和服务状态。");
        defaults.put("loginFeature3Title", "权限与审计");
        defaults.put("loginFeature3Description", "按角色控制功能并保留访问记录。");
        return Map.copyOf(defaults);
    }
}
