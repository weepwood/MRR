package com.zjcxph.imgapi.unit.utils;

import com.zjcxph.imgapi.utils.PasswordUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PasswordUtil 密码工具测试")
class PasswordUtilTest {

    @Test
    @DisplayName("encode — 返回 bcrypt 哈希（$2a$12$ 前缀）")
    void encode_returnsBcryptHash() {
        String hash = PasswordUtil.encode("hello123");
        assertThat(hash).isNotNull().startsWith("$2a$12$");
    }

    @Test
    @DisplayName("encode — null 返回 null")
    void encode_null() {
        assertThat(PasswordUtil.encode(null)).isNull();
    }

    @Test
    @DisplayName("matches — 正确密码返回 true")
    void matches_correctPassword() {
        String hash = PasswordUtil.encode("mypassword");
        assertThat(PasswordUtil.matches("mypassword", hash)).isTrue();
    }

    @Test
    @DisplayName("matches — 错误密码返回 false")
    void matches_wrongPassword() {
        String hash = PasswordUtil.encode("correct");
        assertThat(PasswordUtil.matches("wrong", hash)).isFalse();
    }

    @Test
    @DisplayName("matches — null 参数返回 false")
    void matches_nullArgs() {
        String hash = PasswordUtil.encode("test");
        assertThat(PasswordUtil.matches(null, hash)).isFalse();
        assertThat(PasswordUtil.matches("test", null)).isFalse();
    }

    @Test
    @DisplayName("sha256 — 返回64位十六进制哈希（遗留兼容）")
    void sha256_returns64CharHex() {
        String hash = PasswordUtil.sha256("hello123");
        assertThat(hash).isNotNull().hasSize(64);
        assertThat(hash).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("sha256 — null 返回 null")
    void sha256_null() {
        assertThat(PasswordUtil.sha256(null)).isNull();
    }

    @Test
    @DisplayName("sha256Matches — 匹配 SHA-256 哈希")
    void sha256Matches_correct() {
        String hash = PasswordUtil.sha256("password");
        assertThat(PasswordUtil.sha256Matches("password", hash)).isTrue();
        assertThat(PasswordUtil.sha256Matches("wrong", hash)).isFalse();
    }
}
