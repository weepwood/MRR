package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.service.SystemSettingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 帮助中心公开文档入口配置。
 *
 * <p>仅返回用户手册、开发文档和运维指南三个白名单 URL，
 * 不暴露其他系统设置。URL 只允许站内绝对路径或 HTTP(S) 地址。</p>
 */
@RestController
@RequestMapping("/api/v1/public/config")
public class PublicDocumentationConfigController {

    static final String USER_GUIDE_KEY = "documentationUserGuideUrl";
    static final String DEVELOPER_GUIDE_KEY = "documentationDeveloperUrl";
    static final String OPERATIONS_GUIDE_KEY = "documentationOperationsUrl";

    private static final int MAX_URL_LENGTH = 2048;
    private static final Map<String, String> DEFAULT_URLS = createDefaults();

    private final SystemSettingService systemSettingService;

    public PublicDocumentationConfigController(SystemSettingService systemSettingService) {
        this.systemSettingService = systemSettingService;
    }

    @GetMapping("/documentation")
    public Result<Map<String, String>> getDocumentationConfig() {
        Map<String, String> config = new LinkedHashMap<>();
        DEFAULT_URLS.forEach((key, fallback) -> config.put(key, resolveUrl(key, fallback)));
        return Result.success(config);
    }

    private String resolveUrl(String key, String fallback) {
        String value = systemSettingService.getSetting(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_URL_LENGTH || !isAllowedUrl(normalized)) {
            return fallback;
        }
        return normalized;
    }

    static boolean isAllowedUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim();
        if (normalized.startsWith("/") && !normalized.startsWith("//")) {
            return !normalized.contains("\\");
        }
        try {
            URI uri = URI.create(normalized);
            String scheme = uri.getScheme();
            return uri.getHost() != null
                    && uri.getUserInfo() == null
                    && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static Map<String, String> createDefaults() {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put(USER_GUIDE_KEY, "/docs/");
        defaults.put(DEVELOPER_GUIDE_KEY, "/docs/internal/");
        defaults.put(OPERATIONS_GUIDE_KEY, "/docs/internal/deployment.html");
        return Map.copyOf(defaults);
    }
}
