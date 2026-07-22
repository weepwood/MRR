package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.service.DeveloperApiAccessService;
import com.zjcxph.imgapi.service.DeveloperModeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
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
        boolean archiveLegacy = developerModeService.isArchiveLegacyRequestAvailable(request);
        boolean permissionBypass = developerApiAccessService.isPermissionBypassAllowed(request);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", archiveLegacy);
        data.put("accessMode", archiveLegacy ? "ARCHIVE_LEGACY" : "DISABLED");
        data.put("apiPermissionBypassEnabled", permissionBypass);
        return Result.success(data);
    }
}
