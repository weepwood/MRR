package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.service.OperationsHistoryService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations/history")
@RequirePermissions({"system:read"})
public class OperationsHistoryController {
    private final OperationsHistoryService service;

    public OperationsHistoryController(OperationsHistoryService service) { this.service = service; }

    @GetMapping
    public Result<OperationsHistoryService.History> history(@RequestParam(defaultValue = "24") int hours,
            HttpServletResponse response) {
        response.setHeader("Cache-Control","private, no-store");
        return Result.success(service.history(hours));
    }
}
