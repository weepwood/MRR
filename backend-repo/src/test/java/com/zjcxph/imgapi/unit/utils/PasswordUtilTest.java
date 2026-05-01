package com.zjcxph.imgapi.unit.utils;

import com.zjcxph.imgapi.utils.PasswordUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;


import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PasswordUtil 密码工具测试")
class PasswordUtilTest {

    @Test
    @DisplayName("sha256 — 返回64位十六进制哈希")
    void sha256_returns64CharHex() {
        String hash = PasswordUtil.sha256("hello123");
        assertThat(hash).isNotNull().hasSize(64);
        assertThat(hash).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("sha256 — 相同输入产生相同哈希")
    void sha256_isDeterministic() {
        assertThat(PasswordUtil.sha256("test")).isEqualTo(PasswordUtil.sha256("test"));
    }

    @Test
    @DisplayName("sha256 — 不同输入产生不同哈希")
    void sha256_differentInputDifferentHash() {
        assertThat(PasswordUtil.sha256("abc")).isNotEqualTo(PasswordUtil.sha256("xyz"));
    }

    @Test
    @DisplayName("sha256 — null返回null")
    void sha256_null() {
        assertThat(PasswordUtil.sha256(null)).isNull();
    }

    @Test
    @DisplayName("sha256 — 空字符串返回空串哈希值")
    void sha256_empty() {
        assertThat(PasswordUtil.sha256("")).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    @DisplayName("matches — 密码匹配返回true")
    void matches_correctPassword() {
        String hash = PasswordUtil.sha256("mypassword");
        assertThat(PasswordUtil.matches("mypassword", hash)).isTrue();
    }

    @Test
    @DisplayName("matches — 密码不匹配返回false")
    void matches_wrongPassword() {
        String hash = PasswordUtil.sha256("correct");
        assertThat(PasswordUtil.matches("wrong", hash)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({",hash", "password,"})
    @DisplayName("matches — null参数返回false")
    void matches_nullArgs(String raw, String hash) {
        assertThat(PasswordUtil.matches(raw, hash)).isFalse();
    }

    @Test
    @DisplayName("encode — 返回64位十六进制字符串")
    void encode_returnsHash() {
        assertThat(PasswordUtil.encode("number")).hasSize(64);
    }
}
