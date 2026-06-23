package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.testing.ApiTestRequest;
import com.zjcxph.imgapi.testing.ApiTestResponse;
import com.zjcxph.imgapi.testing.DataCheckItem;
import com.zjcxph.imgapi.testing.SmokeTestItem;
import com.zjcxph.imgapi.testing.TestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/testing")
@Tag(name = "Testing Center", description = "冒烟测试、接口调试、数据完整性检查")
@RequirePermissions({"test:read"})
public class TestController {

    private static final Logger logger = LoggerFactory.getLogger(TestController.class);

    private final TestService testService;

    public TestController(TestService testService) {
        this.testService = testService;
    }

    @Operation(summary = "执行全量冒烟测试")
    @GetMapping("/smoke")
    public Result<List<SmokeTestItem>> smoke() {
        logger.info("Running smoke tests");
        List<SmokeTestItem> items = testService.runSmoke();
        return Result.success(items);
    }

    @Operation(summary = "调试指定 API 接口")
    @PostMapping("/api-test")
    public Result<ApiTestResponse> apiTest(@Valid @RequestBody ApiTestRequest request) {
        logger.info("API test: {} {}", request.getMethod(), request.getUrl());
        ApiTestResponse response = testService.runApiTest(request);
        return Result.success(response);
    }

    @Operation(summary = "执行数据完整性检查")
    @GetMapping("/data-check")
    public Result<List<DataCheckItem>> dataCheck() {
        logger.info("Running data integrity checks");
        List<DataCheckItem> items = testService.runDataCheck();
        return Result.success(items);
    }
}
