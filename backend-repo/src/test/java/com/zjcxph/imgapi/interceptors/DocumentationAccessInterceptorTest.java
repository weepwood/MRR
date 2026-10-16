package com.zjcxph.imgapi.interceptors;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.service.DocumentationAccessService;
import com.zjcxph.imgapi.utils.JwtUtil;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文档访问拦截器的行为测试。
 *
 * <p>该拦截器在修复前是一段死代码：类上标注了 {@code @Component}，
 * 但从未在 {@code WebConfig} 中注册，导致 OpenAPI 规格与 Swagger UI 对
 * 未登录访问者完全公开。本测试锁定其放行与拒绝语义，注册本身由
 * {@code WebConfigDocumentationAccessRegistrationTest} 守护。</p>
 */
class DocumentationAccessInterceptorTest {

    private static final String JWT_SECRET = "documentation-access-interceptor-test-secret-key";

    private final DocumentationAccessInterceptor interceptor =
            new DocumentationAccessInterceptor(new DocumentationAccessService());

    @BeforeAll
    static void configureJwtSecret() {
        JwtUtil.configure(JWT_SECRET);
    }

    @Test
    void rejectsAnonymousOpenApiRequestWithUnauthorized() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v3/api-docs");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Documentation session required");
    }

    @Test
    void rejectsNormalUserAccessingInternalDocumentationWithForbidden() throws Exception {
        MockHttpServletRequest request = documentationRequest("/swagger-ui/index.html");
        request.setCookies(accessCookie(session("USER", List.of("record:read"))));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("No permission to access documentation");
    }

    @Test
    void allowsOperatorHoldingSystemRead() throws Exception {
        assertAllowed("/v3/api-docs", session("USER", List.of("system:read")));
        assertAllowed("/v3/api-docs.yaml", session("USER", List.of("system:read")));
        assertAllowed("/swagger-ui.html", session("USER", List.of("system:read")));
    }

    @Test
    void allowsAdministrator() throws Exception {
        assertAllowed("/swagger-ui/index.html", session("ADMIN", List.of()));
    }

    @Test
    void allowsAnyAuthenticatedUserToReadUserGuide() throws Exception {
        assertAllowed("/docs/", session("USER", List.of("record:read")));
    }

    @Test
    void rejectsAccessTokenReusedAsDocumentationSession() throws Exception {
        AuthSession session = session("ADMIN", List.of());
        String accessToken = JwtUtil.getToken(session, 60_000L, JwtUtil.ACCESS_TOKEN_TYPE);

        MockHttpServletRequest request = documentationRequest("/v3/api-docs");
        request.setCookies(new Cookie(DocumentationAccessService.COOKIE_NAME, accessToken));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void rejectsTamperedDocumentationToken() throws Exception {
        AuthSession session = session("ADMIN", List.of());
        String token = JwtUtil.getToken(
                session,
                DocumentationAccessService.SESSION_EXPIRE_SECONDS * 1000L,
                JwtUtil.DOCUMENTATION_TOKEN_TYPE
        );

        MockHttpServletRequest request = documentationRequest("/v3/api-docs");
        request.setCookies(new Cookie(DocumentationAccessService.COOKIE_NAME, token + "tampered"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void rejectsSessionWhoseStatusIsNotActive() throws Exception {
        AuthSession session = session("ADMIN", List.of());
        session.setStatus("disabled");

        MockHttpServletRequest request = documentationRequest("/v3/api-docs");
        request.setCookies(accessCookie(session));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void allowsCrossOriginPreflightWithoutSession() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/v3/api-docs");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private void assertAllowed(String uri, AuthSession session) throws Exception {
        MockHttpServletRequest request = documentationRequest(uri);
        request.setCookies(accessCookie(session));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object()))
                .as("应放行 %s", uri)
                .isTrue();
    }

    private MockHttpServletRequest documentationRequest(String uri) {
        return new MockHttpServletRequest("GET", uri);
    }

    private Cookie accessCookie(AuthSession session) {
        String token = JwtUtil.getToken(
                session,
                DocumentationAccessService.SESSION_EXPIRE_SECONDS * 1000L,
                JwtUtil.DOCUMENTATION_TOKEN_TYPE
        );
        return new Cookie(DocumentationAccessService.COOKIE_NAME, token);
    }

    private AuthSession session(String roleCode, List<String> permissions) {
        AuthSession session = new AuthSession();
        session.setId(1L);
        session.setUsername("tester");
        session.setStatus("active");
        session.setRoleCode(roleCode);
        session.setPermissions(permissions);
        return session;
    }
}
