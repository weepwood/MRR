package com.zjcxph.imgapi.handler;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.common.ResultCode;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 保持统一响应体兼容性的同时，将失败业务码同步为真实 HTTP 状态码。
 *
 * <p>只修正仍处于 2xx 的失败响应，不覆盖 Controller 已通过
 * {@code ResponseEntity} 或 Servlet API 明确设置的 4xx/5xx 状态。</p>
 */
@RestControllerAdvice
public class ResultHttpStatusAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (!(body instanceof Result<?> result) || result.isSuccess()) {
            return body;
        }

        if (isSuccessfulResponse(response)) {
            response.setStatusCode(ResultCode.resolveHttpStatus(result.getCode()));
        }
        return body;
    }

    private boolean isSuccessfulResponse(ServerHttpResponse response) {
        if (response instanceof ServletServerHttpResponse servletResponse) {
            int currentStatus = servletResponse.getServletResponse().getStatus();
            return currentStatus >= 200 && currentStatus < 300;
        }

        // 当前项目使用 Spring MVC。非 Servlet 响应没有统一的状态读取接口，
        // 对 Result 失败响应仍按业务码设置状态，避免继续返回默认 200。
        return true;
    }
}
