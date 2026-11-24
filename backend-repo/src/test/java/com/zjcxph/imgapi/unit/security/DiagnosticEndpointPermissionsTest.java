package com.zjcxph.imgapi.unit.security;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.config.ImageDeliveryProperties;
import com.zjcxph.imgapi.controller.OperationsDiagnosticsController;
import com.zjcxph.imgapi.controller.QueryDiagnosticsController;
import com.zjcxph.imgapi.handler.ResultHttpStatusAdvice;
import com.zjcxph.imgapi.interceptors.AuthorizationInterceptor;
import com.zjcxph.imgapi.interceptors.LoginInterceptor;
import com.zjcxph.imgapi.mapper.AuthUserMapper;
import com.zjcxph.imgapi.security.TokenBlacklist;
import com.zjcxph.imgapi.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.HandlerMethod;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DiagnosticEndpointPermissionsTest {
    @Test void anonymousRequestGets401WithoutExistenceEvidence() throws Exception {
        var login = new LoginInterceptor(mock(TokenBlacklist.class), mock(AuthUserMapper.class), mock(DeveloperModeService.class), mock(DeveloperApiAccessService.class));
        var response = new MockHttpServletResponse();
        assertThat(login.preHandle(new MockHttpServletRequest("POST","/api/v1/operations/query-diagnostics"), response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).doesNotContain("samples", "archive", "bah");
    }

    @Test void diagnosticRequiresBothMedicalAndOperationsPermissions() throws Exception {
        var service = mock(QueryDiagnosticsService.class);
        var controller = new QueryDiagnosticsController(service, true);
        var handler = new HandlerMethod(controller, QueryDiagnosticsController.class.getMethod("diagnose",QueryDiagnosticsController.Query.class));
        for (var permissions : List.of(List.of("system:manage"),List.of("record:read"))) {
            var response = new MockHttpServletResponse();
            assertThat(new AuthorizationInterceptor().preHandle(request(permissions),response,handler)).isFalse();
            assertThat(response.getStatus()).isEqualTo(403);
        }
        assertThat(new AuthorizationInterceptor().preHandle(request(List.of("system:manage","record:read")),new MockHttpServletResponse(),handler)).isTrue();
        verifyNoInteractions(service);
    }

    @Test
    void disabledQueryDiagnosticsReturns404WithoutProbing() throws Exception {
        var service = mock(QueryDiagnosticsService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new QueryDiagnosticsController(service, false))
                .setControllerAdvice(new ResultHttpStatusAdvice())
                .build();

        mvc.perform(post("/api/v1/operations/query-diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bah\":\"123\",\"sjh\":\"\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.data").doesNotExist());
        verifyNoInteractions(service);
    }

    @Test
    void operationsOverviewAndFullDiagnosticHaveSeparatePermissions() throws Exception {
        var service = mock(OperationsCenterService.class);
        var controller = new OperationsDiagnosticsController(mock(OperationsDiagnosticsService.class),
                service, mock(ImageSourceDiagnosticsService.class), mock(IntegrityDiagnosticsService.class),
                mock(MaintenanceModeService.class));
        var overview = new HandlerMethod(controller, OperationsDiagnosticsController.class.getMethod("overview"));
        var diagnostic = new HandlerMethod(controller, OperationsDiagnosticsController.class.getMethod("runDiagnostics"));
        var authorization = new AuthorizationInterceptor();

        var anonymous = new MockHttpServletResponse();
        assertThat(authorization.preHandle(new MockHttpServletRequest("GET", "/api/v1/operations/overview"),
                anonymous, overview)).isFalse();
        assertThat(anonymous.getStatus()).isEqualTo(401);

        var forbidden = new MockHttpServletResponse();
        assertThat(authorization.preHandle(request("GET", "/api/v1/operations/overview", List.of("record:read")),
                forbidden, overview)).isFalse();
        assertThat(forbidden.getStatus()).isEqualTo(403);
        assertThat(authorization.preHandle(request("GET", "/api/v1/operations/overview", List.of("system:read")),
                new MockHttpServletResponse(), overview)).isTrue();

        var forbiddenDiagnostic = new MockHttpServletResponse();
        assertThat(authorization.preHandle(request("POST", "/api/v1/operations/diagnostics/run", List.of("system:read")),
                forbiddenDiagnostic, diagnostic)).isFalse();
        assertThat(forbiddenDiagnostic.getStatus()).isEqualTo(403);
        assertThat(authorization.preHandle(request("POST", "/api/v1/operations/diagnostics/run", List.of("system:manage")),
                new MockHttpServletResponse(), diagnostic)).isTrue();
        verifyNoInteractions(service);
    }

    @Test
    void overviewExposesOnlyConfiguredImageModeNotSourceAddresses() {
        var service = mock(OperationsCenterService.class);
        when(service.overview()).thenReturn(java.util.Map.of("generatedAt", "synthetic"));
        var controller = new OperationsDiagnosticsController(mock(OperationsDiagnosticsService.class),
                service, mock(ImageSourceDiagnosticsService.class), mock(IntegrityDiagnosticsService.class),
                mock(MaintenanceModeService.class));
        assertThat(controller.overview().getData()).containsEntry("imageDeliveryMode", "direct");
        assertThat(controller.overview().getData()).doesNotContainKeys("sourceUrl", "secret", "cookie");
    }

    private MockHttpServletRequest request(List<String> permissions) {
        return request("POST", "/api/v1/operations/query-diagnostics", permissions);
    }

    private MockHttpServletRequest request(String method, String path, List<String> permissions) {
        var request = new MockHttpServletRequest(method, path);
        var session=new AuthSession();session.setRoleCode("CUSTOM");session.setPermissions(permissions);
        request.setAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE,session);
        return request;
    }
}
