package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.service.ImageSourceDiagnosticsService;
import com.zjcxph.imgapi.service.IntegrityDiagnosticsService;
import com.zjcxph.imgapi.service.MaintenanceModeService;
import com.zjcxph.imgapi.service.OperationsCenterService;
import com.zjcxph.imgapi.service.OperationsDiagnosticsService;
import com.zjcxph.imgapi.utils.AuthContext;
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
@Tag(name = "Operations Diagnostics", description = "运维总览、图片来源、数据完整性、权限和部署诊断")
@RequirePermissions({"system:read"})
public class OperationsDiagnosticsController {

    private final OperationsDiagnosticsService diagnosticsService;
    private final OperationsCenterService operationsCenterService;
    private final ImageSourceDiagnosticsService imageSourceDiagnosticsService;
    private final IntegrityDiagnosticsService integrityDiagnosticsService;
    private final MaintenanceModeService maintenanceModeService;

    public OperationsDiagnosticsController(
            OperationsDiagnosticsService diagnosticsService,
            OperationsCenterService operationsCenterService,
            ImageSourceDiagnosticsService imageSourceDiagnosticsService,
            IntegrityDiagnosticsService integrityDiagnosticsService,
            MaintenanceModeService maintenanceModeService
    ) {
        this.diagnosticsService = diagnosticsService;
        this.operationsCenterService = operationsCenterService;
        this.imageSourceDiagnosticsService = imageSourceDiagnosticsService;
        this.integrityDiagnosticsService = integrityDiagnosticsService;
        this.maintenanceModeService = maintenanceModeService;
    }

    @GetMapping("/overview")
    @Operation(summary = "获取运维总览、运行模式、任务和错误摘要")
    public Result<Map<String, Object>> overview() {
        return Result.success(operationsCenterService.overview());
    }

    @PostMapping("/diagnostics/run")
    @RequirePermissions({"system:manage"})
    @Operation(summary = "执行一次全面运维体检并返回处理建议")
    public Result<Map<String, Object>> runDiagnostics() {
        return Result.success("全面体检已完成", operationsCenterService.runFullDiagnostics());
    }

    @GetMapping("/diagnostic-report")
    @RequirePermissions({"system:manage"})
    @Operation(summary = "生成不包含患者数据和凭据的诊断报告")
    public Result<Map<String, Object>> diagnosticReport() {
        return Result.success(operationsCenterService.diagnosticReport());
    }

    @GetMapping("/operation-audit")
    @RequirePermissions({"system:read", "log:read"})
    @Operation(summary = "获取最近的运维操作与失败记录")
    public Result<List<Map<String, Object>>> operationAudit(
            @RequestParam(defaultValue = "50") int limit
    ) {
        return Result.success(operationsCenterService.recentOperations(limit));
    }

    @GetMapping("/maintenance")
    @Operation(summary = "获取主动维护模式状态")
    public Result<Map<String, Object>> maintenance() {
        return Result.success(maintenanceModeService.getStatus());
    }

    @PostMapping("/maintenance/enable")
    @RequirePermissions({"system:manage"})
    @Operation(summary = "进入主动维护只读模式")
    public Result<Map<String, Object>> enableMaintenance(
            @RequestBody(required = false) Map<String, String> body
    ) {
        String reason = body == null ? null : body.get("reason");
        return Result.success("已进入主动维护模式", maintenanceModeService.enable(reason, currentActor()));
    }

    @PostMapping("/maintenance/disable")
    @RequirePermissions({"system:manage"})
    @Operation(summary = "退出主动维护模式；自动降级状态不受影响")
    public Result<Map<String, Object>> disableMaintenance() {
        return Result.success("主动维护模式已关闭", maintenanceModeService.disable(currentActor()));
    }

    @GetMapping("/image-source")
    @RequirePermissions({"system:read", "record:read"})
    @Operation(summary = "诊断单张图片的来源解析与回退过程")
    public Result<Map<String, Object>> diagnoseImageSource(
            @RequestParam(required = false) String bah,
            @RequestParam(required = false) String sjh,
            @RequestParam(required = false) Integer imageId
    ) {
        return Result.success(imageSourceDiagnosticsService.diagnose(bah, sjh, imageId));
    }

    @GetMapping("/integrity")
    @Operation(summary = "读取最近一次后台生成的病案数据完整性快照")
    public Result<Map<String, Object>> integrity() {
        return Result.success(integrityDiagnosticsService.getSnapshot());
    }

    @GetMapping("/exports")
    @RequirePermissions({"system:read", "record:read"})
    @Operation(summary = "获取导出文件中心任务")
    public Result<List<Map<String, Object>>> exports(
            @RequestParam(defaultValue = "100") int limit
    ) {
        return Result.success(diagnosticsService.exportCenter(limit));
    }

    @GetMapping("/permission-matrix")
    @RequirePermissions({"system:read", "role:read"})
    @Operation(summary = "获取角色、接口与操作权限矩阵")
    public Result<Map<String, Object>> permissionMatrix(
            @RequestParam(defaultValue = "true") boolean comparePrevious
    ) {
        return Result.success(diagnosticsService.permissionMatrix(comparePrevious));
    }

    @PostMapping("/permission-matrix/snapshots")
    @RequirePermissions({"system:manage", "role:read"})
    @Operation(summary = "保存当前权限矩阵为版本快照")
    public Result<Map<String, Object>> savePermissionSnapshot(
            @RequestBody(required = false) Map<String, String> body
    ) {
        String version = body == null ? null : body.get("version");
        return Result.success("权限矩阵快照已保存", diagnosticsService.savePermissionSnapshot(version));
    }

    @GetMapping("/readiness")
    @Operation(summary = "读取最近一次部署就绪检查结果和当前有效模式")
    public Result<Map<String, Object>> readiness() {
        return Result.success(operationsCenterService.readiness());
    }

    @PostMapping("/readiness/refresh")
    @RequirePermissions({"system:manage"})
    @Operation(summary = "由管理员显式刷新部署就绪检查")
    public Result<Map<String, Object>> refreshReadiness() {
        return Result.success(operationsCenterService.refreshReadiness());
    }

    @GetMapping("/read-only")
    @Operation(summary = "获取当前有效读写模式")
    public Result<Map<String, Object>> readOnly() {
        Map<String, Object> snapshot = operationsCenterService.readiness();
        return Result.success(Map.of(
                "readOnly", snapshot.getOrDefault("readOnly", true),
                "automaticReadOnly", snapshot.getOrDefault("automaticReadOnly", true),
                "maintenanceReadOnly", snapshot.getOrDefault("maintenanceReadOnly", false),
                "mode", snapshot.getOrDefault("mode", "READ_ONLY_DEGRADED"),
                "checkedAt", snapshot.getOrDefault("checkedAt", "")
        ));
    }

    private String currentActor() {
        return AuthContext.getCurrentUser() == null
                ? "unknown"
                : AuthContext.getCurrentUser().getUsername();
    }
}
