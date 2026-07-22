package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.common.Permissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.service.DeveloperApiAccessService;
import com.zjcxph.imgapi.service.DeveloperModeService;
import com.zjcxph.imgapi.utils.PermissionResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/public/status")
public class DeveloperModeStatusController {

    private final DeveloperModeService developerModeService;
    private final DeveloperApiAccessService developerApiAccessService;

    public DeveloperModeStatusController(DeveloperModeService developerModeService,
                                         DeveloperApiAccessService developerApiAccessService) {
        this.developerModeService = developerModeService;
        this.developerApiAccessService = developerApiAccessService;
    }

    @GetMapping("/developer-mode")
    public Result<Map<String, Object>> status(HttpServletRequest request) {
        boolean apiFull = developerApiAccessService.isRequestAllowed(request);
        boolean archiveLegacy = !apiFull && developerModeService.isArchiveLegacyRequestAvailable(request);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", apiFull || archiveLegacy);
        data.put("accessMode", apiFull ? "API_FULL" : archiveLegacy ? "ARCHIVE_LEGACY" : "DISABLED");

        if (apiFull) {
            List<String> permissions = PermissionResolver.resolve(Permissions.ALL_PERMISSIONS)
                    .stream().sorted().toList();
            data.put("session", Map.of(
                    "username", "developer-api",
                    "displayName", "Developer API",
                    "roleCode", "DEVELOPER_API",
                    "roleName", "Developer Full API",
                    "status", "active",
                    "mustChangePassword", false,
                    "passwordVersion", 1,
                    "permissions", permissions
            ));
        }
        return Result.success(data);
    }
}
