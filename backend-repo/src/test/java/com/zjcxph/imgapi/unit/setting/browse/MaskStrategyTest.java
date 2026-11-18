package com.zjcxph.imgapi.unit.setting.browse;

import com.zjcxph.imgapi.setting.browse.MaskStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("脱敏策略测试")
class MaskStrategyTest {

    @Test
    @DisplayName("NONE 原值返回，null 保持 null")
    void none() {
        assertThat(MaskStrategy.NONE.apply("value")).isEqualTo("value");
        assertThat(MaskStrategy.NONE.apply(null)).isNull();
    }

    @Test
    @DisplayName("FULL 整列隐藏并声明 hidesColumn")
    void full() {
        assertThat(MaskStrategy.FULL.hidesColumn()).isTrue();
        assertThat(MaskStrategy.FULL.apply("secret")).isNull();
    }

    @Test
    @DisplayName("NAME 保留首字符，长度不变")
    void name() {
        assertThat(MaskStrategy.NAME.apply("张三")).isEqualTo("张*");
        assertThat(MaskStrategy.NAME.apply("欧阳修文")).isEqualTo("欧***");
        assertThat(MaskStrategy.NAME.apply("李")).isEqualTo("*");
        assertThat(MaskStrategy.NAME.apply("")).isEmpty();
    }

    @Test
    @DisplayName("ID_CARD 18 位保留前 6 后 4，不足 18 位全掩")
    void idCard() {
        // 明显非真实的占位值：避免隐私扫描器把测试样例误判为真实证件号。
        assertThat(MaskStrategy.ID_CARD.apply("000000TEST00001234")).isEqualTo("000000********1234");
        assertThat(MaskStrategy.ID_CARD.apply("000TEST0001234")).isEqualTo("*".repeat("000TEST0001234".length()));
    }

    @Test
    @DisplayName("CODE 保留后 4 位，4 位及以下全掩")
    void code() {
        assertThat(MaskStrategy.CODE.apply("12345678")).isEqualTo("****5678");
        assertThat(MaskStrategy.CODE.apply("1234")).isEqualTo("****");
        assertThat(MaskStrategy.CODE.apply("12")).isEqualTo("**");
    }

    @Test
    @DisplayName("PHONE 保留前 3 后 4")
    void phone() {
        assertThat(MaskStrategy.PHONE.apply("000TEST5678")).isEqualTo("000****5678");
        assertThat(MaskStrategy.PHONE.apply("123456")).isEqualTo("******");
    }

    @Test
    @DisplayName("PATH 只保留文件名")
    void path() {
        assertThat(MaskStrategy.PATH.apply("D:/images/2026/11/a.jpg")).isEqualTo("a.jpg");
        assertThat(MaskStrategy.PATH.apply("\\\\nas\\share\\b.png")).isEqualTo("b.png");
        assertThat(MaskStrategy.PATH.apply("plain.txt")).isEqualTo("plain.txt");
    }
}
