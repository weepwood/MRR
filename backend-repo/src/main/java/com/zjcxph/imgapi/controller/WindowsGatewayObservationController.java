package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.service.WindowsGatewayObservation;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/operations/nginx/windows-observation")
@RequirePermissions({"system:read","system:manage"})
public class WindowsGatewayObservationController {
    private final WindowsGatewayObservation service;
    public WindowsGatewayObservationController(WindowsGatewayObservation service) {this.service=service;}

    @PostMapping
    @RequirePermissions({"system:read","system:manage"})
    public Result<WindowsGatewayObservation.Observation> observe(HttpServletResponse response) {
        response.setHeader("Cache-Control","private, no-store");
        return Result.success(service.observe());
    }
}
