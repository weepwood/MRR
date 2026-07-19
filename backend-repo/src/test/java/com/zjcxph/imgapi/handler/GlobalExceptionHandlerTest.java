package com.zjcxph.imgapi.handler;

import com.zjcxph.imgapi.common.Result;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldReturnBadRequestAndPreserveCredentialErrorMessage() {
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        ResponseEntity<Result<Void>> response = handler.handleIllegalArgumentException(
                new IllegalArgumentException("用户名或密码错误"), servletResponse);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(400);
        assertThat(response.getBody().getMessage()).isEqualTo("用户名或密码错误");
        assertThat(servletResponse.getHeader("X-Error-Code")).isEqualTo("MRR-COMMON-4001");
    }

    @Test
    void shouldUseSafeFallbackWhenIllegalArgumentMessageIsMissing() {
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        ResponseEntity<Result<Void>> response = handler.handleIllegalArgumentException(
                new IllegalArgumentException(), servletResponse);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("请求参数错误");
    }

    @Test
    void shouldNotExposeInternalExceptionMessage() {
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        ResponseEntity<Result<Void>> response = handler.handleException(
                new IllegalStateException("JWT_SECRET_KEY=/internal/secret"), servletResponse);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("服务器内部错误，请联系管理员");
        assertThat(response.getBody().getData()).isNull();
    }
}
