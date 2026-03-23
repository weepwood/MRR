package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.pojo.Result;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/v1/db-api")
@Tag(name = "DB Controller", description = "数据库管理接口")
public class DbController {
    @GetMapping("/hello")
    public Result<Object> hello() {
        return Result.success("db-api success, hello world");
    }

    // TODO: 添加数据库管理接口
    // @PostMapping("/add")
    public Result<Object> add() {
        return Result.success("add success");
    }

    // @DeleteMapping("/delete")
    public Result<Object> delete() {
        return Result.success("delete success");
    }

    // @PostMapping("/update")
    public Result<Object> update() {
        return Result.success("update success");
    }

    // @PostMapping("/query/{id}")
    public Result<Object> query(@PathVariable String id) {
        return Result.success(id + " query success");
    }

}
