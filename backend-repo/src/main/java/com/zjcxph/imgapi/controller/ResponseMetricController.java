package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.dto.req.FrontendResponseMetricRequest;
import com.zjcxph.imgapi.dto.resp.ResponseMetricAnalysisDTO;
import com.zjcxph.imgapi.service.ResponseMetricService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/response-metrics")
@RequirePermissions({"system:read"})
@Tag(name = "Response Metrics", description = "前后端响应性能指标")
public class ResponseMetricController {

    private final ResponseMetricService responseMetricService;

    public ResponseMetricController(ResponseMetricService responseMetricService) {
        this.responseMetricService = responseMetricService;
    }

    @PostMapping("/frontend/batch")
    @Operation(summary = "批量记录前端响应指标")
    public Result<Void> saveFrontendMetrics(
            @RequestBody List<@Valid FrontendResponseMetricRequest> metrics
    ) {
        responseMetricService.saveFrontendMetrics(metrics);
        return Result.success();
    }

    @GetMapping("/analysis")
    @Operation(summary = "获取前后端响应分析")
    public Result<ResponseMetricAnalysisDTO> getAnalysis(
            @RequestParam(defaultValue = "7") @Min(1) @Max(90) int days
    ) {
        return Result.success(responseMetricService.getAnalysis(days));
    }
}
