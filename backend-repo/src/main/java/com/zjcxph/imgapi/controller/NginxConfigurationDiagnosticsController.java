package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.service.NginxConfigurationDiagnostics;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/operations/nginx")
@RequirePermissions({"system:read"})
public class NginxConfigurationDiagnosticsController {
    private final NginxConfigurationDiagnostics diagnostics;
    public NginxConfigurationDiagnosticsController(NginxConfigurationDiagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }

    @GetMapping
    public Result<NginxConfigurationDiagnostics.Status> status(HttpServletResponse response) {
        response.setHeader("Cache-Control","private, no-store");
        return Result.success(diagnostics.status());
    }

    @GetMapping("/listener-hints")
    public Result<NginxConfigurationDiagnostics.ListenerHints> listenerHints(HttpServletResponse response) {
        response.setHeader("Cache-Control","private, no-store");
        return Result.success(diagnostics.listenerHints());
    }

    @PostMapping("/version")
    @RequirePermissions({"system:read","system:manage"})
    public Result<NginxConfigurationDiagnostics.Version> version(HttpServletResponse response) {
        response.setHeader("Cache-Control","private, no-store");
        return Result.success(diagnostics.version());
    }

    @PostMapping("/check")
    @RequirePermissions({"system:read","system:manage"})
    public Result<NginxConfigurationDiagnostics.Check> check(HttpServletResponse response) {
        response.setHeader("Cache-Control","private, no-store");
        return Result.success(diagnostics.check());
    }
}
