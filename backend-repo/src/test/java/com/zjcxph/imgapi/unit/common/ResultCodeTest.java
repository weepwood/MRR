package com.zjcxph.imgapi.unit.common;

import com.zjcxph.imgapi.common.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ResultCode 状态码枚举测试")
class ResultCodeTest {

    @ParameterizedTest
    @EnumSource(ResultCode.class)
    @DisplayName("所有枚举值的 code 应为正整数、message 非空且 HTTP 映射有效")
    void allCodes_valid(ResultCode code) {
        assertThat(code.getCode()).isPositive();
        assertThat(code.getMessage()).isNotBlank();
        assertThat(code.getHttpStatus()).isNotNull();
    }

    @Test
    @DisplayName("SUCCESS 应为 200")
    void success_is200() {
        assertThat(ResultCode.SUCCESS.getCode()).isEqualTo(200);
        assertThat(ResultCode.SUCCESS.getMessage()).isEqualTo("操作成功");
        assertThat(ResultCode.SUCCESS.getHttpStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("业务错误码应在 1000 以上，HTTP 标准码应在 200-599 区间")
    void codes_groupedByRange() {
        assertThat(ResultCode.USER_NOT_FOUND.getCode()).isBetween(1000, 1999);
        assertThat(ResultCode.USER_DISABLED.getCode()).isBetween(1000, 1999);
        assertThat(ResultCode.PASSWORD_WRONG.getCode()).isBetween(1000, 1999);
        assertThat(ResultCode.OSS_UPLOAD_FAIL.getCode()).isBetween(2000, 2999);
        assertThat(ResultCode.MIGRATION_FAIL.getCode()).isBetween(2000, 2999);
    }

    @Test
    @DisplayName("各业务码互不相同（避免重复定义）")
    void codes_unique() {
        long uniqueCount = java.util.Arrays.stream(ResultCode.values())
                .map(ResultCode::getCode)
                .distinct()
                .count();
        assertThat(uniqueCount).isEqualTo(ResultCode.values().length);
    }

    @Test
    @DisplayName("标准 HTTP 状态码直接解析")
    void standardHttpCodes_resolveDirectly() {
        assertThat(ResultCode.resolveHttpStatus(400)).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ResultCode.resolveHttpStatus(409)).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ResultCode.resolveHttpStatus(410)).isEqualTo(HttpStatus.GONE);
        assertThat(ResultCode.resolveHttpStatus(422)).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ResultCode.resolveHttpStatus(428)).isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
        assertThat(ResultCode.resolveHttpStatus(429)).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ResultCode.resolveHttpStatus(503)).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("业务码映射为对应 HTTP 状态")
    void businessCodes_resolveToHttpStatus() {
        assertThat(ResultCode.resolveHttpStatus(ResultCode.USER_NOT_FOUND.getCode()))
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ResultCode.resolveHttpStatus(ResultCode.USER_DISABLED.getCode()))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ResultCode.resolveHttpStatus(ResultCode.PASSWORD_WRONG.getCode()))
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ResultCode.resolveHttpStatus(ResultCode.OSS_UPLOAD_FAIL.getCode()))
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("未知或空业务码按 500 处理")
    void unknownCodes_failClosed() {
        assertThat(ResultCode.resolveHttpStatus(9999)).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(ResultCode.resolveHttpStatus(null)).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
