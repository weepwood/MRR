package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.service.DeveloperModeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 为前端匿名路由守卫提供最小化开发者模式状态。
 *
 * <p>只返回 enabled 布尔值，不返回系统设置、虚拟用户或其他安全配置。</p>
 */
@RestController
@RequestMapping("/api/v1/public/status")
public class DeveloperModeStatusController {

    private final DeveloperModeService developerModeService;

    public DeveloperModeStatusController(DeveloperModeService developerModeService) {
        this.developerModeService = developerModeService;
    }

    @GetMapping("/developer-mode")
    public Result<Map<String, Boolean>> status() {
        return Result.success(Map.of("enabled", developerModeService.isEnabled()));
    }
}
