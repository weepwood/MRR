package com.zjcxph.imgapi.interceptors;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.utils.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordChangeRequiredInterceptorTest {

    private final PasswordChangeRequiredInterceptor interceptor = new PasswordChangeRequiredInterceptor();

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void shouldRejectBusinessApiWhenPasswordChangeIsRequired() throws Exception {
        installSession(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/patients");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(428);
        assertThat(response.getContentAsString()).contains("AUTH_PASSWORD_CHANGE_REQUIRED");
    }

    @Test
    void shouldAllowRequiredPasswordChangeEndpoint() throws Exception {
        installSession(true);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/v1/auth/password/required-change");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    void shouldAllowNormalUser() throws Exception {
        installSession(false);
        HttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/patients");
        HttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    private void installSession(boolean mustChangePassword) {
        AuthSession session = new AuthSession();
        session.setId(10L);
        session.setUsername("tester");
        session.setMustChangePassword(mustChangePassword);
        session.setPasswordVersion(1);
        AuthContext.setCurrentUser(session);
    }
}
