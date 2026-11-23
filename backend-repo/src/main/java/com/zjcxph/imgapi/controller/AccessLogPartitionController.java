package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Permissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.service.AccessLogPartitionService;
import com.zjcxph.imgapi.utils.AuthContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * access_log 按月分区迁移的状态与手动切换入口（ADR-0010）。
 *
 * <p>切换、回退、放弃会获取 access_log 或影子表的排他锁，单次锁等待有超时，应在业务低峰执行；
 * 在维护模式下这些 POST 会被只读降级拦截，执行前先关闭维护模式。</p>
 */
@RestController
@RequestMapping("/api/v1/operations/access-log-partition")
@Tag(name = "Access Log Partition", description = "access_log 按月分区迁移（ADR-0010）")
@RequirePermissions({Permissions.SYSTEM_READ})
public class AccessLogPartitionController {

    private final AccessLogPartitionService service;

    public AccessLogPartitionController(AccessLogPartitionService service) {
        this.service = service;
    }

    @Operation(summary = "查看分区迁移阶段、复制进度与分区数量")
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.success(service.status());
    }

    @Operation(summary = "立即执行一轮历史数据复制（不受 copy-enabled 开关限制）")
    @PostMapping("/copy")
    @RequirePermissions({Permissions.SYSTEM_MANAGE})
    public Result<Map<String, Object>> copy() {
        return Result.success(service.copyRound());
    }

    @Operation(summary = "立即执行分区维护：预建分区、迁出默认分区、删除过期整月分区")
    @PostMapping("/maintain")
    @RequirePermissions({Permissions.SYSTEM_MANAGE})
    public Result<Map<String, Object>> maintain() {
        return Result.success(service.maintain());
    }

    @Operation(summary = "复制完成后切换：分区表成为 access_log，旧表保留为 access_log_legacy")
    @PostMapping("/cutover")
    @RequirePermissions({Permissions.SYSTEM_MANAGE})
    public Result<Map<String, Object>> cutover() {
        return Result.success(service.cutover(currentActor()));
    }

    @Operation(summary = "切换后回退：换回持续同步的旧表")
    @PostMapping("/rollback")
    @RequirePermissions({Permissions.SYSTEM_MANAGE})
    public Result<Map<String, Object>> rollback() {
        return Result.success(service.rollback(currentActor()));
    }

    @Operation(summary = "切换前放弃：删除影子分区表与双写触发器")
    @PostMapping("/abort")
    @RequirePermissions({Permissions.SYSTEM_MANAGE})
    public Result<Map<String, Object>> abort() {
        return Result.success(service.abort(currentActor()));
    }

    private String currentActor() {
        return AuthContext.getCurrentUser() == null
                ? "unknown"
                : AuthContext.getCurrentUser().getUsername();
    }
}
