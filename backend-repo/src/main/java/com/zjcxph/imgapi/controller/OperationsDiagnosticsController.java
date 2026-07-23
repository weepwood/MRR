package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.service.DeploymentReadinessService;
import com.zjcxph.imgapi.service.OperationsDiagnosticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/operations")
@Tag(name = "Operations Diagnostics", description = "图片来源、数据完整性、权限和部署就绪诊断")
@RequirePermissions({"system:read"})
public class OperationsDiagnosticsController {

    private final OperationsDiagnosticsService diagnosticsService;
    private final DeploymentReadinessService readinessService;

    public OperationsDiagnosticsController(
            OperationsDiagnosticsService diagnosticsService,
            DeploymentReadinessService readinessService
    ) {
        this.diagnosticsService = diagnosticsService;
        this.readinessService = readinessService;
    }

    @GetMapping("/image-source")
    @Operation(summary = "诊断单张图片的来源解析与回退过程")
    public Result<Map<String, Object>> diagnoseImageSource(
            @RequestParam(required = false) String bah,
            @RequestParam(required = false) String sjh,
            @RequestParam(required = false) Integer imageId
    ) {
        return Result.success(diagnosticsService.diagnoseImageSource(bah, sjh, imageId));
    }

    @GetMapping("/integrity")
    @Operation(summary = "获取病案数据完整性指标")
    public Result<Map<String, Object>> integrity() {
        return Result.success(diagnosticsService.integritySummary());
    }

    @GetMapping("/exports")
    @Operation(summary = "获取导出文件中心任务")
    public Result<List<Map<String, Object>>> exports(
            @RequestParam(defaultValue = "100") int limit
    ) {
        return Result.success(diagnosticsService.exportCenter(limit));
    }

    @GetMapping("/permission-matrix")
    @Operation(summary = "获取角色、接口与操作权限矩阵")
    public Result<Map<String, Object>> permissionMatrix(
            @RequestParam(defaultValue = "true") boolean comparePrevious
    ) {
        return Result.success(diagnosticsService.permissionMatrix(comparePrevious));
    }

    @PostMapping("/permission-matrix/snapshots")
    @RequirePermissions({"system:manage"})
    @Operation(summary = "保存当前权限矩阵为版本快照")
    public Result<Map<String, Object>> savePermissionSnapshot(
            @RequestBody(required = false) Map<String, String> body
    ) {
        String version = body == null ? null : body.get("version");
        return Result.success("权限矩阵快照已保存", diagnosticsService.savePermissionSnapshot(version));
    }

    @GetMapping("/readiness")
    @Operation(summary = "执行部署就绪检查并返回只读降级状态")
    public Result<Map<String, Object>> readiness(
            @RequestParam(defaultValue = "false") boolean refresh
    ) {
        if (refresh) {
            readinessService.invalidate();
        }
        return Result.success(diagnosticsService.readiness());
    }

    @GetMapping("/read-only")
    @Operation(summary = "获取当前读写模式")
    public Result<Map<String, Object>> readOnly() {
        Map<String, Object> snapshot = diagnosticsService.readiness();
        return Result.success(Map.of(
                "readOnly", snapshot.getOrDefault("readOnly", true),
                "mode", snapshot.getOrDefault("mode", "READ_ONLY_DEGRADED"),
                "checkedAt", snapshot.getOrDefault("checkedAt", "")
        ));
    }
}
