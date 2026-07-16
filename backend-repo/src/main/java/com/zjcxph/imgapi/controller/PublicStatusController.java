package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.service.SystemAvailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/public/status")
@Tag(name = "Public System Status", description = "MRR 服务运行状态与历史可用率")
public class PublicStatusController {

    private final SystemAvailabilityService systemAvailabilityService;

    public PublicStatusController(SystemAvailabilityService systemAvailabilityService) {
        this.systemAvailabilityService = systemAvailabilityService;
    }

    @Operation(summary = "获取当前状态与可用率汇总")
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary(@RequestParam(defaultValue = "90") int days) {
        return Result.<Map<String, Object>>success("获取服务状态汇总成功")
                .data(systemAvailabilityService.getSummary(days));
    }

    @Operation(summary = "获取每日可用率")
    @GetMapping("/daily")
    public Result<List<Map<String, Object>>> daily(@RequestParam(defaultValue = "90") int days) {
        return Result.<List<Map<String, Object>>>success("获取每日可用率成功")
                .data(systemAvailabilityService.getDaily(days));
    }

    @Operation(summary = "获取分钟级运行记录")
    @GetMapping("/minutes")
    public Result<List<Map<String, Object>>> minutes(@RequestParam(required = false) LocalDate date) {
        return Result.<List<Map<String, Object>>>success("获取分钟级运行记录成功")
                .data(systemAvailabilityService.getMinuteAvailability(date));
    }

    @Operation(summary = "获取异常运行区间")
    @GetMapping("/incidents")
    public Result<List<Map<String, Object>>> incidents(@RequestParam(defaultValue = "90") int days) {
        return Result.<List<Map<String, Object>>>success("获取异常运行区间成功")
                .data(systemAvailabilityService.getIncidents(days));
    }

    @Operation(summary = "检查后端 HTTP 服务")
    @GetMapping("/ping")
    public Result<Map<String, Object>> ping() {
        return Result.<Map<String, Object>>success("服务正常")
                .data(systemAvailabilityService.ping());
    }
}
