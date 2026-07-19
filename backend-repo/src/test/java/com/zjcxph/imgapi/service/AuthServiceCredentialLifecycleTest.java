package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.dto.req.AdminCreateUserRequest;
import com.zjcxph.imgapi.dto.req.AdminResetPasswordRequest;
import com.zjcxph.imgapi.dto.req.RequiredPasswordChangeRequest;
import com.zjcxph.imgapi.dto.resp.UserCredentialResultDTO;
import com.zjcxph.imgapi.entity.AuthRole;
import com.zjcxph.imgapi.entity.AuthUser;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.mapper.AuthRoleMapper;
import com.zjcxph.imgapi.mapper.AuthUserMapper;
import com.zjcxph.imgapi.security.LoginRateLimiter;
import com.zjcxph.imgapi.service.impl.AuthServiceImpl;
import com.zjcxph.imgapi.utils.PasswordUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceCredentialLifecycleTest {

    @Mock
    private AuthUserMapper authUserMapper;
    @Mock
    private AuthRoleMapper authRoleMapper;
    @Mock
    private LoginRateLimiter loginRateLimiter;

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(authUserMapper, authRoleMapper, loginRateLimiter);
    }

    @Test
    void shouldCreateUserWithOneTimeTemporaryPassword() {
        AdminCreateUserRequest request = createRequest("active");

        AuthUser administrator = createdUser(1L, "admin", "ADMIN", false, 1);
        AuthRole role = new AuthRole();
        role.setCode("DOCTOR");
        when(authUserMapper.findById(1L)).thenReturn(administrator);
        when(authRoleMapper.findByCode("DOCTOR")).thenReturn(role);
        when(authUserMapper.findByUsername("doctor.test"))
                .thenReturn(null)
                .thenAnswer(invocation -> createdUser(20L, "doctor.test", "DOCTOR", true, 1));

        UserCredentialResultDTO result = service.createUser(request, 1L, "127.0.0.1");

        assertThat(result.getTemporaryPassword()).hasSize(16);
        assertThat(result.getTemporaryPasswordExpiresAt()).isAfter(LocalDateTime.now());
        assertThat(result.getUser().getMustChangePassword()).isTrue();

        ArgumentCaptor<AuthUser> captor = ArgumentCaptor.forClass(AuthUser.class);
        verify(authUserMapper).insertUser(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("active");
        assertThat(captor.getValue().isPasswordChangeRequired()).isTrue();
        assertThat(PasswordUtil.matches(result.getTemporaryPassword(), captor.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void shouldRejectCreatingDisabledUserBeforeIssuingCredential() {
        AdminCreateUserRequest request = createRequest("disabled");
        AuthUser administrator = createdUser(1L, "admin", "ADMIN", false, 1);
        AuthRole role = new AuthRole();
        role.setCode("DOCTOR");
        when(authUserMapper.findById(1L)).thenReturn(administrator);
        when(authRoleMapper.findByCode("DOCTOR")).thenReturn(role);
        when(authUserMapper.findByUsername("doctor.test")).thenReturn(null);

        assertThatThrownBy(() -> service.createUser(request, 1L, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("初始状态必须为启用");

        verify(authUserMapper, never()).insertUser(any(AuthUser.class));
    }

    @Test
    void shouldResetPasswordAndIncrementCredentialVersionInPersistenceLayer() {
        AuthUser administrator = createdUser(1L, "admin", "ADMIN", false, 3);
        administrator.setPasswordHash(PasswordUtil.encode("AdministratorPassword123"));
        AuthUser target = createdUser(2L, "doctor", "DOCTOR", false, 4);
        AuthUser updated = createdUser(2L, "doctor", "DOCTOR", true, 5);

        when(authUserMapper.findById(1L)).thenReturn(administrator);
        when(authUserMapper.findById(2L)).thenReturn(target, updated);

        AdminResetPasswordRequest request = new AdminResetPasswordRequest();
        request.setAdministratorPassword("AdministratorPassword123");
        request.setTemporaryPasswordValidHours(24);

        UserCredentialResultDTO result = service.resetPassword(2L, request, 1L, "127.0.0.1");

        assertThat(result.getTemporaryPassword()).hasSize(16);
        assertThat(result.getUser().getPasswordVersion()).isEqualTo(5);
        verify(authUserMapper).resetPassword(eq(2L), any(String.class), any(LocalDateTime.class), eq(1L));
    }

    @Test
    void shouldRejectExpiredTemporaryPassword() {
        AuthUser user = createdUser(2L, "doctor", "DOCTOR", true, 1);
        user.setPasswordHash(PasswordUtil.encode("TemporaryPassword123"));
        user.setTemporaryPasswordExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(authUserMapper.findById(2L)).thenReturn(user);

        RequiredPasswordChangeRequest request = new RequiredPasswordChangeRequest();
        request.setCurrentPassword("TemporaryPassword123");
        request.setNewPassword("A brand new password 123");
        request.setConfirmPassword("A brand new password 123");

        assertThatThrownBy(() -> service.changeRequiredPassword(2L, request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("临时密码已过期");
    }

    private AdminCreateUserRequest createRequest(String status) {
        AdminCreateUserRequest request = new AdminCreateUserRequest();
        request.setUsername("doctor.test");
        request.setDisplayName("测试医生");
        request.setRoleCode("DOCTOR");
        request.setStatus(status);
        request.setTemporaryPasswordValidHours(24);
        return request;
    }

    private AuthUser createdUser(Long id,
                                 String username,
                                 String roleCode,
                                 boolean mustChangePassword,
                                 int passwordVersion) {
        AuthUser user = new AuthUser();
        user.setId(id);
        user.setUsername(username);
        user.setDisplayName(username);
        user.setRoleCode(roleCode);
        user.setRoleName(roleCode);
        user.setStatus("active");
        user.setMustChangePassword(mustChangePassword);
        user.setPasswordVersion(passwordVersion);
        return user;
    }
}
