package com.zjcxph.imgapi.interceptors;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.mapper.AuthUserMapper;
import com.zjcxph.imgapi.security.TokenBlacklist;
import com.zjcxph.imgapi.service.DeveloperApiAccessService;
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

    @Mock private TokenBlacklist tokenBlacklist;
    @Mock private AuthUserMapper authUserMapper;
    @Mock private DeveloperModeService developerModeService;
    @Mock private DeveloperApiAccessService developerApiAccessService;

    private LoginInterceptor loginInterceptor;

    @BeforeEach
    void setUp() {
        JwtUtil.configure("test-jwt-secret-key-for-auth-tests-1234567890");
        loginInterceptor = new LoginInterceptor(
                tokenBlacklist, authUserMapper, developerModeService, developerApiAccessService);
    }

    @AfterEach
    void tearDown() {
        loginInterceptor.afterCompletion(null, null, null, null);
    }

    @Test
    void shouldAllowAnonymousRegistrationRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/register");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(loginInterceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        verify(developerApiAccessService, never()).isRequestAllowed(request);
        verify(developerModeService, never()).isArchiveLegacyRequestAllowed(request);
    }

    @Test
    void shouldAllowAnonymousRegistrationWithContextPathAndTrailingSlash() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mrr/api/v1/auth/register/");
        request.setContextPath("/mrr");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(loginInterceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        verify(developerApiAccessService, never()).isRequestAllowed(request);
    }

    @Test
    void shouldNotExposePublicRegistrationByGetRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/register");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(developerApiAccessService.isRequestAllowed(request)).thenReturn(false);
        when(developerModeService.isArchiveLegacyRequestAllowed(request)).thenReturn(false);

        assertThat(loginInterceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void shouldAllowFullApiRequestWhenSecondLevelDeveloperSwitchIsAllowed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/oss/migration/jobs");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(developerApiAccessService.isRequestAllowed(request)).thenReturn(true);

        assertThat(loginInterceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(response.getHeader("X-MRR-Access-Mode")).isEqualTo("api-full");
        AuthSession session = (AuthSession) request.getAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE);
        assertThat(session.getId()).isEqualTo(-2L);
        assertThat(session.getRoleCode()).isEqualTo("DEVELOPER_API");
        assertThat(session.getPermissions())
                .contains("record:manage", "record:edit", "system:manage", "user:manage");
        verify(developerModeService, never()).isArchiveLegacyRequestAllowed(request);
    }

    @Test
    void shouldAllowAnonymousReadOnlyArchiveRequestWhenLegacyModeIsAllowed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/img/search");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(developerApiAccessService.isRequestAllowed(request)).thenReturn(false);
        when(developerModeService.isArchiveLegacyRequestAllowed(request)).thenReturn(true);

        assertThat(loginInterceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(response.getHeader("X-MRR-Access-Mode")).isEqualTo("archive-legacy");
        AuthSession session = (AuthSession) request.getAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE);
        assertThat(session.getRoleCode()).isEqualTo("DEVELOPER_ARCHIVE");
        assertThat(session.getPermissions()).containsExactlyInAnyOrder("record:read", "search:read");
    }

    @Test
    void shouldRejectInvalidTokenWithoutFallingBackToDeveloperMode() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/img/search");
        request.addHeader("Authorization", "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(loginInterceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Token 无效或已过期");
        verify(developerApiAccessService, never()).isRequestAllowed(request);
        verify(developerModeService, never()).isArchiveLegacyRequestAllowed(request);
    }

    @Test
    void shouldRejectMalformedAuthorizationHeaderWithoutFallingBack() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/img/search");
        request.addHeader("Authorization", "Basic abc");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(loginInterceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        verify(developerApiAccessService, never()).isRequestAllowed(request);
        verify(developerModeService, never()).isArchiveLegacyRequestAllowed(request);
    }

    @Test
    void shouldRejectAnonymousRequestWhenDeveloperModeIsDisabled() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/img/search");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(developerApiAccessService.isRequestAllowed(request)).thenReturn(false);
        when(developerModeService.isArchiveLegacyRequestAllowed(request)).thenReturn(false);

        assertThat(loginInterceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
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

        assertThat(loginInterceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(503);
        verify(developerApiAccessService, never()).isRequestAllowed(request);
        verify(developerModeService, never()).isArchiveLegacyRequestAllowed(request);
    }
}
