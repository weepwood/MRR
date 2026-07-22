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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    void shouldAllowAnonymousRegistrationRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/register");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = loginInterceptor.preHandle(request, response, new Object());

        assertThat(allowed).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        verify(developerModeService, never()).isArchiveLegacyRequestAllowed(request);
    }

    @Test
    void shouldAllowAnonymousRegistrationWithContextPathAndTrailingSlash() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mrr/api/v1/auth/register/");
        request.setContextPath("/mrr");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = loginInterceptor.preHandle(request, response, new Object());

        assertThat(allowed).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        verify(developerModeService, never()).isArchiveLegacyRequestAllowed(request);
    }

    @Test
    void shouldNotExposePublicRegistrationByGetRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/register");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(developerModeService.isArchiveLegacyRequestAllowed(request)).thenReturn(false);

        boolean allowed = loginInterceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void shouldAllowAnonymousReadOnlyArchiveRequestWhenLegacyModeIsAllowed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/img/search");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(developerModeService.isArchiveLegacyRequestAllowed(request)).thenReturn(true);

        boolean allowed = loginInterceptor.preHandle(request, response, new Object());

        assertThat(allowed).isTrue();
        assertThat(response.getHeader("X-MRR-Developer-Mode")).isEqualTo("enabled");
        assertThat(response.getHeader("X-MRR-Access-Mode")).isEqualTo("archive-legacy");
        assertThat(request.getAttribute(LoginInterceptor.DEVELOPER_MODE_ATTRIBUTE)).isEqualTo(Boolean.TRUE);
        assertThat(request.getAttribute(LoginInterceptor.ACCESS_MODE_ATTRIBUTE))
                .isEqualTo(LoginInterceptor.ARCHIVE_LEGACY_ACCESS_MODE);

        AuthSession session = (AuthSession) request.getAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE);
        assertThat(session).isNotNull();
        assertThat(session.getId()).isEqualTo(-1L);
        assertThat(session.getUsername()).isEqualTo("developer-archive");
        assertThat(session.getRoleCode()).isEqualTo("DEVELOPER_ARCHIVE");
        assertThat(session.getPermissions()).containsExactlyInAnyOrder("record:read", "search:read");
    }

    @Test
    void shouldRejectAnonymousNonArchiveRequestEvenWhenDeveloperModeIsEnabled() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/settings");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(developerModeService.isArchiveLegacyRequestAllowed(request)).thenReturn(false);

        boolean allowed = loginInterceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("请先登录");
        assertThat(request.getAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE)).isNull();
    }

    @Test
    void shouldRejectInvalidTokenWithoutFallingBackToArchiveLegacyMode() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/img/search");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("Authorization", "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = loginInterceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Token 无效或已过期");
        verify(developerModeService, never()).isArchiveLegacyRequestAllowed(request);
        assertThat(request.getAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE)).isNull();
    }

    @Test
    void shouldRejectMalformedAuthorizationHeaderWithoutFallingBack() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/img/search");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("Authorization", "Basic abc");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = loginInterceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Authorization 格式无效");
        verify(developerModeService, never()).isArchiveLegacyRequestAllowed(request);
    }

    @Test
    void shouldRejectAnonymousRequestWhenDeveloperModeIsDisabled() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/img/search");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(developerModeService.isArchiveLegacyRequestAllowed(request)).thenReturn(false);

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

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/img/search");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = loginInterceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("AUTH_SERVICE_UNAVAILABLE");
        verify(developerModeService, never()).isArchiveLegacyRequestAllowed(request);
        assertThat(request.getAttribute(LoginInterceptor.DEVELOPER_MODE_ATTRIBUTE)).isNull();
        assertThat(request.getAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE)).isNull();
    }
}
