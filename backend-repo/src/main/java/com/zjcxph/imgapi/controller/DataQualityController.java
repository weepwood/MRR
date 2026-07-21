package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.service.DataQualityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system/data-quality")
@Tag(name = "Data Quality Monitoring", description = "病案数据质量检查与异常明细")
@RequirePermissions({"system:read"})
public class DataQualityController {

    private final DataQualityService dataQualityService;

    public DataQualityController(DataQualityService dataQualityService) {
        this.dataQualityService = dataQualityService;
    }

    @Operation(summary = "获取最近一次数据质量检查摘要")
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        return Result.<Map<String, Object>>success("获取数据质量摘要成功")
                .data(dataQualityService.getSummary());
    }

    @Operation(summary = "获取最近一次数据质量异常样本")
    @GetMapping("/issues")
    public Result<List<Map<String, Object>>> issues(@RequestParam(defaultValue = "100") int limit) {
        return Result.<List<Map<String, Object>>>success("获取数据质量异常成功")
                .data(dataQualityService.getIssues(limit));
    }

    @Operation(summary = "获取数据质量异常详情")
    @GetMapping("/issues/{issueId}")
    public Result<Map<String, Object>> issue(@PathVariable long issueId) {
        return Result.<Map<String, Object>>success("获取数据质量异常详情成功")
                .data(dataQualityService.getIssue(issueId));
    }

    @Operation(summary = "预览异常的建议修复，不修改数据")
    @GetMapping("/issues/{issueId}/repair-preview")
    public Result<Map<String, Object>> repairPreview(@PathVariable long issueId) {
        return Result.<Map<String, Object>>success("获取修复预览成功")
                .data(dataQualityService.previewRepair(issueId));
    }

    @Operation(summary = "立即执行一次数据质量检查")
    @PostMapping("/run")
    public Result<Map<String, Object>> run() {
        try {
            return Result.<Map<String, Object>>success("数据质量检查完成")
                    .data(dataQualityService.runChecks("manual"));
        } catch (IllegalStateException e) {
            return Result.fail(e.getMessage());
        }
    }
}
