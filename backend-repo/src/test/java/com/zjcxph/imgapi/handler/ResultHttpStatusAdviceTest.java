package com.zjcxph.imgapi.handler;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.common.ResultCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ResultHttpStatusAdviceTest {

    private final ResultHttpStatusAdvice advice = new ResultHttpStatusAdvice();

    @Test
    void shouldConvertDefaultFailureToBadRequest() {
        MockHttpServletResponse response = apply(Result.fail("参数错误"));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void shouldConvertAuthenticationFailureToUnauthorized() {
        MockHttpServletResponse response = apply(Result.unauthorized("用户名或密码错误"));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void shouldSupportStandardExtendedHttpStatuses() {
        assertThat(apply(Result.fail(410, "接口已停用")).getStatus())
                .isEqualTo(HttpStatus.GONE.value());
        assertThat(apply(Result.fail(422, "数据无法处理")).getStatus())
                .isEqualTo(422);
        assertThat(apply(Result.fail(428, "需要修改密码")).getStatus())
                .isEqualTo(428);
        assertThat(apply(Result.fail(503, "服务不可用")).getStatus())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
    }

    @Test
    void shouldResolveRegisteredBusinessCode() {
        MockHttpServletResponse response = apply(
                Result.fail(ResultCode.USER_NOT_FOUND.getCode(), "用户不存在"));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void shouldNotOverrideExplicitErrorStatus() {
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        servletResponse.setStatus(HttpStatus.CONFLICT.value());
        apply(Result.fail("参数错误"), servletResponse);

        assertThat(servletResponse.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    void shouldLeaveSuccessfulResultAtHttp200() {
        MockHttpServletResponse response = apply(Result.success("成功"));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    private MockHttpServletResponse apply(Result<?> result) {
        return apply(result, new MockHttpServletResponse());
    }

    private MockHttpServletResponse apply(Result<?> result, MockHttpServletResponse servletResponse) {
        advice.beforeBodyWrite(
                result,
                null,
                MediaType.APPLICATION_JSON,
                MappingJackson2HttpMessageConverter.class,
                null,
                new ServletServerHttpResponse(servletResponse));
        return servletResponse;
    }
}
