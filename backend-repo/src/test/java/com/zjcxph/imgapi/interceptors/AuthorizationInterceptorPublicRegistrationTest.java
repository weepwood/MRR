package com.zjcxph.imgapi.interceptors;

import com.zjcxph.imgapi.controller.UserController;
import com.zjcxph.imgapi.dto.req.RegisterRequest;
import com.zjcxph.imgapi.security.TokenBlacklist;
import com.zjcxph.imgapi.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AuthorizationInterceptorPublicRegistrationTest {

    @Test
    void shouldAllowAnonymousRegistrationControllerMethod() throws Exception {
        UserController controller = new UserController(mock(AuthService.class), mock(TokenBlacklist.class));
        Method method = UserController.class.getMethod(
                "register",
                RegisterRequest.class,
                HttpServletRequest.class
        );
        HandlerMethod handlerMethod = new HandlerMethod(controller, method);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/register");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = new AuthorizationInterceptor().preHandle(request, response, handlerMethod);

        assertThat(allowed).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
