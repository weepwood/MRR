package com.zjcxph.imgapi.handler;

import com.zjcxph.imgapi.common.Result;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldReturnBadRequestAndPreserveCredentialErrorMessage() {
        ResponseEntity<Result<Void>> response = handler.handleIllegalArgumentException(
                new IllegalArgumentException("用户名或密码错误"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(400);
        assertThat(response.getBody().getMessage()).isEqualTo("用户名或密码错误");
    }

    @Test
    void shouldUseSafeFallbackWhenIllegalArgumentMessageIsMissing() {
        ResponseEntity<Result<Void>> response = handler.handleIllegalArgumentException(
                new IllegalArgumentException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("请求参数有误");
    }

    @Test
    void shouldNotExposeInternalExceptionMessage() {
        ResponseEntity<Result<Void>> response = handler.handleException(
                new IllegalStateException("JWT_SECRET_KEY=/internal/secret"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("服务器内部错误，请联系管理员");
        assertThat(response.getBody().getData()).isNull();
    }
}
