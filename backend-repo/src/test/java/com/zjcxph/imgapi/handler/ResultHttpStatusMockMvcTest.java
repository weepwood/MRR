package com.zjcxph.imgapi.handler;

import com.zjcxph.imgapi.common.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ResultHttpStatusMockMvcTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new StatusController())
                .setControllerAdvice(new ResultHttpStatusAdvice())
                .build();
    }

    @Test
    void shouldReturnBadRequestInsteadOfHttp200() throws Exception {
        mockMvc.perform(get("/contract/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void shouldReturnUnauthorizedInsteadOfHttp200() throws Exception {
        mockMvc.perform(get("/contract/unauthorized"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void shouldReturnNotFoundInsteadOfHttp200() throws Exception {
        mockMvc.perform(get("/contract/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void shouldReturnGoneAndServiceUnavailable() throws Exception {
        mockMvc.perform(get("/contract/gone"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value(410));

        mockMvc.perform(get("/contract/unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503));
    }

    @Test
    void shouldPreserveExplicitErrorStatus() throws Exception {
        mockMvc.perform(get("/contract/explicit-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(400));
    }

    @RestController
    static class StatusController {

        @GetMapping("/contract/bad-request")
        Result<Void> badRequest() {
            return Result.fail("参数错误");
        }

        @GetMapping("/contract/unauthorized")
        Result<Void> unauthorized() {
            return Result.unauthorized("请先登录");
        }

        @GetMapping("/contract/not-found")
        Result<Void> notFound() {
            return Result.notFound("资源不存在");
        }

        @GetMapping("/contract/gone")
        Result<Void> gone() {
            return Result.fail(410, "接口已停用");
        }

        @GetMapping("/contract/unavailable")
        Result<Void> unavailable() {
            return Result.fail(503, "服务不可用");
        }

        @GetMapping("/contract/explicit-conflict")
        ResponseEntity<Result<Void>> explicitConflict() {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Result.fail("冲突"));
        }
    }
}
