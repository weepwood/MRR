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
 * <p>只返回经过白名单筛选的展示文案，不暴露开发者模式、图片来源、权限或其他系统配置。</p>
 */
@RestController
@RequestMapping("/api/v1/public/config")
public class PublicLoginPageConfigController {

    private static final Map<String, String> DEFAULTS = createDefaults();
    private static final int MAX_PUBLIC_TEXT_LENGTH = 240;

    private final SystemSettingService systemSettingService;

    public PublicLoginPageConfigController(SystemSettingService systemSettingService) {
        this.systemSettingService = systemSettingService;
    }

    @GetMapping("/login-page")
    public Result<Map<String, String>> getLoginPageConfig() {
        Map<String, String> config = new LinkedHashMap<>();
        DEFAULTS.forEach((key, fallback) -> config.put(key, resolveValue(key, fallback)));
        return Result.success(config);
    }

    private String resolveValue(String key, String fallback) {
        String value = systemSettingService.getSetting(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim();
        return normalized.length() <= MAX_PUBLIC_TEXT_LENGTH
                ? normalized
                : normalized.substring(0, MAX_PUBLIC_TEXT_LENGTH);
    }

    private static Map<String, String> createDefaults() {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("loginEnvironmentLabel", "MRR Console");
        defaults.put("loginBrandEyebrow", "Medical Record Repository");
        defaults.put("loginBrandTitle", "病案文件管理系统");
        defaults.put("loginBrandDescription", "面向病案影像、档案记录与运行审计的一体化工作平台。");
        defaults.put("loginFeature1Title", "统一档案管理");
        defaults.put("loginFeature1Description", "集中检索病案、影像和装箱记录。");
        defaults.put("loginFeature2Title", "运行数据可视化");
        defaults.put("loginFeature2Description", "查看扫描、访问和服务状态。");
        defaults.put("loginFeature3Title", "权限与审计");
        defaults.put("loginFeature3Description", "按角色控制功能并保留访问记录。");
        defaults.put("loginFormEyebrow", "Secure sign in");
        defaults.put("loginFormTitle", "登录 MRR");
        defaults.put("loginFormDescription", "使用管理员分配的账号进入系统工作区。");
        defaults.put("loginHelpText", "系统不开放自助注册和在线重置密码。账号创建、角色调整或密码问题请联系系统管理员。");
        defaults.put("loginFooterText", "医院内网部署 · 数据由本地服务管理");
        return Map.copyOf(defaults);
    }
}
