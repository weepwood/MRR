package com.zjcxph.imgapi.unit.utils;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.utils.JwtUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtUtil JWT 工具测试")
class JwtUtilTest {

    @BeforeAll
    static void configureSecret() {
        JwtUtil.configure("unit-test-jwt-secret-key-with-at-least-32-characters");
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

        String token = JwtUtil.getToken(session);
        AuthSession parsed = JwtUtil.parseToken(token);

        assertThat(parsed.getId()).isEqualTo(1L);
        assertThat(parsed.getUsername()).isEqualTo("doctor1");
        assertThat(parsed.getDisplayName()).isEqualTo("张医生");
        assertThat(parsed.getRoleCode()).isEqualTo("DOCTOR");
        assertThat(parsed.getRoleName()).isEqualTo("医生");
        assertThat(parsed.getStatus()).isEqualTo("active");
        assertThat(parsed.getPermissions()).containsExactly("record:read", "record:edit");
    }

    @Test
    @DisplayName("getToken — null Session不抛异常")
    void getToken_nullSession() {
        String token = JwtUtil.getToken((AuthSession) null);
        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("parseToken — 解析仅含用户名的Token")
    void parseToken_usernameOnly() {
        String token = JwtUtil.getToken("nurse");
        AuthSession parsed = JwtUtil.parseToken(token);
        assertThat(parsed.getUsername()).isEqualTo("nurse");
        assertThat(parsed.getId()).isNull();
        assertThat(parsed.getDisplayName()).isNull();
        assertThat(parsed.getPermissions()).isEmpty();
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
}
