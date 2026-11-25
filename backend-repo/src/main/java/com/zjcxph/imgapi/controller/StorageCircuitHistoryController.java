package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.service.StorageCircuitHistoryService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/operations/storage-circuit-history")
@RequirePermissions({"system:read"})
public class StorageCircuitHistoryController {
    private final StorageCircuitHistoryService service;
    public StorageCircuitHistoryController(StorageCircuitHistoryService service) { this.service=service; }

    @GetMapping
    public Result<StorageCircuitHistoryService.History> history(@RequestParam(defaultValue="24") int hours,HttpServletResponse response) {
        response.setHeader("Cache-Control","private, no-store");
        return Result.success(service.history(hours));
    }
}
