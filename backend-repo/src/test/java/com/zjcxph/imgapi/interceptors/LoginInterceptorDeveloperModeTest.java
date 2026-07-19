package com.zjcxph.imgapi.interceptors;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.mapper.AuthUserMapper;
import com.zjcxph.imgapi.security.TokenBlacklist;
import com.zjcxph.imgapi.service.DeveloperModeService;
import com.zjcxph.imgapi.utils.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginInterceptorDeveloperModeTest {

    @Mock
    private TokenBlacklist tokenBlacklist;
    @Mock
    private AuthUserMapper authUserMapper;
    @Mock
    private DeveloperModeService developerModeService;

    private LoginInterceptor loginInterceptor;

    @BeforeEach
    void setUp() {
        JwtUtil.configure("test-jwt-secret-key-for-auth-tests-1234567890");
        loginInterceptor = new LoginInterceptor(tokenBlacklist, authUserMapper, developerModeService);
    }

    @AfterEach
    void tearDown() {
        loginInterceptor.afterCompletion(null, null, null, null);
    }

    @Test
    void shouldInjectLegacyDeveloperAdminSessionWithoutToken() throws Exception {
        when(developerModeService.isEnabled()).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/settings");
        request.setRemoteAddr("10.10.20.15");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = loginInterceptor.preHandle(request, response, new Object());

        assertThat(allowed).isTrue();
        assertThat(response.getHeader("X-MRR-Developer-Mode")).isEqualTo("enabled");
        assertThat(request.getAttribute(LoginInterceptor.DEVELOPER_MODE_ATTRIBUTE)).isEqualTo(Boolean.TRUE);
        AuthSession session = (AuthSession) request.getAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE);
        assertThat(session).isNotNull();
        assertThat(session.getUsername()).isEqualTo("dev");
        assertThat(session.getRoleCode()).isEqualTo("ADMIN");
        assertThat(session.getPermissions()).contains("system:read", "user:manage", "record:manage");
    }

    @Test
    void shouldRejectAnonymousRequestWhenDeveloperModeIsDisabled() throws Exception {
        when(developerModeService.isEnabled()).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/settings");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = loginInterceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("请先登录");
    }

    @Test
    void shouldFailClosedWhenUserDatabaseIsUnavailableEvenInDeveloperMode() throws Exception {
        AuthSession tokenUser = new AuthSession();
        tokenUser.setId(20L);
        tokenUser.setUsername("doctor.test");
        tokenUser.setStatus("active");
        tokenUser.setPasswordVersion(1);
        String token = JwtUtil.getToken(tokenUser);

        when(tokenBlacklist.isRevoked(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        when(authUserMapper.findById(20L)).thenThrow(new IllegalStateException("database unavailable"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/settings");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = loginInterceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("AUTH_SERVICE_UNAVAILABLE");
        assertThat(request.getAttribute(LoginInterceptor.DEVELOPER_MODE_ATTRIBUTE)).isNull();
        assertThat(request.getAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE)).isNull();
    }
}
