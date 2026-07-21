package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.service.DataRelationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system/data-relations")
@Tag(name = "Data Relation Workbench", description = "核心业务表关系总览和病案关系下钻")
@RequirePermissions({"system:read"})
public class DataRelationController {

    private final DataRelationService dataRelationService;

    public DataRelationController(DataRelationService dataRelationService) {
        this.dataRelationService = dataRelationService;
    }

    @Operation(summary = "获取核心业务表关系总览")
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.<Map<String, Object>>success("获取数据关系总览成功")
                .data(dataRelationService.getOverview());
    }

    @Operation(summary = "按 archive_id、BAH 或 SJH 搜索病案主档")
    @GetMapping("/archives/search")
    public Result<List<Map<String, Object>>> search(
            @Parameter(description = "ARCHIVE_ID、BAH 或 SJH")
            @RequestParam(defaultValue = "BAH") String type,
            @RequestParam String value,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return Result.<List<Map<String, Object>>>success("搜索病案主档成功")
                .data(dataRelationService.searchArchives(type, value, limit));
    }

    @Operation(summary = "获取一份病案的跨表关系详情")
    @GetMapping("/archives/{archiveId}")
    public Result<Map<String, Object>> archiveRelation(@PathVariable long archiveId) {
        return Result.<Map<String, Object>>success("获取病案关系详情成功")
                .data(dataRelationService.getArchiveRelation(archiveId));
    }
}
