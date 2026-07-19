package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.controller.UserController;
import com.zjcxph.imgapi.dto.req.AuthUserUpdateRequest;
import com.zjcxph.imgapi.dto.req.RegisterRequest;
import com.zjcxph.imgapi.dto.req.UserRequest;
import com.zjcxph.imgapi.dto.resp.AuthUserProfileDTO;
import com.zjcxph.imgapi.dto.resp.LoginResponseDTO;
import com.zjcxph.imgapi.entity.AuthRole;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.security.TokenBlacklist;
import com.zjcxph.imgapi.service.AuthService;
import com.zjcxph.imgapi.utils.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserController 认证控制器测试")
class UserControllerTest {

    @Mock
    private AuthService authService;
    @Mock
    private TokenBlacklist tokenBlacklist;
    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private UserController userController;

    private UserRequest validRequest;
    private LoginResponseDTO loginResponse;

    @BeforeEach
    void setUp() {
        validRequest = new UserRequest();
        validRequest.setUsername("admin");
        validRequest.setPassword("123456789012");

        loginResponse = new LoginResponseDTO();
        loginResponse.setToken("test.jwt.token");
        AuthSession user = adminSession();
        loginResponse.setUser(user);
        AuthContext.setCurrentUser(user);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("POST login — 正常登录返回 Token")
    void login_success() {
        when(authService.login(any())).thenReturn(loginResponse);

        Result<LoginResponseDTO> result = userController.login(validRequest);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getToken()).isEqualTo("test.jwt.token");
        assertThat(result.getData().getUser().getUsername()).isEqualTo("admin");
    }

    @Test
    @DisplayName("POST login — Token 为空返回失败")
    void login_emptyToken() {
        loginResponse.setToken(null);
        when(authService.login(any())).thenReturn(loginResponse);

        Result<LoginResponseDTO> result = userController.login(validRequest);

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).contains("用户名或密码错误");
    }

    @Test
    @DisplayName("POST login — Token 空白返回失败")
    void login_blankToken() {
        loginResponse.setToken("   ");
        when(authService.login(any())).thenReturn(loginResponse);

        Result<LoginResponseDTO> result = userController.login(validRequest);

        assertThat(result.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST register — 旧接口已停用且不调用注册服务")
    void register_disabled() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("legacy.user");
        request.setPassword("LegacyPassword123");
        when(httpServletRequest.getRemoteAddr()).thenReturn("127.0.0.1");

        assertThatThrownBy(() -> userController.register(request, httpServletRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("旧版注册接口已停用");
        verify(authService, never()).register(any(RegisterRequest.class), any());
    }

    @Test
    @DisplayName("GET currentUser — 已登录返回用户会话")
    void currentUser_loggedIn() {
        AuthSession session = adminSession();
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
        assertThat(result.getMessage()).contains("未登录");
    }

    @Test
    @DisplayName("GET listUsers — 返回用户列表")
    void listUsers() {
        AuthUserProfileDTO profile = new AuthUserProfileDTO();
        profile.setUsername("admin");
        profile.setRoleCode("ADMIN");
        when(authService.listUsersPaginated(1, 20, null, null, null))
                .thenReturn(com.zjcxph.imgapi.dto.resp.PageResult.of(List.of(profile), 1, 1, 20));

        Result<com.zjcxph.imgapi.dto.resp.PageResult<AuthUserProfileDTO>> result
                = userController.listUsers(1, 20, null, null, null);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getList()).hasSize(1);
        assertThat(result.getData().getList().getFirst().getUsername()).isEqualTo("admin");
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
        updated.setUsername("doctor");
        updated.setRoleCode("DOCTOR");
        AuthUserUpdateRequest request = updateRequest("DOCTOR", "active");
        when(authService.updateUser(2L, request)).thenReturn(updated);

        var result = userController.updateUser(2L, request);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getRoleCode()).isEqualTo("DOCTOR");
    }

    @Test
    @DisplayName("PUT updateUser — 用户不存在返回失败")
    void updateUser_notFound() {
        AuthUserUpdateRequest request = updateRequest("DOCTOR", "active");
        when(authService.updateUser(999L, request)).thenReturn(null);

        var result = userController.updateUser(999L, request);

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).contains("用户不存在");
    }

    @Test
    @DisplayName("DELETE disableUser — 禁用其他用户成功")
    void disableUser_success() {
        when(authService.disableUser(2L)).thenReturn(1);

        Result<Void> result = userController.disableUser(2L);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getMessage()).contains("禁用成功");
    }

    @Test
    @DisplayName("DELETE disableUser — 用户不存在返回失败")
    void disableUser_notFound() {
        when(authService.disableUser(999L)).thenReturn(0);

        Result<Void> result = userController.disableUser(999L);

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).contains("用户不存在");
    }

    private AuthSession adminSession() {
        AuthSession user = new AuthSession();
        user.setId(1L);
        user.setUsername("admin");
        user.setDisplayName("管理员");
        user.setRoleCode("ADMIN");
        user.setRoleName("管理员");
        user.setStatus("active");
        return user;
    }

    private AuthUserUpdateRequest updateRequest(String roleCode, String status) {
        AuthUserUpdateRequest request = new AuthUserUpdateRequest();
        request.setDisplayName("测试用户");
        request.setRoleCode(roleCode);
        request.setStatus(status);
        return request;
    }
}
