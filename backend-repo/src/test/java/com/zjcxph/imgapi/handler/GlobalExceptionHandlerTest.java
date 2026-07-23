package com.zjcxph.imgapi.handler;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.common.ResultCode;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.utils.ErrorReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

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
    void shouldMapExtendedBusinessHttpStatus() {
        ResponseEntity<Result<Void>> response = handler.handleBusinessException(
                new BusinessException(503, "认证用户服务暂时不可用"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(503);
    }

    @Test
    void shouldMapRegisteredBusinessCode() {
        ResponseEntity<Result<Void>> response = handler.handleBusinessException(
                new BusinessException(ResultCode.PASSWORD_WRONG.getCode(), "用户名或密码错误"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ResultCode.PASSWORD_WRONG.getCode());
    }

    @Test
    void shouldNotExposeInternalExceptionMessage() {
        ResponseEntity<Result<Void>> response = handler.handleException(
                new IllegalStateException("JWT_SECRET_KEY=/internal/secret"),
                new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).startsWith("服务器内部错误，请联系管理员（错误编号：ERR-");
        assertThat(response.getBody().getMessage()).doesNotContain("JWT_SECRET_KEY", "/internal/secret");
        assertThat(response.getHeaders().getFirst(ErrorReference.RESPONSE_HEADER)).isNotBlank();
        assertThat(response.getBody().getData()).isNull();
    }
}
