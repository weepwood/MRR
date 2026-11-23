package com.zjcxph.imgapi.interceptors;

import com.zjcxph.imgapi.security.ImageAccessCookieService;
import com.zjcxph.imgapi.service.DocumentationAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentationSessionCleanupInterceptorTest {

    @Test
    void logoutExpiresDocumentationAndImageAccessCookies() {
        var interceptor = new DocumentationSessionCleanupInterceptor(ImageAccessCookieService.disabled());
        var response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(new MockHttpServletRequest("POST", "/api/v1/auth/logout"), response, new Object()))
                .isTrue();

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(header -> assertThat(header)
                        .startsWith(DocumentationAccessService.COOKIE_NAME + "=;").contains("Max-Age=0"))
                .anySatisfy(header -> assertThat(header)
                        .startsWith(ImageAccessCookieService.COOKIE_NAME + "=;").contains("Max-Age=0", "HttpOnly"));
    }
}
