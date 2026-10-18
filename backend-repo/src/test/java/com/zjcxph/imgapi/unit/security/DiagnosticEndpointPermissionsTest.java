package com.zjcxph.imgapi.unit.security;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.controller.QueryDiagnosticsController;
import com.zjcxph.imgapi.controller.RuntimeLogController;
import com.zjcxph.imgapi.interceptors.AuthorizationInterceptor;
import com.zjcxph.imgapi.interceptors.LoginInterceptor;
import com.zjcxph.imgapi.mapper.AuthUserMapper;
import com.zjcxph.imgapi.security.TokenBlacklist;
import com.zjcxph.imgapi.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

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
        var controller = new QueryDiagnosticsController(service);
        var handler = new HandlerMethod(controller, QueryDiagnosticsController.class.getMethod("diagnose",QueryDiagnosticsController.Query.class));
        for (var permissions : List.of(List.of("system:manage"),List.of("record:read"))) {
            var response = new MockHttpServletResponse();
            assertThat(new AuthorizationInterceptor().preHandle(request(permissions),response,handler)).isFalse();
            assertThat(response.getStatus()).isEqualTo(403);
        }
        assertThat(new AuthorizationInterceptor().preHandle(request(List.of("system:manage","record:read")),new MockHttpServletResponse(),handler)).isTrue();
        verifyNoInteractions(service);
    }

    @Test void importingLogsNeedsIndependentImportPermission() throws Exception {
        var journal=mock(RuntimeLogJournalService.class);
        var controller=new RuntimeLogController(journal,mock(RuntimeLogQueryService.class));
        var handler=new HandlerMethod(controller,RuntimeLogController.class.getMethod("importFile",RuntimeLogController.ImportRequest.class));
        var response=new MockHttpServletResponse();
        assertThat(new AuthorizationInterceptor().preHandle(request(List.of("system:manage","log:read")),response,handler)).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(new AuthorizationInterceptor().preHandle(request(List.of("system:manage","log:read","system:log:import")),new MockHttpServletResponse(),handler)).isTrue();
        verifyNoInteractions(journal);
    }

    private MockHttpServletRequest request(List<String> permissions) {
        var request=new MockHttpServletRequest("POST","/api/v1/operations/query-diagnostics");
        var session=new AuthSession();session.setRoleCode("CUSTOM");session.setPermissions(permissions);
        request.setAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE,session);
        return request;
    }
}
