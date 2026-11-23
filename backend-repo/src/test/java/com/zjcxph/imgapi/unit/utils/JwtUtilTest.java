package com.zjcxph.imgapi.unit.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.utils.JwtUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtUtil JWT 工具测试")
class JwtUtilTest {

    private static final String TEST_SECRET = "unit-test-jwt-secret-key-with-at-least-32-characters";

    @BeforeAll
    static void configureSecret() {
        JwtUtil.configure(TEST_SECRET);
    }

    @Test
    @DisplayName("getToken — 根据用户名生成非空Token")
    void getToken_byUsername() {
        String token = JwtUtil.getToken("admin");
        assertThat(token).isNotBlank();
        assertThat(token).contains(".");
    }

    @Test
    @DisplayName("getToken — 根据AuthSession生成Token并解析回同一Session")
    void getToken_and_parseToken_roundTrip() {
        AuthSession session = new AuthSession();
        session.setId(1L);
        session.setUsername("doctor1");
        session.setDisplayName("张医生");
        session.setRoleCode("DOCTOR");
        session.setRoleName("医生");
        session.setStatus("active");
        session.setPermissions(List.of("record:read", "record:edit"));
        session.setMustChangePassword(true);
        session.setPasswordVersion(4);

        String token = JwtUtil.getToken(session);
        AuthSession parsed = JwtUtil.parseToken(token);

        assertThat(parsed.getId()).isEqualTo(1L);
        assertThat(parsed.getUsername()).isEqualTo("doctor1");
        assertThat(parsed.getDisplayName()).isEqualTo("张医生");
        assertThat(parsed.getRoleCode()).isEqualTo("DOCTOR");
        assertThat(parsed.getRoleName()).isEqualTo("医生");
        assertThat(parsed.getStatus()).isEqualTo("active");
        assertThat(parsed.getPermissions()).containsExactly("record:read", "record:edit");
        assertThat(parsed.isPasswordChangeRequired()).isTrue();
        assertThat(parsed.getPasswordVersion()).isEqualTo(4);
    }

    @Test
    @DisplayName("getToken — null Session不抛异常")
    void getToken_nullSession() {
        String token = JwtUtil.getToken((AuthSession) null);
        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("parseToken — 解析仅含用户名的Token并使用默认密码版本")
    void parseToken_usernameOnly() {
        String token = JwtUtil.getToken("nurse");
        AuthSession parsed = JwtUtil.parseToken(token);
        assertThat(parsed.getUsername()).isEqualTo("nurse");
        assertThat(parsed.getId()).isNull();
        assertThat(parsed.getDisplayName()).isNull();
        assertThat(parsed.getPermissions()).isEmpty();
        assertThat(parsed.isPasswordChangeRequired()).isFalse();
        assertThat(parsed.getPasswordVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("parseToken — 解析包含权限的Token")
    void parseToken_withPermissions() {
        AuthSession session = new AuthSession();
        session.setUsername("admin");
        session.setPermissions(List.of("user:manage", "role:read", "record:manage"));
        String token = JwtUtil.getToken(session);

        AuthSession parsed = JwtUtil.parseToken(token);
        assertThat(parsed.getPermissions()).contains("user:manage", "role:read");
    }

    @Test
    @DisplayName("parseToken — 超出 Instant 范围的 NumericDate 应受控拒绝")
    void parseToken_rejectsOutOfRangeNumericDate() {
        for (long expiration : new long[] {Long.MAX_VALUE, Long.MIN_VALUE}) {
            String token = JWT.create()
                    .withClaim("tokenType", JwtUtil.ACCESS_TOKEN_TYPE)
                    .withClaim("exp", expiration)
                    .sign(Algorithm.HMAC256(TEST_SECRET));

            assertThatThrownBy(() -> JwtUtil.parseToken(token))
                    .isInstanceOf(JWTDecodeException.class)
                    .hasMessageContaining("exp");
        }
    }

    @Test
    @DisplayName("verifyToken — 一次验签返回类型、编号、过期时间与会话")
    void verifyToken_returnsAllClaimsFromSingleVerification() {
        AuthSession session = new AuthSession();
        session.setId(7L);
        session.setUsername("doctor7");
        session.setPasswordVersion(3);
        session.setPermissions(List.of("record:read"));
        long before = System.currentTimeMillis();

        String token = JwtUtil.getToken(session, 60_000L, JwtUtil.ACCESS_TOKEN_TYPE);
        JwtUtil.VerifiedToken verified = JwtUtil.verifyToken(token);

        assertThat(verified.tokenType()).isEqualTo(JwtUtil.ACCESS_TOKEN_TYPE);
        assertThat(verified.jti()).isEqualTo(JwtUtil.getJti(token));
        assertThat(verified.expiresAtMillis()).isEqualTo(JwtUtil.getExpirationMillis(token));
        assertThat(verified.expiresAtMillis()).isBetween(before, before + 61_000L);
        assertThat(verified.session().getId()).isEqualTo(7L);
        assertThat(verified.session().getUsername()).isEqualTo("doctor7");
        assertThat(verified.session().getPasswordVersion()).isEqualTo(3);
        assertThat(verified.session().getPermissions()).containsExactly("record:read");
    }

    @Test
    @DisplayName("verifyToken — 其他密钥签发的令牌被拒绝")
    void verifyToken_rejectsForeignSignature() {
        String forged = JWT.create()
                .withClaim("tokenType", JwtUtil.ACCESS_TOKEN_TYPE)
                .withClaim("username", "admin")
                .sign(Algorithm.HMAC256("another-secret-key-with-at-least-32-characters"));

        assertThatThrownBy(() -> JwtUtil.verifyToken(forged)).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("deriveKey — 按用途隔离、可复现，且随 JWT 密钥轮换变化")
    void deriveKey_isPurposeSeparatedAndRotatesWithSecret() {
        byte[] image = JwtUtil.deriveKey("MRR-IMAGE-ACCESS-V1");

        assertThat(image).hasSize(32).isEqualTo(JwtUtil.deriveKey("MRR-IMAGE-ACCESS-V1"));
        assertThat(image).isNotEqualTo(JwtUtil.deriveKey("MRR-OTHER-PURPOSE"));
        assertThat(new String(image, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain(TEST_SECRET);
        assertThatThrownBy(() -> JwtUtil.deriveKey(" ")).isInstanceOf(IllegalArgumentException.class);

        try {
            JwtUtil.configure("rotated-secret-for-derive-key-test-0123456789");
            assertThat(JwtUtil.deriveKey("MRR-IMAGE-ACCESS-V1")).isNotEqualTo(image);
        } finally {
            JwtUtil.configure(TEST_SECRET);
        }
    }
}
