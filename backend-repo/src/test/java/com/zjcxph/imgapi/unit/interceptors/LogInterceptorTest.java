package com.zjcxph.imgapi.unit.interceptors;

import com.zjcxph.imgapi.common.AppErrorCode;
import com.zjcxph.imgapi.entity.Log;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.interceptors.LogInterceptor;
import com.zjcxph.imgapi.service.AsyncLogService;
import com.zjcxph.imgapi.service.ReliableAuditService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class LogInterceptorTest {

    private final AsyncLogService asyncLogService = mock(AsyncLogService.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final LogInterceptor interceptor = new LogInterceptor(asyncLogService, meterRegistry);

    @Test
    void exposesRequestIdEndpointTemplateAndServerTiming() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/scans/42");
        request.addHeader("X-Request-Id", "nginx-request-1234");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/scans/{id}");

        interceptor.preHandle(request, response, new Object());

        assertThat(response.getHeader("X-Request-Id")).isEqualTo("nginx-request-1234");
        assertThat(response.getHeader("X-Endpoint-Template")).isEqualTo("/api/v1/scans/{id}");
        assertThat(response.getHeader("Server-Timing")).isNull();

        request.setAttribute("startTime", System.currentTimeMillis() - 25);
        interceptor.postHandle(request, response, new Object(), null);

        assertThat(response.getHeader("Server-Timing")).matches("app;dur=\\d+");
    }

    @Test
    void rejectsUnsafeClientSuppliedRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/scans/42");
        request.addHeader("X-Request-Id", "bad request id with spaces\r\nInjected: true");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());

        assertThat(response.getHeader("X-Request-Id"))
                .matches("[0-9a-f]{32}")
                .doesNotContain("Injected");
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

    @Test
    void omitsRequestPayloadValuesAndUsesEndpointTemplateInAuditLog() throws Exception {
        MockHttpServletRequest rawRequest = new MockHttpServletRequest(
                "POST", "/api/v1/img/image/00789508/605746/24.04.30/0072.jpg");
        rawRequest.setQueryString("bah=00123456&page=2");
        rawRequest.addHeader("Referer", "http://localhost/records?token=secret-token");
        rawRequest.setContentType("application/json");
        rawRequest.setContent("{\"password\":\"secret-password\",\"idCard\":\"330123456789012345\"}"
                .getBytes(StandardCharsets.UTF_8));
        ContentCachingRequestWrapper request = new ContentCachingRequestWrapper(rawRequest, 10240);
        request.getInputStream().readAllBytes();
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                "/api/v1/img/image/{bah}/{brxh}/{folder}/{filename}");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        var logCaptor = org.mockito.ArgumentCaptor.forClass(Log.class);
        verify(asyncLogService).saveLogAsync(logCaptor.capture());
        Log savedLog = logCaptor.getValue();

        assertThat(savedLog.getRequestUri())
                .isEqualTo("/api/v1/img/image/{bah}/{brxh}/{folder}/{filename}");
        assertThat(savedLog.getQueryString()).isEqualTo("bah=[REDACTED]&page=[REDACTED]");
        assertThat(savedLog.getRequestBody()).matches("\\[OMITTED \\d+ bytes]");
        assertThat(savedLog.getReferer()).isEqualTo("[REDACTED]");
        assertThat(savedLog.getAuditAction()).isEqualTo("VIEW_IMAGE");
        assertThat(savedLog.getAuditTarget()).matches("sha256:[0-9a-f]{32}");
        assertThat(savedLog.toString())
                .doesNotContain("00789508", "605746", "0072.jpg", "secret-password",
                        "330123456789012345", "secret-token");
    }

    @Test
    void preflightsDurableAuditFallbackForSensitiveRequests() throws Exception {
        ReliableAuditService reliableAuditService = mock(ReliableAuditService.class);
        LogInterceptor secureInterceptor = new LogInterceptor(
                asyncLogService,
                reliableAuditService,
                meterRegistry,
                "audit-hmac-secret"
        );
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/v1/img/image/00789508/605746/24.04.30/0072.jpg");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(secureInterceptor.preHandle(request, response, new Object())).isTrue();

        verify(reliableAuditService).assertFallbackAvailable();
    }

    @Test
    void doesNotProbeAuditFallbackForOrdinaryReadOnlyEndpoints() throws Exception {
        ReliableAuditService reliableAuditService = mock(ReliableAuditService.class);
        LogInterceptor secureInterceptor = new LogInterceptor(
                asyncLogService,
                reliableAuditService,
                meterRegistry,
                "audit-hmac-secret"
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/scans/42");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(secureInterceptor.preHandle(request, response, new Object())).isTrue();

        verify(reliableAuditService, never()).assertFallbackAvailable();
    }

    @Test
    void blocksSensitiveRequestWhenAuditFallbackIsUnavailable() {
        ReliableAuditService reliableAuditService = mock(ReliableAuditService.class);
        doThrow(new BusinessException(AppErrorCode.AUDIT_UNAVAILABLE))
                .when(reliableAuditService).assertFallbackAvailable();
        LogInterceptor secureInterceptor = new LogInterceptor(
                asyncLogService,
                reliableAuditService,
                meterRegistry,
                "audit-hmac-secret"
        );
        MockHttpServletRequest request = new MockHttpServletRequest(
                "DELETE", "/api/v1/oss/object/123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> secureInterceptor.preHandle(request, response, new Object()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("审计服务暂不可用");
    }
}
