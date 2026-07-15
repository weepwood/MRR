package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.entity.ArchiveRecord;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.service.ArchiveRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/archive-records")
@Tag(name = "Archive Records", description = "病案主数据及关联影像查询")
@RequirePermissions({"record:read"})
public class ArchiveRecordController {

    private final ArchiveRecordService archiveRecordService;

    public ArchiveRecordController(ArchiveRecordService archiveRecordService) {
        this.archiveRecordService = archiveRecordService;
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据病案主键查询病案")
    public Result<ArchiveRecord> findById(
            @PathVariable
            @Parameter(description = "病案主表 ID", example = "1")
            Long id
    ) {
        ArchiveRecord archive = archiveRecordService.findById(id);
        return archive == null ? Result.fail("未找到病案") : Result.success(archive);
    }

    @GetMapping("/resolve")
    @Operation(summary = "根据病案号或上架号解析病案")
    public Result<ArchiveRecord> resolve(
            @RequestParam(required = false) String bah,
            @RequestParam(required = false) String sjh
    ) {
        if ((bah == null || bah.isBlank()) && (sjh == null || sjh.isBlank())) {
            return Result.fail("病案号和上架号不能同时为空");
        }

        ArchiveRecord archive = archiveRecordService.findByCode(bah, sjh);
        return archive == null ? Result.fail("未找到唯一匹配的病案") : Result.success(archive);
    }

    @GetMapping("/{id}/scans")
    @Operation(summary = "查询病案关联的扫描影像")
    public Result<List<Scan>> findScans(
            @PathVariable
            @Parameter(description = "病案主表 ID", example = "1")
            Long id
    ) {
        ArchiveRecord archive = archiveRecordService.findById(id);
        if (archive == null) {
            return Result.fail("未找到病案");
        }
        return Result.success(archiveRecordService.findScans(id));
    }
}
