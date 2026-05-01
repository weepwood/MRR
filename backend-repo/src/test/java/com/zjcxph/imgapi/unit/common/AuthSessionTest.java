package com.zjcxph.imgapi.unit.common;

import com.zjcxph.imgapi.common.AuthSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuthSession 认证会话模型测试")
class AuthSessionTest {

    @Test
    @DisplayName("isAdmin — ADMIN角色返回true")
    void isAdmin_roleAdmin() {
        AuthSession s = new AuthSession();
        s.setRoleCode("ADMIN");
        assertThat(s.isAdmin()).isTrue();
    }

    @Test
    @DisplayName("isAdmin — 拥有user:manage权限返回true")
    void isAdmin_hasUserManage() {
        AuthSession s = new AuthSession();
        s.setPermissions(List.of("user:manage"));
        assertThat(s.isAdmin()).isTrue();
    }

    @Test
    @DisplayName("isAdmin — DOCTOR角色返回false")
    void isAdmin_doctor() {
        AuthSession s = new AuthSession();
        s.setRoleCode("DOCTOR");
        assertThat(s.isAdmin()).isFalse();
    }

    @Test
    @DisplayName("hasPermission — 包含权限返回true")
    void hasPermission_true() {
        AuthSession s = new AuthSession();
        s.setPermissions(List.of("record:read", "record:edit"));
        assertThat(s.hasPermission("record:read")).isTrue();
        assertThat(s.hasPermission("record:edit")).isTrue();
    }

    @Test
    @DisplayName("hasPermission — 不包含权限返回false")
    void hasPermission_false() {
        AuthSession s = new AuthSession();
        s.setPermissions(List.of("record:read"));
        assertThat(s.hasPermission("record:delete")).isFalse();
    }

    @Test
    @DisplayName("hasPermission — null权限返回false")
    void hasPermission_nullPermission() {
        AuthSession s = new AuthSession();
        assertThat(s.hasPermission(null)).isFalse();
    }

    @Test
    @DisplayName("setPermissions — null入参创建空列表")
    void setPermissions_null() {
        AuthSession s = new AuthSession();
        s.setPermissions(null);
        assertThat(s.getPermissions()).isEmpty();
    }
}
