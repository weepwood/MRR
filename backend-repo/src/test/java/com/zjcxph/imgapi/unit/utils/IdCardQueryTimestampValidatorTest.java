package com.zjcxph.imgapi.unit.utils;

import com.zjcxph.imgapi.utils.IdCardQueryTimestampValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("IdCardQueryTimestampValidator 加密身份证查询时间戳校验")
class IdCardQueryTimestampValidatorTest {

    private static final long NOW = 1_700_000_000L;
    private static final int TOLERANCE = 300;

    @Test
    @DisplayName("窗口内的时间戳通过")
    void acceptsTimestampInsideWindow() {
        assertThatCode(() -> IdCardQueryTimestampValidator.validate(
                String.valueOf(NOW - TOLERANCE), TOLERANCE, NOW))
                .doesNotThrowAnyException();
        assertThatCode(() -> IdCardQueryTimestampValidator.validate(
                String.valueOf(NOW + TOLERANCE), TOLERANCE, NOW))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("过期时间戳被拒绝（重放防护）")
    void rejectsExpiredTimestamp() {
        assertThatThrownBy(() -> IdCardQueryTimestampValidator.validate(
                String.valueOf(NOW - TOLERANCE - 1), TOLERANCE, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已过期");
    }

    @Test
    @DisplayName("超前于窗口的时间戳被拒绝")
    void rejectsFutureTimestampBeyondWindow() {
        assertThatThrownBy(() -> IdCardQueryTimestampValidator.validate(
                String.valueOf(NOW + TOLERANCE + 1), TOLERANCE, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已过期");
    }

    @Test
    @DisplayName("毫秒时间戳按毫秒解析")
    void acceptsMillisecondTimestamp() {
        assertThatCode(() -> IdCardQueryTimestampValidator.validate(
                String.valueOf(NOW * 1000L), TOLERANCE, NOW))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("非数字时间戳被拒绝")
    void rejectsNonNumericTimestamp() {
        assertThatThrownBy(() -> IdCardQueryTimestampValidator.validate("ts", TOLERANCE, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("格式无效");
    }

    @Test
    @DisplayName("空时间戳被拒绝")
    void rejectsBlankTimestamp() {
        assertThatThrownBy(() -> IdCardQueryTimestampValidator.validate("  ", TOLERANCE, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("缺少时间戳");
        assertThatThrownBy(() -> IdCardQueryTimestampValidator.validate(null, TOLERANCE, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("缺少时间戳");
    }

    @Test
    @DisplayName("容差配置为 0 或负数时关闭校验（运维逃生阀）")
    void skipsValidationWhenDisabled() {
        assertThatCode(() -> IdCardQueryTimestampValidator.validate("0", 0, NOW))
                .doesNotThrowAnyException();
        assertThatCode(() -> IdCardQueryTimestampValidator.validate("not-a-timestamp", -1, NOW))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("解析结果统一为秒级时间戳")
    void parsesToEpochSeconds() {
        assertThat(IdCardQueryTimestampValidator.parseEpochSeconds("1700000000")).isEqualTo(NOW);
        assertThat(IdCardQueryTimestampValidator.parseEpochSeconds("1700000000000")).isEqualTo(NOW);
    }
}
