package com.zjcxph.imgapi.interceptors;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.config.ImageDeliveryProperties;
import com.zjcxph.imgapi.entity.AuthUser;
import com.zjcxph.imgapi.mapper.AuthUserMapper;
import com.zjcxph.imgapi.security.ImageAccessCookieService;
import com.zjcxph.imgapi.security.TokenBlacklist;
import com.zjcxph.imgapi.service.DeveloperApiAccessService;
import com.zjcxph.imgapi.service.DeveloperModeService;
import com.zjcxph.imgapi.utils.JwtUtil;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoginInterceptor 内容网关影像访问 Cookie 认证（ADR-0009）")
class LoginInterceptorImageAccessTest {

    private static final long USER_ID = 31L;

    @Mock private TokenBlacklist tokenBlacklist;
    @Mock private AuthUserMapper authUserMapper;
    @Mock private DeveloperModeService developerModeService;
    @Mock private DeveloperApiAccessService developerApiAccessService;

    private LoginInterceptor loginInterceptor;
    private String bearerToken;

    @BeforeEach
    void setUp() {
        JwtUtil.configure("login-image-access-test-secret-0123456789abcdef");
        ImageDeliveryProperties properties = new ImageDeliveryProperties();
        properties.setMode(ImageDeliveryProperties.Mode.GATEWAY);
        loginInterceptor = new LoginInterceptor(tokenBlacklist, authUserMapper, developerModeService,
                developerApiAccessService, new ImageAccessCookieService(properties));

        AuthSession tokenUser = new AuthSession();
        tokenUser.setId(USER_ID);
        tokenUser.setUsername("synthetic.reader");
        tokenUser.setStatus("active");
        tokenUser.setPasswordVersion(2);
        bearerToken = JwtUtil.getToken(tokenUser);
    }

    @AfterEach
    void tearDown() {
        loginInterceptor.afterCompletion(null, null, null, null);
        org.slf4j.MDC.clear();
    }

    @Test
    @DisplayName("Bearer 认证的档案袋列表请求签发 Cookie，随后无 Token 的内容网关请求凭 Cookie 通过")
    void issuesCookieOnListRequestAndAcceptsItOnContentGateway() throws Exception {
        String cookie = issueCookie();

        when(tokenBlacklist.isRevokedHash(anyString())).thenReturn(false);
        when(authUserMapper.findById(USER_ID)).thenReturn(user("active", 2));
        MockHttpServletRequest content = contentRequest(cookie);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(loginInterceptor.preHandle(content, response, new Object())).isTrue();
        AuthSession session = (AuthSession) content.getAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE);
        assertThat(session.getId()).isEqualTo(USER_ID);
        assertThat(session.getPermissions()).contains("record:read");
        assertThat(content.getAttribute(LoginInterceptor.ACCESS_MODE_ATTRIBUTE))
                .isEqualTo(ImageAccessCookieService.ACCESS_MODE);
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
        verify(developerModeService, never()).isArchiveLegacyRequestAllowed(content);
    }

    @Test
    @DisplayName("签发令牌已登出（撤销）后 Cookie 立即失效，且不加载用户")
    void rejectsCookieOfRevokedToken() throws Exception {
        String cookie = issueCookie();
        when(tokenBlacklist.isRevokedHash(anyString())).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(loginInterceptor.preHandle(contentRequest(cookie), response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("REVOKED_TOKEN");
    }

    @Test
    @DisplayName("密码变更或账号停用后 Cookie 失效")
    void rejectsCookieAfterPasswordChangeOrDisable() throws Exception {
        String cookie = issueCookie();
        when(tokenBlacklist.isRevokedHash(anyString())).thenReturn(false);

        when(authUserMapper.findById(USER_ID)).thenReturn(user("active", 3));
        MockHttpServletResponse changed = new MockHttpServletResponse();
        assertThat(loginInterceptor.preHandle(contentRequest(cookie), changed, new Object())).isFalse();
        assertThat(changed.getContentAsString()).contains("AUTH_CREDENTIAL_CHANGED");

        when(authUserMapper.findById(USER_ID)).thenReturn(user("disabled", 2));
        MockHttpServletResponse disabled = new MockHttpServletResponse();
        assertThat(loginInterceptor.preHandle(contentRequest(cookie), disabled, new Object())).isFalse();
        assertThat(disabled.getContentAsString()).contains("DISABLED_USER");
    }

    @Test
    @DisplayName("伪造 Cookie 在内存中被拒绝，不查询撤销表与用户表")
    void rejectsForgedCookieWithoutDatabaseAccess() throws Exception {
        String forged = "v1." + USER_ID + ".2." + "c".repeat(64) + ".9999999999.forged-signature";
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(loginInterceptor.preHandle(contentRequest(forged), response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("INVALID_IMAGE_ACCESS");
        verifyNoInteractions(tokenBlacklist, authUserMapper);
    }

    @Test
    @DisplayName("Cookie 不能用于内容网关以外的接口")
    void ignoresCookieOutsideContentGateway() throws Exception {
        String cookie = issueCookie();
        MockHttpServletRequest other = new MockHttpServletRequest("GET", "/api/v1/statistics/dashboard");
        other.setCookies(new Cookie(ImageAccessCookieService.COOKIE_NAME, cookie));
        when(developerModeService.isArchiveLegacyRequestAllowed(other)).thenReturn(false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(loginInterceptor.preHandle(other, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        verify(tokenBlacklist, never()).isRevokedHash(anyString());
    }

    @Test
    @DisplayName("直连模式下内容网关不接受 Cookie")
    void ignoresCookieWhenGatewayDisabled() throws Exception {
        String cookie = issueCookie();
        LoginInterceptor direct = new LoginInterceptor(tokenBlacklist, authUserMapper, developerModeService,
                developerApiAccessService, ImageAccessCookieService.disabled());
        MockHttpServletRequest content = contentRequest(cookie);
        when(developerModeService.isArchiveLegacyRequestAllowed(content)).thenReturn(false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(direct.preHandle(content, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        verify(tokenBlacklist, never()).isRevokedHash(anyString());
    }

    @Test
    @DisplayName("撤销状态存储故障时 fail-closed 返回 503")
    void failsClosedWhenRevocationStoreUnavailable() throws Exception {
        String cookie = issueCookie();
        when(tokenBlacklist.isRevokedHash(anyString()))
                .thenThrow(new DataAccessResourceFailureException("SYNTHETIC-PRIVATE-DATA"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(loginInterceptor.preHandle(contentRequest(cookie), response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).doesNotContain("SYNTHETIC-PRIVATE-DATA");
        verifyNoInteractions(authUserMapper);
    }

    private String issueCookie() throws Exception {
        when(tokenBlacklist.isRevoked(anyString())).thenReturn(false);
        when(authUserMapper.findById(USER_ID)).thenReturn(user("active", 2));
        MockHttpServletRequest list = new MockHttpServletRequest("GET", "/api/v1/img/search");
        list.addHeader("Authorization", "Bearer " + bearerToken);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(loginInterceptor.preHandle(list, response, new Object())).isTrue();
        loginInterceptor.afterCompletion(list, response, new Object(), null);
        String header = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(header).startsWith(ImageAccessCookieService.COOKIE_NAME + "=v1." + USER_ID + ".2.");
        org.mockito.Mockito.clearInvocations(tokenBlacklist, authUserMapper);
        String pair = header.substring(0, header.indexOf(';'));
        return pair.substring(pair.indexOf('=') + 1);
    }

    private static MockHttpServletRequest contentRequest(String cookie) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/img/content/5");
        request.setCookies(new Cookie(ImageAccessCookieService.COOKIE_NAME, cookie));
        return request;
    }

    private static AuthUser user(String status, int passwordVersion) {
        AuthUser user = new AuthUser();
        user.setId(USER_ID);
        user.setUsername("synthetic.reader");
        user.setDisplayName("synthetic.reader");
        user.setRoleCode("READER");
        user.setRoleName("READER");
        user.setPermissionsCsv("record:read");
        user.setStatus(status);
        user.setMustChangePassword(false);
        user.setPasswordVersion(passwordVersion);
        return user;
    }
}
