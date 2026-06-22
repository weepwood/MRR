package com.zjcxph.imgapi.unit.common;

import com.zjcxph.imgapi.common.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ResultCode 状态码枚举测试")
class ResultCodeTest {

    @ParameterizedTest
    @EnumSource(ResultCode.class)
    @DisplayName("所有枚举值的 code 应为正整数、message 非空")
    void allCodes_valid(ResultCode code) {
        assertThat(code.getCode()).isPositive();
        assertThat(code.getMessage()).isNotBlank();
    }

    @Test
    @DisplayName("SUCCESS 应为 200")
    void success_is200() {
        assertThat(ResultCode.SUCCESS.getCode()).isEqualTo(200);
        assertThat(ResultCode.SUCCESS.getMessage()).isEqualTo("操作成功");
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
}
