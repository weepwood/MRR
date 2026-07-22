package com.zjcxph.imgapi.interceptors;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.entity.AuthUser;
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
import static org.mockito.ArgumentMatchers.anyString;
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
                tokenBlacklist,
                authUserMapper,
                developerModeService,
                developerApiAccessService
        );
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
        verify(developerModeService, never()).isArchiveLegacyRequestAllowed(request);
        verify(developerApiAccessService, never()).isPermissionBypassAllowed(request);
    }

    @Test
    void shouldAllowAnonymousRegistrationWithContextPathAndTrailingSlash() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mrr/api/v1/auth/register/");
        request.setContextPath("/mrr");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(loginInterceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldNotExposePublicRegistrationByGetRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/register");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(developerModeService.isArchiveLegacyRequestAllowed(request)).thenReturn(false);

        assertThat(loginInterceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void shouldRejectAnonymousFullApiRequestEvenWhenDeveloperModeIsEnabled() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/oss/migration/jobs");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(developerModeService.isArchiveLegacyRequestAllowed(request)).thenReturn(false);

        assertThat(loginInterceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(request.getAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE)).isNull();
        verify(developerApiAccessService, never()).isPermissionBypassAllowed(request);
    }

    @Test
    void shouldKeepRealIdentityAndGrantEffectivePermissionsAfterValidLogin() throws Exception {
        AuthSession tokenUser = tokenSession(20L, "doctor.test");
        String token = JwtUtil.getToken(tokenUser);
        AuthUser databaseUser = activeUser(20L, "doctor.test", "DOCTOR", "record:read");

        when(tokenBlacklist.isRevoked(anyString())).thenReturn(false);
        when(authUserMapper.findById(20L)).thenReturn(databaseUser);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/oss/migration/jobs");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(developerApiAccessService.isPermissionBypassAllowed(request)).thenReturn(true);

        assertThat(loginInterceptor.preHandle(request, response, new Object())).isTrue();
        AuthSession session = (AuthSession) request.getAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE);
        assertThat(session.getId()).isEqualTo(20L);
        assertThat(session.getUsername()).isEqualTo("doctor.test");
        assertThat(session.getRoleCode()).isEqualTo("DOCTOR");
        assertThat(session.getPermissions()).contains(
                "record:manage", "record:download", "record:pdf:export",
                "user:manage", "system:manage"
        );
        assertThat(response.getHeader("X-MRR-Access-Mode")).isEqualTo("api-permission-bypass");
    }

    @Test
    void shouldKeepConfiguredPermissionsWhenBypassIsDisabled() throws Exception {
        AuthSession tokenUser = tokenSession(21L, "nurse.test");
        String token = JwtUtil.getToken(tokenUser);
        AuthUser databaseUser = activeUser(21L, "nurse.test", "NURSE", "record:read");

        when(tokenBlacklist.isRevoked(anyString())).thenReturn(false);
        when(authUserMapper.findById(21L)).thenReturn(databaseUser);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/scan/1");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(developerApiAccessService.isPermissionBypassAllowed(request)).thenReturn(false);

        assertThat(loginInterceptor.preHandle(request, response, new Object())).isTrue();
        AuthSession session = (AuthSession) request.getAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE);
        assertThat(session.getPermissions()).containsExactly("record:read");
        assertThat(response.getHeader("X-MRR-Access-Mode")).isNull();
    }

    @Test
    void shouldAllowAnonymousReadOnlyArchiveRequestWhenLegacyModeIsAllowed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/img/search");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(developerModeService.isArchiveLegacyRequestAllowed(request)).thenReturn(true);

        assertThat(loginInterceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(response.getHeader("X-MRR-Access-Mode")).isEqualTo("archive-legacy");
        AuthSession session = (AuthSession) request.getAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE);
        assertThat(session.getRoleCode()).isEqualTo("DEVELOPER_ARCHIVE");
        assertThat(session.getPermissions()).containsExactlyInAnyOrder("record:read", "search:read");
        verify(developerApiAccessService, never()).isPermissionBypassAllowed(request);
    }

    @Test
    void shouldRejectInvalidTokenWithoutFallingBackToDeveloperMode() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/img/search");
        request.addHeader("Authorization", "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(loginInterceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Token 无效或已过期");
        verify(developerModeService, never()).isArchiveLegacyRequestAllowed(request);
        verify(developerApiAccessService, never()).isPermissionBypassAllowed(request);
    }

    @Test
    void shouldRejectMalformedAuthorizationHeaderWithoutFallingBack() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/img/search");
        request.addHeader("Authorization", "Basic abc");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(loginInterceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        verify(developerModeService, never()).isArchiveLegacyRequestAllowed(request);
        verify(developerApiAccessService, never()).isPermissionBypassAllowed(request);
    }

    @Test
    void shouldFailClosedWhenUserDatabaseIsUnavailableEvenInDeveloperMode() throws Exception {
        AuthSession tokenUser = tokenSession(22L, "doctor.fail");
        String token = JwtUtil.getToken(tokenUser);

        when(tokenBlacklist.isRevoked(anyString())).thenReturn(false);
        when(authUserMapper.findById(22L)).thenThrow(new IllegalStateException("database unavailable"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/img/search");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(loginInterceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(503);
        verify(developerApiAccessService, never()).isPermissionBypassAllowed(request);
    }

    private AuthSession tokenSession(Long id, String username) {
        AuthSession session = new AuthSession();
        session.setId(id);
        session.setUsername(username);
        session.setStatus("active");
        session.setPasswordVersion(1);
        return session;
    }

    private AuthUser activeUser(Long id, String username, String roleCode, String permissions) {
        AuthUser user = new AuthUser();
        user.setId(id);
        user.setUsername(username);
        user.setDisplayName(username);
        user.setRoleCode(roleCode);
        user.setRoleName(roleCode);
        user.setPermissionsCsv(permissions);
        user.setStatus("active");
        user.setMustChangePassword(false);
        user.setPasswordVersion(1);
        return user;
    }
}
