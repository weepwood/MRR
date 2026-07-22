package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.dto.resp.PageResult;
import com.zjcxph.imgapi.entity.ImageMigrationLog;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.service.OssMigrationManagementService;
import com.zjcxph.imgapi.utils.PaginationUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OSS 迁移管理页面的受限查询接口。
 */
@RestController
@RequestMapping("/api/v1/oss/migration/management")
@Tag(name = "OSS Migration Management", description = "OSS 迁移管理页面查询接口")
@RequirePermissions({"record:read"})
public class OssMigrationManagementController {

    private static final int MAX_RECORD_LIMIT = 500;

    private final OssMigrationManagementService managementService;

    public OssMigrationManagementController(OssMigrationManagementService managementService) {
        this.managementService = managementService;
    }

    @Operation(summary = "查询当前可领取的待迁移记录")
    @GetMapping("/pending")
    public Result<Map<String, Object>> getPending(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) String folder,
            @RequestParam(required = false) String bah,
            @RequestParam(required = false) String sjh) {
        int safeLimit = safeLimit(limit);
        List<Scan> records = managementService.getPending(folder, bah, sjh, safeLimit + 1);
        return Result.success(recordListResponse(records, safeLimit));
    }

    @Operation(summary = "查询等待补齐上架号的记录")
    @GetMapping("/waiting-sjh")
    public Result<Map<String, Object>> getWaitingSjh(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) String folder,
            @RequestParam(required = false) String bah,
            @RequestParam(required = false) String sjh) {
        int safeLimit = safeLimit(limit);
        List<Scan> records = managementService.getWaitingSjh(folder, bah, sjh, safeLimit + 1);
        return Result.success(recordListResponse(records, safeLimit));
    }

    @Operation(summary = "按状态和 Scan ID 分页查询迁移日志")
    @GetMapping("/logs")
    public Result<PageResult<ImageMigrationLog>> getLogs(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer scanId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PaginationUtils.validatePageParams(page, size);
        List<ImageMigrationLog> logs = managementService.getLogs(status, scanId, page, size);
        long total = managementService.countLogs(status, scanId);
        return Result.success(PageResult.of(logs, total, page, size));
    }

    private int safeLimit(int limit) {
        return Math.max(1, Math.min(limit, MAX_RECORD_LIMIT));
    }

    private Map<String, Object> recordListResponse(List<Scan> records, int limit) {
        List<Scan> safeRecords = records == null ? List.of() : records;
        boolean hasMore = safeRecords.size() > limit;
        List<Scan> visibleRecords = hasMore ? safeRecords.subList(0, limit) : safeRecords;
        Map<String, Object> response = new HashMap<>();
        response.put("list", visibleRecords);
        response.put("returned", visibleRecords.size());
        response.put("limit", limit);
        response.put("hasMore", hasMore);
        return response;
    }
}
