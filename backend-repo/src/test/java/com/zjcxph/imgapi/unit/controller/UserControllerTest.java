package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.controller.UserController;
import com.zjcxph.imgapi.dto.req.AuthUserUpdateRequest;
import com.zjcxph.imgapi.dto.req.RegisterRequest;
import com.zjcxph.imgapi.dto.req.RegistrationApprovalRequest;
import com.zjcxph.imgapi.dto.req.RegistrationRejectionRequest;
import com.zjcxph.imgapi.dto.req.UserRequest;
import com.zjcxph.imgapi.dto.resp.AuthUserProfileDTO;
import com.zjcxph.imgapi.dto.resp.LoginResponseDTO;
import com.zjcxph.imgapi.dto.resp.RegistrationResultDTO;
import com.zjcxph.imgapi.entity.AuthRole;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
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
        lenient().when(httpServletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("POST login — 正常登录返回 Token")
    void login_success() {
        when(authService.login(any(UserRequest.class), anyString())).thenReturn(loginResponse);

        Result<LoginResponseDTO> result = userController.login(validRequest, httpServletRequest);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getToken()).isEqualTo("test.jwt.token");
        assertThat(result.getData().getUser().getUsername()).isEqualTo("admin");
        verify(authService).login(validRequest, "127.0.0.1");
    }

    @Test
    @DisplayName("POST login — Token 为空返回 401")
    void login_emptyToken() {
        loginResponse.setToken(null);
        when(authService.login(any(UserRequest.class), anyString())).thenReturn(loginResponse);

        Result<LoginResponseDTO> result = userController.login(validRequest, httpServletRequest);

        assertThat(result.getCode()).isEqualTo(401);
        assertThat(result.getMessage()).contains("用户名或密码错误");
    }

    @Test
    @DisplayName("POST login — Token 空白返回 401")
    void login_blankToken() {
        loginResponse.setToken("   ");
        when(authService.login(any(UserRequest.class), anyString())).thenReturn(loginResponse);

        Result<LoginResponseDTO> result = userController.login(validRequest, httpServletRequest);

        assertThat(result.getCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("POST register — 提交注册申请且不返回登录 Token")
    void register_submitsPendingApplication() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("new.doctor");
        request.setPassword("Registration123!");
        request.setDisplayName("新医生");
        RegistrationResultDTO registration = new RegistrationResultDTO(
                2L, "new.doctor", "新医生", "pending", LocalDateTime.now());
        when(authService.register(request, "127.0.0.1")).thenReturn(registration);

        Result<RegistrationResultDTO> result = userController.register(request, httpServletRequest);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getStatus()).isEqualTo("pending");
        assertThat(result.getMessage()).contains("等待管理员审核");
        verify(authService).register(request, "127.0.0.1");
    }

    @Test
    @DisplayName("POST approve registration — 管理员审核通过")
    void approveRegistration_success() {
        RegistrationApprovalRequest request = new RegistrationApprovalRequest();
        request.setRoleCode("DOCTOR");
        AuthUserProfileDTO approved = new AuthUserProfileDTO();
        approved.setId(2L);
        approved.setUsername("new.doctor");
        approved.setStatus("active");
        when(authService.approveRegistration(2L, request, 1L, "127.0.0.1")).thenReturn(approved);

        Result<AuthUserProfileDTO> result = userController.approveRegistration(2L, request, httpServletRequest);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getStatus()).isEqualTo("active");
        verify(authService).approveRegistration(2L, request, 1L, "127.0.0.1");
    }

    @Test
    @DisplayName("POST reject registration — 管理员记录拒绝原因")
    void rejectRegistration_success() {
        RegistrationRejectionRequest request = new RegistrationRejectionRequest();
        request.setRejectReason("身份信息无法核验");
        AuthUserProfileDTO rejected = new AuthUserProfileDTO();
        rejected.setId(2L);
        rejected.setStatus("rejected");
        rejected.setRejectReason("身份信息无法核验");
        when(authService.rejectRegistration(2L, request, 1L, "127.0.0.1")).thenReturn(rejected);

        Result<AuthUserProfileDTO> result = userController.rejectRegistration(2L, request, httpServletRequest);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getStatus()).isEqualTo("rejected");
        assertThat(result.getData().getRejectReason()).contains("无法核验");
        verify(authService).rejectRegistration(2L, request, 1L, "127.0.0.1");
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
    @DisplayName("GET currentUser — 未登录返回 401")
    void currentUser_notLoggedIn() {
        when(authService.currentUser()).thenReturn(null);

        Result<AuthSession> result = userController.currentUser();

        assertThat(result.getCode()).isEqualTo(401);
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
    @DisplayName("PUT updateUser — 用户不存在返回 404")
    void updateUser_notFound() {
        AuthUserUpdateRequest request = updateRequest("DOCTOR", "active");
        when(authService.updateUser(999L, request)).thenReturn(null);

        var result = userController.updateUser(999L, request);

        assertThat(result.getCode()).isEqualTo(404);
        assertThat(result.getMessage()).contains("用户不存在");
    }

    @Test
    @DisplayName("PUT updateUser — 禁用当前账号返回 409")
    void updateUser_cannotDisableCurrentUser() {
        AuthUserUpdateRequest request = updateRequest("ADMIN", "disabled");

        var result = userController.updateUser(1L, request);

        assertThat(result.getCode()).isEqualTo(409);
        verify(authService, never()).updateUser(any(), any());
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
    @DisplayName("DELETE disableUser — 用户不存在返回 404")
    void disableUser_notFound() {
        when(authService.disableUser(999L)).thenReturn(0);

        Result<Void> result = userController.disableUser(999L);

        assertThat(result.getCode()).isEqualTo(404);
        assertThat(result.getMessage()).contains("用户不存在");
    }

    @Test
    @DisplayName("DELETE disableUser — 禁用当前账号返回 409")
    void disableUser_cannotDisableCurrentUser() {
        Result<Void> result = userController.disableUser(1L);

        assertThat(result.getCode()).isEqualTo(409);
        verify(authService, never()).disableUser(any());
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
