package com.zjcxph.imgapi.unit.common;

import com.zjcxph.imgapi.common.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Result 统一响应封装测试")
class ResultTest {

    @Test
    @DisplayName("success() 无参 — code=200, data=null")
    void success_noArgs() {
        Result<Object> result = Result.success();
        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("success(data) — 自动推断泛型")
    void success_withData() {
        Result<String> result = Result.success("hello");
        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isEqualTo("hello");
    }

    @Test
    @DisplayName("successPage — 分页数据")
    void successPage() {
        Result<List<String>> result = Result.successPage(List.of("a", "b"), 10);
        assertThat(result.getTotal()).isEqualTo(10);
        assertThat(result.getData()).hasSize(2);
    }

    @Test
    @DisplayName("fail() — code=400")
    void fail_default() {
        Result<Object> result = Result.fail();
        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.isFail()).isTrue();
    }

    @Test
    @DisplayName("unauthorized — code=401")
    void unauthorized() {
        Result<Object> result = Result.unauthorized("请登录");
        assertThat(result.getCode()).isEqualTo(401);
        assertThat(result.getMessage()).isEqualTo("请登录");
    }

    @Test
    @DisplayName("链式调用")
    void chained() {
        Result<String> result = Result.<String>success()
                .code(201)
                .message("已创建")
                .data("new-resource");
        assertThat(result.getCode()).isEqualTo(201);
        assertThat(result.getMessage()).isEqualTo("已创建");
        assertThat(result.getData()).isEqualTo("new-resource");
    }
}