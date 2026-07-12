package com.zjcxph.imgapi.unit.interceptors;

import com.zjcxph.imgapi.entity.Log;
import com.zjcxph.imgapi.interceptors.LogInterceptor;
import com.zjcxph.imgapi.service.AsyncLogService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LogInterceptorTest {

    private final AsyncLogService asyncLogService = mock(AsyncLogService.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final LogInterceptor interceptor = new LogInterceptor(asyncLogService, meterRegistry);

    @Test
    void exposesRequestIdEndpointTemplateAndServerTiming() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/scans/42");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/scans/{id}");
        request.setAttribute("startTime", System.currentTimeMillis() - 25);
        interceptor.postHandle(request, response, new Object(), null);

        assertThat(response.getHeader("X-Request-Id")).isNotBlank();
        assertThat(response.getHeader("X-Endpoint-Template")).isEqualTo("/api/v1/scans/{id}");
        assertThat(response.getHeader("Server-Timing")).matches("app;dur=\\d+");
    }

    @Test
    void recordsTimerWithEndpointTemplateInsteadOfRawUri() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/scans/42");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/scans/{id}");
        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(meterRegistry.find("http.requests.duration")
                .tag("uri", "/api/v1/scans/{id}")
                .timer()).isNotNull();
        assertThat(meterRegistry.find("http.requests.duration")
                .tag("uri", "/api/v1/scans/42")
                .timer()).isNull();
        verify(asyncLogService).saveLogAsync(any(Log.class));
    }
}
