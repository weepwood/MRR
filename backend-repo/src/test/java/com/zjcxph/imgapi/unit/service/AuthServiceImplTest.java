package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.dto.req.AuthUserUpdateRequest;
import com.zjcxph.imgapi.dto.req.RegisterRequest;
import com.zjcxph.imgapi.dto.req.RegistrationApprovalRequest;
import com.zjcxph.imgapi.dto.req.RegistrationRejectionRequest;
import com.zjcxph.imgapi.dto.req.UserRequest;
import com.zjcxph.imgapi.dto.resp.LoginResponseDTO;
import com.zjcxph.imgapi.dto.resp.RegistrationResultDTO;
import com.zjcxph.imgapi.entity.AuthRole;
import com.zjcxph.imgapi.entity.AuthUser;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.mapper.AuthRoleMapper;
import com.zjcxph.imgapi.mapper.AuthUserMapper;
import com.zjcxph.imgapi.security.LoginRateLimiter;
import com.zjcxph.imgapi.service.impl.AuthServiceImpl;
import com.zjcxph.imgapi.utils.AuthContext;
import com.zjcxph.imgapi.utils.JwtUtil;
import com.zjcxph.imgapi.utils.PasswordUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl 单元测试")
class AuthServiceImplTest {

    @Mock
    private AuthUserMapper authUserMapper;
    @Mock
    private AuthRoleMapper authRoleMapper;
    @Mock
    private LoginRateLimiter rateLimiter;
    @InjectMocks
    private AuthServiceImpl authService;

    private AuthUser mockUser;

    @BeforeEach
    void setUp() {
        JwtUtil.configure("test-jwt-secret-key-for-auth-service-tests-123456");
        mockUser = new AuthUser();
        mockUser.setId(1L);
        mockUser.setUsername("admin");
        mockUser.setDisplayName("管理员");
        mockUser.setPasswordHash(PasswordUtil.encode("123456"));
        mockUser.setRoleCode("ADMIN");
        mockUser.setRoleName("管理员");
        mockUser.setPermissionsCsv("user:manage,role:manage,role:read");
        mockUser.setStatus("active");
        mockUser.setPasswordVersion(1);
        mockUser.setLastLoginAt(LocalDateTime.now());
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Nested
    @DisplayName("login 方法")
    class LoginTests {

        @Test
        @DisplayName("正常登录 — 返回 Token 和用户信息")
        void login_success() {
            UserRequest req = request("admin", "123456");
            when(authUserMapper.findByUsername("admin")).thenReturn(mockUser);
            when(authUserMapper.updateLastLoginAt(eq(1L), any(LocalDateTime.class))).thenReturn(1);

            LoginResponseDTO result = authService.login(req, "10.0.0.8");

            assertThat(result).isNotNull();
            assertThat(result.getToken()).isNotBlank();
            assertThat(result.getUser()).isNotNull();
            assertThat(result.getUser().getUsername()).isEqualTo("admin");
            assertThat(result.getUser().getRoleCode()).isEqualTo("ADMIN");
            verify(rateLimiter).resetLoginFailures("admin|10.0.0.8");
            verify(authUserMapper).updateLastLoginAt(eq(1L), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("用户名为空 — 抛出异常")
        void login_emptyUsername() {
            assertThatThrownBy(() -> authService.login(request("", "123456"), "10.0.0.8"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不能为空");
        }

        @Test
        @DisplayName("用户不存在 — 返回通用凭据错误并按用户和 IP 计数")
        void login_userNotFound() {
            when(authUserMapper.findByUsername("ghost")).thenReturn(null);

            assertThatThrownBy(() -> authService.login(request("ghost", "123456"), "10.0.0.8"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("用户名或密码错误");
            verify(rateLimiter).recordLoginFailure("ghost|10.0.0.8");
        }

        @Test
        @DisplayName("密码错误 — 抛出通用异常")
        void login_wrongPassword() {
            when(authUserMapper.findByUsername("admin")).thenReturn(mockUser);

            assertThatThrownBy(() -> authService.login(request("admin", "wrong_password"), "10.0.0.8"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("用户名或密码错误");
            verify(rateLimiter).recordLoginFailure("admin|10.0.0.8");
        }

        @Test
        @DisplayName("待审核账号 — 正确密码也不能登录")
        void login_pendingUser() {
            mockUser.setStatus("pending");
            when(authUserMapper.findByUsername("admin")).thenReturn(mockUser);

            assertThatThrownBy(() -> authService.login(request("admin", "123456"), "10.0.0.8"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("等待管理员审核");
        }

        @Test
        @DisplayName("审核拒绝账号 — 正确密码也不能登录")
        void login_rejectedUser() {
            mockUser.setStatus("rejected");
            when(authUserMapper.findByUsername("admin")).thenReturn(mockUser);

            assertThatThrownBy(() -> authService.login(request("admin", "123456"), "10.0.0.8"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已被拒绝");
        }

        @Test
        @DisplayName("账号禁用 — 正确密码后返回停用状态")
        void login_disabledUser() {
            mockUser.setStatus("disabled");
            when(authUserMapper.findByUsername("admin")).thenReturn(mockUser);

            assertThatThrownBy(() -> authService.login(request("admin", "123456"), "10.0.0.8"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("停用");
        }

        @Test
        @DisplayName("同一账号不同 IP 使用独立失败计数键")
        void login_usesIndependentIpKeys() {
            when(authUserMapper.findByUsername("admin")).thenReturn(mockUser);

            assertThatThrownBy(() -> authService.login(request("admin", "wrong"), "10.0.0.8"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> authService.login(request("admin", "wrong"), "10.0.0.9"))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(rateLimiter).recordLoginFailure("admin|10.0.0.8");
            verify(rateLimiter).recordLoginFailure("admin|10.0.0.9");
        }

        private UserRequest request(String username, String password) {
            UserRequest request = new UserRequest();
            request.setUsername(username);
            request.setPassword(password);
            return request;
        }
    }

    @Nested
    @DisplayName("注册与审核")
    class RegistrationReviewTests {

        @Test
        @DisplayName("自主注册只能创建 pending 账号且不签发 JWT")
        void register_createsPendingUserWithoutToken() {
            RegisterRequest request = registrationRequest();
            AuthUser created = pendingUser();
            when(authUserMapper.findByUsername("new.doctor")).thenReturn(null, created);
            when(authRoleMapper.findByCode("DOCTOR")).thenReturn(role("DOCTOR"));

            RegistrationResultDTO result = authService.register(request, "10.0.0.8");

            ArgumentCaptor<AuthUser> insertedUser = ArgumentCaptor.forClass(AuthUser.class);
            verify(authUserMapper).insertUser(insertedUser.capture());
            assertThat(insertedUser.getValue().getStatus()).isEqualTo("pending");
            assertThat(insertedUser.getValue().getRoleCode()).isEqualTo("DOCTOR");
            assertThat(insertedUser.getValue().getContactInfo()).isEqualTo("内线 6123");
            assertThat(result.getStatus()).isEqualTo("pending");
            assertThat(result.getUsername()).isEqualTo("new.doctor");
        }

        @Test
        @DisplayName("管理员审核通过执行 pending 到 active 单向转换")
        void approveRegistration_activatesPendingUser() {
            AuthUser pending = pendingUser();
            AuthUser approved = pendingUser();
            approved.setStatus("active");
            approved.setRoleCode("DOCTOR");
            when(authUserMapper.findById(1L)).thenReturn(mockUser);
            when(authUserMapper.findById(2L)).thenReturn(pending, approved);
            when(authRoleMapper.findByCode("DOCTOR")).thenReturn(role("DOCTOR"));
            when(authUserMapper.approveRegistration(2L, "DOCTOR", 1L)).thenReturn(1);
            RegistrationApprovalRequest request = new RegistrationApprovalRequest();
            request.setRoleCode("DOCTOR");

            var result = authService.approveRegistration(2L, request, 1L, "10.0.0.8");

            assertThat(result.getStatus()).isEqualTo("active");
            verify(authUserMapper).approveRegistration(2L, "DOCTOR", 1L);
        }

        @Test
        @DisplayName("管理员拒绝申请并保存原因")
        void rejectRegistration_recordsReason() {
            AuthUser pending = pendingUser();
            AuthUser rejected = pendingUser();
            rejected.setStatus("rejected");
            rejected.setRejectReason("身份信息无法核验");
            when(authUserMapper.findById(1L)).thenReturn(mockUser);
            when(authUserMapper.findById(2L)).thenReturn(pending, rejected);
            when(authUserMapper.rejectRegistration(2L, "身份信息无法核验", 1L)).thenReturn(1);
            RegistrationRejectionRequest request = new RegistrationRejectionRequest();
            request.setRejectReason("身份信息无法核验");

            var result = authService.rejectRegistration(2L, request, 1L, "10.0.0.8");

            assertThat(result.getStatus()).isEqualTo("rejected");
            assertThat(result.getRejectReason()).contains("无法核验");
            verify(authUserMapper).rejectRegistration(2L, "身份信息无法核验", 1L);
        }

        @Test
        @DisplayName("通用用户编辑不能绕过审核直接启用 pending 账号")
        void updateUser_cannotActivatePendingRegistration() {
            AuthUser pending = pendingUser();
            when(authUserMapper.findById(2L)).thenReturn(pending);
            AuthUserUpdateRequest request = new AuthUserUpdateRequest();
            request.setDisplayName("新医生");
            request.setRoleCode("DOCTOR");
            request.setStatus("active");

            assertThatThrownBy(() -> authService.updateUser(2L, request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("专用审核");
        }

        private RegisterRequest registrationRequest() {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("new.doctor");
            request.setPassword("Registration123!");
            request.setDisplayName("新医生");
            request.setContactInfo("内线 6123");
            request.setApplyRemark("肿瘤科病案查阅");
            return request;
        }

        private AuthUser pendingUser() {
            AuthUser user = new AuthUser();
            user.setId(2L);
            user.setUsername("new.doctor");
            user.setDisplayName("新医生");
            user.setPasswordHash(PasswordUtil.encode("Registration123!"));
            user.setRoleCode("DOCTOR");
            user.setRoleName("医生");
            user.setPermissionsCsv("record:read");
            user.setStatus("pending");
            user.setContactInfo("内线 6123");
            user.setApplyRemark("肿瘤科病案查阅");
            user.setAppliedAt(LocalDateTime.now());
            user.setPasswordVersion(1);
            return user;
        }

        private AuthRole role(String code) {
            AuthRole role = new AuthRole();
            role.setCode(code);
            role.setName("医生");
            return role;
        }
    }

    @Nested
    @DisplayName("listUsers 方法")
    class ListUsersTests {
        @Test
        @DisplayName("返回所有用户 Profile 列表")
        void listUsers_returnsProfiles() {
            when(authUserMapper.findAll()).thenReturn(List.of(mockUser));
            var profiles = authService.listUsers();
            assertThat(profiles).hasSize(1);
            assertThat(profiles.getFirst().getUsername()).isEqualTo("admin");
            assertThat(profiles.getFirst().getPermissions()).contains("user:manage");
        }
    }

    @Nested
    @DisplayName("disableUser 方法")
    class DisableUserTests {
        @Test
        @DisplayName("禁用普通用户返回影响行数")
        void disableUser_success() {
            mockUser.setRoleCode("DOCTOR");
            when(authUserMapper.findById(1L)).thenReturn(mockUser);
            when(authUserMapper.updateStatus(1L, "disabled")).thenReturn(1);

            int result = authService.disableUser(1L);

            assertThat(result).isEqualTo(1);
            verify(authUserMapper).updateStatus(1L, "disabled");
        }
    }

    @Nested
    @DisplayName("currentUser 方法")
    class CurrentUserTests {
        @Test
        @DisplayName("未登录时返回 null")
        void currentUser_notLoggedIn() {
            assertThat(authService.currentUser()).isNull();
        }
    }

    @Nested
    @DisplayName("listRoles 方法")
    class ListRolesTests {
        @Test
        @DisplayName("返回所有角色列表")
        void listRoles_returnsRoles() {
            AuthRole role = new AuthRole();
            role.setCode("ADMIN");
            role.setName("管理员");
            when(authRoleMapper.findAll()).thenReturn(List.of(role));

            var roles = authService.listRoles();

            assertThat(roles).hasSize(1);
            assertThat(roles.getFirst().getCode()).isEqualTo("ADMIN");
        }
    }
}
