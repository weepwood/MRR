package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.dto.req.ArchiveBoxRecordRequest;
import com.zjcxph.imgapi.dto.resp.ArchiveBoxGroupDTO;
import com.zjcxph.imgapi.dto.resp.ArchiveBoxSummaryDTO;
import com.zjcxph.imgapi.dto.resp.PageResult;
import com.zjcxph.imgapi.entity.ArchiveBoxRecord;
import com.zjcxph.imgapi.service.ArchiveBoxRecordService;
import com.zjcxph.imgapi.utils.PaginationUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/archive-box-records")
@Tag(name = "Archive Box Management", description = "实体病案装箱位置管理接口")
@RequirePermissions({"record:read"})
public class ArchiveBoxRecordController {

    private static final int MAX_PAGE_SIZE = 1000;

    private final ArchiveBoxRecordService service;

    public ArchiveBoxRecordController(ArchiveBoxRecordService service) {
        this.service = service;
    }

    @Operation(summary = "分页查询装箱明细")
    @GetMapping
    public Result<PageResult<ArchiveBoxRecord>> findPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String bah,
            @RequestParam(required = false) String sjh,
            @RequestParam(required = false) String boxNo,
            @RequestParam(required = false) String status,
            @Parameter(description = "排序字段：bah/sjh/boxNo/status/createdAt/updatedAt")
            @RequestParam(required = false) String sortBy,
            @Parameter(description = "排序方向：asc/desc")
            @RequestParam(required = false) String sortOrder) {
        PaginationUtils.validatePageParams(page, size);
        size = Math.min(size, MAX_PAGE_SIZE);
        List<ArchiveBoxRecord> list = service.findPage(
                page, size, keyword, bah, sjh, boxNo, status, sortBy, sortOrder);
        long total = service.countPage(keyword, bah, sjh, boxNo, status);
        return Result.success(PageResult.of(list, total, page, size));
    }

    @Operation(summary = "根据 ID 查询装箱记录")
    @GetMapping("/{id}")
    public Result<ArchiveBoxRecord> findById(@PathVariable Long id) {
        ArchiveBoxRecord record = service.findById(id);
        return record == null ? Result.fail("未找到装箱记录") : Result.success(record);
    }

    @Operation(summary = "根据病案号或上架号反查箱号")
    @GetMapping("/record/{code}")
    public Result<List<ArchiveBoxRecord>> findByRecordCode(@PathVariable String code) {
        return Result.success(service.findByRecordCode(code));
    }

    @Operation(summary = "查询指定箱号内的全部病案")
    @GetMapping("/box/{boxNo}")
    public Result<List<ArchiveBoxRecord>> findByBoxNo(@PathVariable String boxNo) {
        return Result.success(service.findByBoxNo(boxNo));
    }

    @Operation(summary = "获取装箱总体摘要")
    @GetMapping("/summary")
    public Result<ArchiveBoxSummaryDTO> getSummary() {
        return Result.success(service.getSummary());
    }

    @Operation(summary = "分页查询箱号汇总")
    @GetMapping("/boxes")
    public Result<PageResult<ArchiveBoxGroupDTO>> findBoxGroups(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        PaginationUtils.validatePageParams(page, size);
        size = Math.min(size, MAX_PAGE_SIZE);
        List<ArchiveBoxGroupDTO> list = service.findBoxGroups(page, size, keyword);
        long total = service.countBoxGroups(keyword);
        return Result.success(PageResult.of(list, total, page, size));
    }

    @Operation(summary = "新增装箱记录")
    @PostMapping
    @RequirePermissions({"record:edit"})
    public Result<ArchiveBoxRecord> create(@Valid @RequestBody ArchiveBoxRecordRequest request) {
        try {
            return Result.success(service.create(request));
        } catch (IllegalArgumentException exception) {
            return Result.fail(exception.getMessage());
        }
    }

    @Operation(summary = "更新装箱记录")
    @PutMapping("/{id}")
    @RequirePermissions({"record:edit"})
    public Result<ArchiveBoxRecord> update(
            @PathVariable Long id,
            @Valid @RequestBody ArchiveBoxRecordRequest request) {
        try {
            ArchiveBoxRecord updated = service.update(id, request);
            return updated == null ? Result.fail("未找到装箱记录") : Result.success(updated);
        } catch (IllegalArgumentException exception) {
            return Result.fail(exception.getMessage());
        }
    }

    @Operation(summary = "删除装箱记录")
    @DeleteMapping("/{id}")
    @RequirePermissions({"record:edit"})
    public Result<String> delete(@PathVariable Long id) {
        return service.delete(id) ? Result.success("删除成功") : Result.fail("未找到装箱记录");
    }
}
