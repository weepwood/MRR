package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.service.DeveloperModeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 为前端匿名路由守卫提供最小化档案袋兼容模式状态。
 *
 * <p>只返回当前请求来源是否可使用兼容模式，不暴露启动配置、允许地址或虚拟身份。</p>
 */
@RestController
@RequestMapping("/api/v1/public/status")
public class DeveloperModeStatusController {

    private final DeveloperModeService developerModeService;

    public DeveloperModeStatusController(DeveloperModeService developerModeService) {
        this.developerModeService = developerModeService;
    }

    @GetMapping("/developer-mode")
    public Result<Map<String, Object>> status(HttpServletRequest request) {
        boolean enabled = developerModeService.isArchiveLegacyRequestAvailable(request);
        return Result.success(Map.of(
                "enabled", enabled,
                "accessMode", enabled ? "ARCHIVE_LEGACY" : "DISABLED"
        ));
    }
}
