package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.controller.UserController;
import com.zjcxph.imgapi.dto.req.UserRequest;
import com.zjcxph.imgapi.dto.resp.AuthUserProfileDTO;
import com.zjcxph.imgapi.dto.resp.LoginResponseDTO;
import com.zjcxph.imgapi.entity.AuthRole;
import com.zjcxph.imgapi.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserController 认证控制器测试")
class UserControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private UserController userController;

    private UserRequest validRequest;
    private LoginResponseDTO loginResponse;

    @BeforeEach
    void setUp() {
        validRequest = new UserRequest();
        validRequest.setUsername("admin");
        validRequest.setPassword("123456");

        loginResponse = new LoginResponseDTO();
        loginResponse.setToken("test.jwt.token");
        AuthSession user = new AuthSession();
        user.setId(1L);
        user.setUsername("admin");
        user.setDisplayName("管理员");
        user.setRoleCode("ADMIN");
        user.setRoleName("管理员");
        user.setStatus("active");
        loginResponse.setUser(user);
    }

    @Test
    @DisplayName("POST login — 正常登录返回Token")
    void login_success() {
        when(authService.login(any())).thenReturn(loginResponse);

        Result<LoginResponseDTO> result = userController.login(validRequest);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getToken()).isEqualTo("test.jwt.token");
        assertThat(result.getData().getUser().getUsername()).isEqualTo("admin");
    }

    @Test
    @DisplayName("POST login — Token为空返回失败")
    void login_emptyToken() {
        loginResponse.setToken(null);
        when(authService.login(any())).thenReturn(loginResponse);

        Result<LoginResponseDTO> result = userController.login(validRequest);

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).contains("Invalid");
    }

    @Test
    @DisplayName("POST login — Token空白返回失败")
    void login_blankToken() {
        loginResponse.setToken("   ");
        when(authService.login(any())).thenReturn(loginResponse);

        Result<LoginResponseDTO> result = userController.login(validRequest);

        assertThat(result.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("GET currentUser — 已登录返回用户会话")
    void currentUser_loggedIn() {
        AuthSession session = new AuthSession();
        session.setId(1L);
        session.setUsername("admin");
        session.setRoleCode("ADMIN");
        when(authService.currentUser()).thenReturn(session);

        Result<AuthSession> result = userController.currentUser();

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getRoleCode()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("GET currentUser — 未登录返回失败")
    void currentUser_notLoggedIn() {
        when(authService.currentUser()).thenReturn(null);

        Result<AuthSession> result = userController.currentUser();

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).contains("Not logged in");
    }

    @Test
    @DisplayName("GET listUsers — 返回用户列表")
    void listUsers() {
        AuthUserProfileDTO profile = new AuthUserProfileDTO();
        profile.setUsername("admin");
        profile.setRoleCode("ADMIN");
        when(authService.listUsers()).thenReturn(List.of(profile));

        Result<List<AuthUserProfileDTO>> result = userController.listUsers();

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().getFirst().getUsername()).isEqualTo("admin");
    }

    @Test
    @DisplayName("GET listRoles — 返回角色列表")
    void listRoles() {
        AuthRole role = new AuthRole();
        role.setCode("DOCTOR");
        role.setName("医生");
        when(authService.listRoles()).thenReturn(List.of(role));

        Result<List<AuthRole>> result = userController.listRoles();

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().getFirst().getCode()).isEqualTo("DOCTOR");
    }

    @Test
    @DisplayName("PUT updateUser — 更新成功返回用户")
    void updateUser_success() {
        AuthUserProfileDTO updated = new AuthUserProfileDTO();
        updated.setUsername("admin");
        updated.setRoleCode("DOCTOR");
        when(authService.updateUser(any(), any())).thenReturn(updated);

        var result = userController.updateUser(1L, null);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getRoleCode()).isEqualTo("DOCTOR");
    }

    @Test
    @DisplayName("PUT updateUser — 用户不存在返回失败")
    void updateUser_notFound() {
        when(authService.updateUser(any(), any())).thenReturn(null);

        var result = userController.updateUser(999L, null);

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).contains("not found");
    }

    @Test
    @DisplayName("DELETE disableUser — 禁用成功")
    void disableUser_success() {
        when(authService.disableUser(1L)).thenReturn(1);

        Result<Void> result = userController.disableUser(1L);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getMessage()).contains("Disable success");
    }

    @Test
    @DisplayName("DELETE disableUser — 用户不存在返回失败")
    void disableUser_notFound() {
        when(authService.disableUser(999L)).thenReturn(0);

        Result<Void> result = userController.disableUser(999L);

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).contains("not found");
    }
}
