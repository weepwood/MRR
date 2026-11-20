package com.zjcxph.imgapi.unit.security;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.controller.NginxManagementController;
import com.zjcxph.imgapi.interceptors.AuthorizationInterceptor;
import com.zjcxph.imgapi.service.NginxManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class NginxManagementEndpointPermissionsTest {
    private final NginxManagementService service = mock(NginxManagementService.class);
    private final NginxManagementController controller = new NginxManagementController(service);
    private final AuthorizationInterceptor authorization = new AuthorizationInterceptor();

    @Test
    void statusRequiresSystemRead() throws Exception {
        HandlerMethod method = new HandlerMethod(controller,
                NginxManagementController.class.getMethod("status"));
        MockHttpServletResponse anonymous = new MockHttpServletResponse();
        assertThat(authorization.preHandle(new MockHttpServletRequest("GET", "/api/v1/nginx/management/status"),
                anonymous, method)).isFalse();
        assertThat(anonymous.getStatus()).isEqualTo(401);

        MockHttpServletResponse denied = new MockHttpServletResponse();
        assertThat(authorization.preHandle(request("GET", "/status", List.of("record:read")), denied, method)).isFalse();
        assertThat(denied.getStatus()).isEqualTo(403);
        assertThat(authorization.preHandle(request("GET", "/status", List.of("system:read")),
                new MockHttpServletResponse(), method)).isTrue();
        verifyNoInteractions(service);
    }

    @Test
    void configTestRequiresSystemManageNotJustSystemRead() throws Exception {
        HandlerMethod method = new HandlerMethod(controller,
                NginxManagementController.class.getMethod("testConfig"));
        MockHttpServletResponse denied = new MockHttpServletResponse();
        assertThat(authorization.preHandle(request("POST", "/config/test", List.of("system:read")),
                denied, method)).isFalse();
        assertThat(denied.getStatus()).isEqualTo(403);
        assertThat(authorization.preHandle(request("POST", "/config/test", List.of("system:manage")),
                new MockHttpServletResponse(), method)).isTrue();
        verifyNoInteractions(service);
    }

    @Test
    void nginxLogsNeedBothSystemAndLogPermission() throws Exception {
        HandlerMethod method = new HandlerMethod(controller,
                NginxManagementController.class.getMethod("logs", String.class, int.class));
        for (List<String> insufficient : List.of(List.of("system:read"), List.of("log:read"))) {
            MockHttpServletResponse denied = new MockHttpServletResponse();
            assertThat(authorization.preHandle(request("GET", "/logs", insufficient), denied, method)).isFalse();
            assertThat(denied.getStatus()).isEqualTo(403);
        }
        assertThat(authorization.preHandle(request("GET", "/logs", List.of("system:read", "log:read")),
                new MockHttpServletResponse(), method)).isTrue();
        verifyNoInteractions(service);
    }

    private MockHttpServletRequest request(String httpMethod, String suffix, List<String> permissions) {
        MockHttpServletRequest request = new MockHttpServletRequest(httpMethod, "/api/v1/nginx/management" + suffix);
        AuthSession session = new AuthSession();
        session.setRoleCode("CUSTOM");
        session.setPermissions(permissions);
        request.setAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE, session);
        return request;
    }
}
