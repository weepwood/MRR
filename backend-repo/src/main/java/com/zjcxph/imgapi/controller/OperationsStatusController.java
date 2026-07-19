package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.service.OperationsStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/system/operations")
@Tag(name = "Operations Status", description = "单服务器只读运维状态")
@RequirePermissions({"system:read"})
public class OperationsStatusController {

    private final OperationsStatusService operationsStatusService;

    public OperationsStatusController(OperationsStatusService operationsStatusService) {
        this.operationsStatusService = operationsStatusService;
    }

    @GetMapping
    @Operation(summary = "获取备份、审计队列、磁盘和日志状态")
    public Result<Map<String, Object>> getStatus() {
        return Result.<Map<String, Object>>success("获取单服务器运维状态成功")
                .data(operationsStatusService.getStatus());
    }
}
