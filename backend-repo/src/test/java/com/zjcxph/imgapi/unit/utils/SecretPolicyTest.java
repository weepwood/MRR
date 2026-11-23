package com.zjcxph.imgapi.unit.utils;

import com.zjcxph.imgapi.utils.SecretPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SecretPolicy 密钥配置策略")
class SecretPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "CHANGE_ME_WITH_A_LONG_RANDOM_VALUE",
            "CHANGE_ME_WITH_AT_LEAST_32_BYTES",
            "CHANGE_ME_WITH_A_DIFFERENT_LONG_RANDOM_HMAC_SECRET",
            "replace-with-a-random-jwt-signing-secret",
            "replace-with-a-random-secret-of-at-least-32-characters",
            "replace-with-your-own-32-byte-secret-key",
            "  change_me_lowercase_is_still_a_placeholder  "
    })
    @DisplayName("仓库模板中的占位值全部被拒绝")
    void rejectsRepositoryPlaceholders(String placeholder) {
        assertThat(SecretPolicy.isPlaceholder(placeholder)).isTrue();
        assertThat(SecretPolicy.rejectionReason(placeholder)).isEqualTo("仍为模板占位值");
    }

    @Test
    @DisplayName("重复字符构成的长密钥被拒绝")
    void rejectsLowDiversitySecrets() {
        assertThat(SecretPolicy.rejectionReason("a".repeat(64))).contains("字符种类过少");
        assertThat(SecretPolicy.rejectionReason("abababababababababababababababab")).contains("字符种类过少");
    }

    @Test
    @DisplayName("随机生成的 hex / base64url 密钥通过")
    void acceptsRandomSecrets() {
        assertThat(SecretPolicy.rejectionReason("9f2c4e7a1b0d83f6c5a2e9b4d7f1038e")).isNull();
        assertThat(SecretPolicy.rejectionReason("q3V_8sLx-2NfW0eZ7kRb1TyHcPjUoAmD4gIvE6nQ9wS")).isNull();
        assertThat(SecretPolicy.rejectionReason("backend-test-jwt-secret-key-with-at-least-32-characters")).isNull();
    }

    @Test
    @DisplayName("拒绝原因不回显密钥内容")
    void rejectionReasonNeverEchoesTheSecret() {
        String placeholder = "CHANGE_ME_WITH_A_LONG_RANDOM_VALUE";
        assertThat(SecretPolicy.rejectionReason(placeholder)).doesNotContain(placeholder);
    }

    @Test
    @DisplayName("null 不视为占位值（是否必填由调用方决定）")
    void nullIsNotAPlaceholder() {
        assertThat(SecretPolicy.isPlaceholder(null)).isFalse();
        assertThat(SecretPolicy.hasLowDiversity(null)).isFalse();
        assertThat(SecretPolicy.rejectionReason(null)).isNull();
    }
}
