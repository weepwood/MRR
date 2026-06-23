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
    @DisplayName("isAdmin — 非ADMIN角色即使有user:manage也返回false")
    void isAdmin_hasUserManage_butNotAdmin() {
        AuthSession s = new AuthSession();
        s.setRoleCode("DOCTOR");
        s.setPermissions(List.of("user:manage"));
        assertThat(s.isAdmin()).isFalse();
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
    @DisplayName("hasPermission — 层级继承: record:manage 包含 record:read")
    void hasPermission_hierarchy_recordManageImpliesRecordRead() {
        AuthSession s = new AuthSession();
        s.setPermissions(List.of("record:manage"));
        assertThat(s.hasPermission("record:read")).isTrue();
        assertThat(s.hasPermission("record:edit")).isTrue();
        assertThat(s.hasPermission("record:manage")).isTrue();
        assertThat(s.hasPermission("record:delete")).isFalse();
    }

    @Test
    @DisplayName("hasPermission — 层级继承: role:manage 包含 role:read")
    void hasPermission_hierarchy_roleManageImpliesRoleRead() {
        AuthSession s = new AuthSession();
        s.setPermissions(List.of("role:manage"));
        assertThat(s.hasPermission("role:read")).isTrue();
        assertThat(s.hasPermission("role:manage")).isTrue();
    }

    @Test
    @DisplayName("setPermissions — null入参创建空列表")
    void setPermissions_null() {
        AuthSession s = new AuthSession();
        s.setPermissions(null);
        assertThat(s.getPermissions()).isEmpty();
    }

    @Test
    @DisplayName("hasPermission — ADMIN角色对任意权限返回true（admin bypass）")
    void hasPermission_adminBypass() {
        AuthSession s = new AuthSession();
        s.setRoleCode("ADMIN");
        s.setPermissions(List.of());
        assertThat(s.hasPermission("record:read")).isTrue();
        assertThat(s.hasPermission("statistics:read")).isTrue();
        assertThat(s.hasPermission("any:permission")).isTrue();
    }

    @Test
    @DisplayName("hasPermission — ADMIN角色不区分大小写")
    void hasPermission_adminCaseInsensitive() {
        AuthSession s = new AuthSession();
        s.setRoleCode("admin");
        assertThat(s.isAdmin()).isTrue();
        assertThat(s.hasPermission("record:read")).isTrue();
    }

    @Test
    @DisplayName("hasPermission — 空权限列表的非ADMIN用户返回false")
    void hasPermission_emptyPerms_nonAdmin() {
        AuthSession s = new AuthSession();
        s.setRoleCode("DOCTOR");
        s.setPermissions(List.of());
        assertThat(s.hasPermission("record:read")).isFalse();
    }
}
