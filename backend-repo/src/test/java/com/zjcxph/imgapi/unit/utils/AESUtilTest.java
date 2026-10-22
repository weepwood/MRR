package com.zjcxph.imgapi.unit.utils;

import com.zjcxph.imgapi.utils.AESUtil;
import org.apache.commons.codec.DecoderException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AESUtil AES 加解密工具测试")
class AESUtilTest {

    @Test
    @DisplayName("parseEncryptID — 解析JSON格式密文")
    void parseEncryptID_json() {
        String json = "{\"ciphertext\":\"abc123\",\"iv\":\"iv456\"}";
        String[] result = AESUtil.parseEncryptID(json);
        assertThat(result).containsExactly("abc123", "iv456");
    }

    @Test
    @DisplayName("parseEncryptID — 解析下划线格式密文")
    void parseEncryptID_underscore() {
        String[] result = AESUtil.parseEncryptID("cipher_iv");
        assertThat(result).containsExactly("cipher", "iv");
    }

    @Test
    @DisplayName("parseEncryptID — 无效格式抛异常")
    void parseEncryptID_invalid() {
        assertThatThrownBy(() -> AESUtil.parseEncryptID("invalid"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("解析加密ID失败");
    }

    @Test
    @DisplayName("parseEncryptIDWithTimestamp — 解析含时间戳的JSON")
    void parseEncryptIDWithTimestamp_json() {
        String json = "{\"ciphertext\":\"cc\",\"iv\":\"ivv\",\"timestamp\":\"1700000000\"}";
        String[] result = AESUtil.parseEncryptIDWithTimestamp(json);
        assertThat(result).containsExactly("cc", "ivv", "1700000000");
    }

    @Test
    @DisplayName("parseEncryptIDWithTimestamp — 非JSON格式抛异常")
    void parseEncryptIDWithTimestamp_nonJson() {
        assertThatThrownBy(() -> AESUtil.parseEncryptIDWithTimestamp("cipher_iv"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("解析加密ID失败");
    }

    @Test
    @DisplayName("decryptIdCard — ASCII 十六进制密文应还原合成数据")
    void decryptIdCard_asciiHex() {
        assertThat(AESUtil.decryptIdCard(
                "32011fee299ca608c221f90ac36adc3dbbfe96a0517bea6fb8bcdd04538c1c1f",
                "000102030405060708090a0b0c0d0e0f", "1", "test-key"))
                .isEqualTo("SYNTHETIC-ID-001");
    }

    @Test
    @DisplayName("decryptIdCard — 非法十六进制密文应拒绝")
    void decryptIdCard_invalidHex() {
        assertThatThrownBy(() -> AESUtil.decryptIdCard("badhex", "badhex", "1", "test-key"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("parseEncryptID — JSON中缺失字段抛异常")
    void parseEncryptID_missingField() {
        assertThatThrownBy(() -> AESUtil.parseEncryptID("{\"foo\":\"bar\"}"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("decryptIdCard — 密钥为空时 fail-closed 而不是静默补零")
    void decryptIdCard_blankKeyFailsClosed() {
        assertThatThrownBy(() -> AESUtil.decryptIdCard("00", "00", "1", "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("密钥未配置");
    }

    @Test
    @DisplayName("decryptIdCard — 非 ASCII 十六进制密文应在解码阶段拒绝")
    void decryptIdCard_rejectsNonAsciiHex() {
        assertThatThrownBy(() -> AESUtil.decryptIdCard(
                "Ａ0", "00000000000000000000000000000000", "1", "test-key"))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(DecoderException.class);
    }

    @Test
    @DisplayName("decryptIdCardWithTimestamp — 密钥为空时 fail-closed 而不是静默补零")
    void decryptIdCardWithTimestamp_blankKeyFailsClosed() {
        assertThatThrownBy(() -> AESUtil.decryptIdCardWithTimestamp("00", "00", "1", "1700000000", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("密钥未配置");
    }

    @Test
    @DisplayName("不再保留可用的硬编码默认密钥常量")
    void hasNoHardcodedDefaultKey() {
        assertThat(Arrays.stream(AESUtil.class.getDeclaredFields()).map(Field::getName))
                .doesNotContain("SECRET_KEY");
    }
}
