package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.service.NginxRuntimeObservation;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/operations/nginx")
@RequirePermissions({"system:read"})
public class NginxRuntimeObservationController {
    private final NginxRuntimeObservation service;
    public NginxRuntimeObservationController(NginxRuntimeObservation service) {this.service=service;}
    @GetMapping("/runtime")
    public Result<NginxRuntimeObservation.RuntimeStatus> runtime(HttpServletResponse response) {
        response.setHeader("Cache-Control","private, no-store");return Result.success(service.runtime());
    }
    @GetMapping("/logs")
    @RequirePermissions({"system:read","log:read"})
    public Result<NginxRuntimeObservation.LogTail> logs(@RequestParam(defaultValue="access") String kind,
            @RequestParam(defaultValue="50") int limit,HttpServletResponse response) {
        response.setHeader("Cache-Control","private, no-store");return Result.success(service.logs(kind,limit));
    }
}
