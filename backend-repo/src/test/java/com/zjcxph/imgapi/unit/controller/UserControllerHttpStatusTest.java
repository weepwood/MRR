package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.controller.UserController;
import com.zjcxph.imgapi.dto.req.UserRequest;
import com.zjcxph.imgapi.dto.resp.LoginResponseDTO;
import com.zjcxph.imgapi.security.TokenBlacklist;
import com.zjcxph.imgapi.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerHttpStatusTest {

    @Mock
    private AuthService authService;
    @Mock
    private TokenBlacklist tokenBlacklist;
    @Mock
    private HttpServletRequest httpServletRequest;

    @Test
    void credentialFailureShouldReturnUnauthorizedResult() {
        UserRequest request = new UserRequest();
        request.setUsername("admin");
        request.setPassword("wrong-password");
        when(httpServletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(authService.login(request, "127.0.0.1"))
                .thenThrow(new IllegalArgumentException("用户名或密码错误"));

        UserController controller = new UserController(authService, tokenBlacklist);
        Result<LoginResponseDTO> result = controller.login(request, httpServletRequest);

        assertThat(result.getCode()).isEqualTo(401);
        assertThat(result.getMessage()).isEqualTo("用户名或密码错误");
    }
}
