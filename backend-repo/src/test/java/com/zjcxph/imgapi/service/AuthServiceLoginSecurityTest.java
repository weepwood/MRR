package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.dto.req.UserRequest;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceLoginSecurityTest {

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
    void shouldNotRevealDisabledAccountForWrongPassword() {
        AuthUser disabled = disabledUser();
        when(authUserMapper.findByUsername("disabled.user")).thenReturn(disabled);

        UserRequest request = request("disabled.user", "WrongPassword123");

        assertThatThrownBy(() -> service.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("用户名或密码错误");
        verify(loginRateLimiter).recordLoginFailure("disabled.user");
    }

    @Test
    void shouldReportDisabledStatusOnlyAfterCorrectCredential() {
        AuthUser disabled = disabledUser();
        when(authUserMapper.findByUsername("disabled.user")).thenReturn(disabled);

        UserRequest request = request("disabled.user", "CorrectPassword123");

        assertThatThrownBy(() -> service.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("账号已被禁用，请联系管理员");
    }

    private AuthUser disabledUser() {
        AuthUser user = new AuthUser();
        user.setId(20L);
        user.setUsername("disabled.user");
        user.setDisplayName("停用用户");
        user.setPasswordHash(PasswordUtil.encode("CorrectPassword123"));
        user.setRoleCode("DOCTOR");
        user.setStatus("disabled");
        user.setPasswordVersion(1);
        return user;
    }

    private UserRequest request(String username, String password) {
        UserRequest request = new UserRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }
}
