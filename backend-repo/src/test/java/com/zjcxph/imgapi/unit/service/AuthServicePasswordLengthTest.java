package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.dto.req.RegisterRequest;
import com.zjcxph.imgapi.entity.AuthRole;
import com.zjcxph.imgapi.entity.AuthUser;
import com.zjcxph.imgapi.mapper.AuthRoleMapper;
import com.zjcxph.imgapi.mapper.AuthUserMapper;
import com.zjcxph.imgapi.security.LoginRateLimiter;
import com.zjcxph.imgapi.service.impl.AuthServiceImpl;
import com.zjcxph.imgapi.utils.PasswordUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("密码长度策略测试")
class AuthServicePasswordLengthTest {

    @Mock
    private AuthUserMapper authUserMapper;
    @Mock
    private AuthRoleMapper authRoleMapper;
    @Mock
    private LoginRateLimiter rateLimiter;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(authUserMapper, authRoleMapper, rateLimiter);
    }

    @Test
    @DisplayName("注册允许使用六位密码")
    void register_acceptsSixCharacterPassword() {
        RegisterRequest request = registrationRequest("Ab1234");
        AuthUser created = pendingUser();
        when(authUserMapper.findByUsername("six.user")).thenReturn(null, created);
        when(authRoleMapper.findByCode("DOCTOR")).thenReturn(role());

        var result = authService.register(request, "10.0.0.8");

        ArgumentCaptor<AuthUser> captor = ArgumentCaptor.forClass(AuthUser.class);
        verify(authUserMapper).insertUser(captor.capture());
        assertThat(PasswordUtil.matches("Ab1234", captor.getValue().getPasswordHash())).isTrue();
        assertThat(result.getStatus()).isEqualTo("pending");
    }

    @Test
    @DisplayName("注册拒绝少于六位的密码")
    void register_rejectsFiveCharacterPassword() {
        RegisterRequest request = registrationRequest("12345");

        assertThatThrownBy(() -> authService.register(request, "10.0.0.8"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("6 到 64");
    }

    private RegisterRequest registrationRequest(String password) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("six.user");
        request.setPassword(password);
        request.setDisplayName("六位密码用户");
        return request;
    }

    private AuthUser pendingUser() {
        AuthUser user = new AuthUser();
        user.setId(6L);
        user.setUsername("six.user");
        user.setDisplayName("六位密码用户");
        user.setRoleCode("DOCTOR");
        user.setRoleName("医生");
        user.setStatus("pending");
        user.setAppliedAt(LocalDateTime.now());
        user.setPasswordVersion(1);
        return user;
    }

    private AuthRole role() {
        AuthRole role = new AuthRole();
        role.setCode("DOCTOR");
        role.setName("医生");
        return role;
    }
}
