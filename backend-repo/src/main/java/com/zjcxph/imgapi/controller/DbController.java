package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.common.Result;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/db")
@Tag(name = "DB Controller", description = "数据库管理接口")
public class DbController {
    @GetMapping("/hello")
    public Result<String> hello() {
        return Result.success("db-api success, hello world");
    }

}
