package com.zjcxph.imgapi.unit.interceptors;

import com.zjcxph.imgapi.config.LogRetentionProperties;
import com.zjcxph.imgapi.entity.Log;
import com.zjcxph.imgapi.interceptors.LogInterceptor;
import com.zjcxph.imgapi.service.AsyncLogService;
import com.zjcxph.imgapi.setting.SettingRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LogInterceptorTest {

    private final AsyncLogService asyncLogService = mock(AsyncLogService.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final SettingRegistry settingRegistry = new SettingRegistry(new LogRetentionProperties());
    private final LogInterceptor interceptor = new LogInterceptor(asyncLogService, meterRegistry, settingRegistry);

    @Test
    void exposesRequestIdEndpointTemplateAndServerTiming() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/scans/42");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/scans/{id}");

        interceptor.preHandle(request, response, new Object());

        assertThat(response.getHeader("X-Request-Id")).isNotBlank();
        assertThat(response.getHeader("X-Endpoint-Template")).isEqualTo("/api/v1/scans/{id}");
        assertThat(response.getHeader("Server-Timing")).isNull();

        request.setAttribute("startTime", System.currentTimeMillis() - 25);
        interceptor.postHandle(request, response, new Object(), null);

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

    @Test
    void preservesBusinessAuditValuesAndHashesCredentials() throws Exception {
        MockHttpServletRequest rawRequest = new MockHttpServletRequest(
                "POST", "/api/v1/img/image/00789508/605746/24.04.30/0072.jpg");
        rawRequest.setQueryString("bah=00123456&page=2&token=secret-token");
        rawRequest.addHeader("Referer", "http://localhost/records?bah=00123456&ticket=secret-ticket");
        rawRequest.setContentType("application/json");
        rawRequest.setContent("{\"password\":\"secret-password\",\"idCard\":\"330123456789012345\"}"
                .getBytes(StandardCharsets.UTF_8));
        ContentCachingRequestWrapper request = new ContentCachingRequestWrapper(rawRequest, 16384);
        request.getInputStream().readAllBytes();
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                "/api/v1/img/image/{bah}/{brxh}/{folder}/{filename}");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        var logCaptor = org.mockito.ArgumentCaptor.forClass(Log.class);
        verify(asyncLogService).saveLogAsync(logCaptor.capture());
        Log savedLog = logCaptor.getValue();

        assertThat(savedLog.getRequestId()).isNotBlank();
        assertThat(savedLog.getRequestUri())
                .isEqualTo("/api/v1/img/image/00789508/605746/24.04.30/0072.jpg");
        assertThat(savedLog.getEndpointTemplate())
                .isEqualTo("/api/v1/img/image/{bah}/{brxh}/{folder}/{filename}");
        assertThat(savedLog.getQueryString())
                .startsWith("bah=00123456&page=2&token=sha256:");
        assertThat(savedLog.getReferer())
                .startsWith("http://localhost/records?bah=00123456&ticket=sha256:");
        assertThat(savedLog.getRequestBody())
                .contains("330123456789012345", "password", "sha256:")
                .doesNotContain("secret-password");
        assertThat(savedLog.getAuditAction()).isEqualTo("VIEW_IMAGE");
        assertThat(savedLog.getAuditTarget()).isEqualTo("00789508");
        assertThat(savedLog.toString())
                .doesNotContain("secret-password", "secret-token", "secret-ticket");
    }

    @Test
    void auditsSensitiveRuntimeSettingChangesFromBatchPut() throws Exception {
        Log savedLog = captureSavedLog(jsonRequest(
                "PUT",
                "/api/v1/settings",
                "{\"loginMaxAttempts\":\"20\",\"accessLogRetentionDays\":\"30\"}"));

        assertThat(savedLog.getAuditAction()).isEqualTo("SETTING_UPDATE");
        assertThat(savedLog.getAuditTarget()).isEqualTo("accessLogRetentionDays,loginMaxAttempts");
        assertThat(savedLog.getAuditDescription())
                .contains("2 项敏感参数")
                .contains("loginMaxAttempts");
    }

    @Test
    void auditsSingleSettingUpdateWithPathKey() throws Exception {
        Log savedLog = captureSavedLog(jsonRequest(
                "PUT", "/api/v1/settings/sessionExpireMinutes", "{\"value\":\"120\"}"));

        assertThat(savedLog.getAuditAction()).isEqualTo("SETTING_UPDATE");
        assertThat(savedLog.getAuditTarget()).isEqualTo("sessionExpireMinutes");
    }

    @Test
    void auditsSingleSettingDelete() throws Exception {
        Log savedLog = captureSavedLog(
                new MockHttpServletRequest("DELETE", "/api/v1/settings/sessionExpireMinutes"));

        assertThat(savedLog.getAuditAction()).isEqualTo("SETTING_DELETE");
        assertThat(savedLog.getAuditTarget()).isEqualTo("sessionExpireMinutes");
    }

    @Test
    void auditsNonSensitiveRegisteredSettingWithoutSensitiveCount() throws Exception {
        Log savedLog = captureSavedLog(jsonRequest(
                "PUT", "/api/v1/settings", "{\"accessLogCleanupBatchSize\":\"1000\"}"));

        assertThat(savedLog.getAuditAction()).isEqualTo("SETTING_UPDATE");
        assertThat(savedLog.getAuditTarget()).isEqualTo("accessLogCleanupBatchSize");
        assertThat(savedLog.getAuditDescription())
                .startsWith("修改运行参数: ")
                .doesNotContain("敏感");
    }

    @Test
    void doesNotAuditUnregisteredSettingKeys() throws Exception {
        // adminAppSettings 是前端同步的界面外观键，未在注册表中登记，不应写满审计表。
        Log savedLog = captureSavedLog(jsonRequest(
                "PUT", "/api/v1/settings", "{\"adminAppSettings\":\"{\\\"app\\\":{}}\"}"));

        assertThat(savedLog.getAuditAction()).isNull();
        assertThat(savedLog.getAuditTarget()).isNull();
    }

    @Test
    void doesNotAuditSettingReads() throws Exception {
        Log savedLog = captureSavedLog(new MockHttpServletRequest("GET", "/api/v1/settings"));

        assertThat(savedLog.getAuditAction()).isNull();
    }

    @Test
    void doesNotAuditNestedSettingsSubPathAsSettingKey() throws Exception {
        Log savedLog = captureSavedLog(
                new MockHttpServletRequest("PUT", "/api/v1/settings/unknown/nested"));

        assertThat(savedLog.getAuditAction()).isNull();
    }

    @Test
    void doesNotAuditMalformedSettingsBody() throws Exception {
        Log savedLog = captureSavedLog(jsonRequest("PUT", "/api/v1/settings", "not-json"));

        assertThat(savedLog.getAuditAction()).isNull();
    }

    private Log captureSavedLog(HttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        var logCaptor = org.mockito.ArgumentCaptor.forClass(Log.class);
        verify(asyncLogService).saveLogAsync(logCaptor.capture());
        return logCaptor.getValue();
    }

    private ContentCachingRequestWrapper jsonRequest(String method, String uri, String body)
            throws Exception {
        MockHttpServletRequest rawRequest = new MockHttpServletRequest(method, uri);
        rawRequest.setContentType("application/json");
        rawRequest.setContent(body.getBytes(StandardCharsets.UTF_8));
        ContentCachingRequestWrapper request = new ContentCachingRequestWrapper(rawRequest, 16384);
        request.getInputStream().readAllBytes();
        return request;
    }
}
