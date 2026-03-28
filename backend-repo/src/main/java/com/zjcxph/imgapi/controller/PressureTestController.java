package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.monitoring.PressureTestReport;
import com.zjcxph.imgapi.monitoring.PressureTestRequest;
import com.zjcxph.imgapi.monitoring.PressureTestService;
import com.zjcxph.imgapi.pojo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/monitoring-api/pressure-tests")
@Tag(name = "Pressure Test Monitoring", description = "Pressure test monitoring and history")
public class PressureTestController {

    private static final Logger logger = LoggerFactory.getLogger(PressureTestController.class);

    private final PressureTestService pressureTestService;

    public PressureTestController(PressureTestService pressureTestService) {
        this.pressureTestService = pressureTestService;
    }

    @Operation(summary = "Run a pressure test")
    @PostMapping("/run")
    public Result<Object> run(@Valid @RequestBody PressureTestRequest request) {
        logger.info("run pressure test: {}", request.getTargetUrl());
        PressureTestReport report = pressureTestService.run(request);
        return Result.success("pressure test completed").data(report);
    }

    @Operation(summary = "Get pressure test history")
    @GetMapping("/history")
    public Result<Object> history() {
        List<PressureTestReport> reports = pressureTestService.getHistory();
        return Result.success("ok").data(reports);
    }

    @Operation(summary = "Get latest pressure test result")
    @GetMapping("/latest")
    public Result<Object> latest() {
        return pressureTestService.getLatest()
                .<Result<Object>>map(report -> Result.success("ok").data(report))
                .orElseGet(() -> Result.success("ok").data(null));
    }

    @Operation(summary = "Get report by run id")
    @GetMapping("/{runId}")
    public Result<Object> getByRunId(@PathVariable String runId) {
        return pressureTestService.findByRunId(runId)
                .<Result<Object>>map(report -> Result.success("ok").data(report))
                .orElseGet(() -> Result.fail("pressure test record not found"));
    }

    @Operation(summary = "Clear pressure test history")
    @DeleteMapping("/history")
    public Result<Object> clearHistory() {
        pressureTestService.clearHistory();
        return Result.success("history cleared");
    }
}
