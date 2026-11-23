package com.zjcxph.imgapi.config;

import com.zjcxph.imgapi.utils.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtSecretConfiguration 启动密钥校验")
class JwtSecretConfigurationTest {

    private static final String VALID_JWT = "jwt-secret-configuration-test-0123456789abcdef";

    @AfterEach
    void restoreSecret() {
        JwtUtil.configure(VALID_JWT);
    }

    @Test
    @DisplayName("AES 密钥留空时允许启动（对应功能按需 fail-closed）")
    void blankAesKeyIsAllowed() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("JWT_SECRET_KEY", VALID_JWT)
                .withProperty("aes.secret.key", "");

        assertThatCode(() -> new JwtSecretConfiguration(environment)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("AES 密钥为模板占位值时阻止启动")
    void placeholderAesKeyBlocksStartup() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("JWT_SECRET_KEY", VALID_JWT)
                .withProperty("aes.secret.key", "CHANGE_ME_WITH_AT_LEAST_32_BYTES");

        assertThatThrownBy(() -> new JwtSecretConfiguration(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("aes.secret.key")
                .hasMessageNotContaining("CHANGE_ME_WITH_AT_LEAST_32_BYTES");
    }

    @Test
    @DisplayName("JWT 密钥为模板占位值时阻止启动")
    void placeholderJwtKeyBlocksStartup() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("JWT_SECRET_KEY", "CHANGE_ME_WITH_A_LONG_RANDOM_VALUE");

        assertThatThrownBy(() -> new JwtSecretConfiguration(environment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("占位值");
    }
}
