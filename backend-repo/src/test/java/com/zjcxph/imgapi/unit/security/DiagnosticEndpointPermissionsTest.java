package com.zjcxph.imgapi.unit.security;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.config.ImageDeliveryProperties;
import com.zjcxph.imgapi.controller.OperationsDiagnosticsController;
import com.zjcxph.imgapi.controller.ArchiveManifestController;
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
    @Test
    void nginxCheckRequiresManageWithExistingReadInheritance() throws Exception {
        var service = mock(NginxConfigurationDiagnostics.class);
        var controller = new com.zjcxph.imgapi.controller.NginxConfigurationDiagnosticsController(service);
        var handler = new HandlerMethod(controller,controller.getClass().getMethod("check",jakarta.servlet.http.HttpServletResponse.class));
        var authorization = new AuthorizationInterceptor();
        String path = "/api/v1/operations/nginx/check";
        for (var permissions : List.of(List.<String>of(),List.of("system:read"),List.of("log:read"))) {
            var response = new MockHttpServletResponse();
            assertThat(authorization.preHandle(request("POST",path,permissions),response,handler)).isFalse();
            assertThat(response.getStatus()).isEqualTo(403);
        }
        // Existing PermissionResolver hierarchy: system:manage includes system:read.
        assertThat(authorization.preHandle(request("POST",path,List.of("system:manage")),
                new MockHttpServletResponse(),handler)).isTrue();
        assertThat(authorization.preHandle(request("POST",path,List.of("system:read","system:manage")),
                new MockHttpServletResponse(),handler)).isTrue();
        verifyNoInteractions(service);
    }

    @Test
    void operationsHistoryRequiresReadPermissionAndNoStore() throws Exception {
        var service = mock(OperationsHistoryService.class);
        var controller = new com.zjcxph.imgapi.controller.OperationsHistoryController(service);
        var handler = new HandlerMethod(controller, controller.getClass().getMethod("history",int.class,jakarta.servlet.http.HttpServletResponse.class));
        var auth = new AuthorizationInterceptor();
        var path = "/api/v1/operations/history";
        var anonymous = new MockHttpServletResponse();
        assertThat(auth.preHandle(new MockHttpServletRequest("GET",path),anonymous,handler)).isFalse();
        assertThat(anonymous.getStatus()).isEqualTo(401);
        var forbidden = new MockHttpServletResponse();
        assertThat(auth.preHandle(request("GET",path,List.of("record:read")),forbidden,handler)).isFalse();
        assertThat(forbidden.getStatus()).isEqualTo(403);
        assertThat(auth.preHandle(request("GET",path,List.of("system:read")),new MockHttpServletResponse(),handler)).isTrue();
        verifyNoInteractions(service);
        var response = new MockHttpServletResponse();
        controller.history(24,response);
        verify(service).history(24);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("private, no-store");
    }

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
                mock(MaintenanceModeService.class), new ImageDeliveryProperties());
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
        var properties = new ImageDeliveryProperties();
        var controller = new OperationsDiagnosticsController(mock(OperationsDiagnosticsService.class),
                service, mock(ImageSourceDiagnosticsService.class), mock(IntegrityDiagnosticsService.class),
                mock(MaintenanceModeService.class), properties);
        assertThat(controller.overview().getData()).containsEntry("imageDeliveryMode", "direct");
        properties.setMode(ImageDeliveryProperties.Mode.GATEWAY);
        assertThat(controller.overview().getData()).containsEntry("imageDeliveryMode", "gateway")
                .doesNotContainKeys("sourceUrl", "secret", "cookie");
    }

    @Test
    void contentVerificationRequiresBothPermissionsAndNoStore() throws Exception {
        var service = mock(ImageSourceDiagnosticsService.class);
        var controller = new OperationsDiagnosticsController(mock(OperationsDiagnosticsService.class),
                mock(OperationsCenterService.class), service, mock(IntegrityDiagnosticsService.class),
                mock(MaintenanceModeService.class), new ImageDeliveryProperties());
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(new AuthorizationInterceptor()).build();
        String path = "/api/v1/operations/image-content/verify";
        for (var permissions : List.of(List.of("system:manage"), List.of("record:read"))) {
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{\"imageId\":1}")
                    .requestAttr(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE,
                        request(permissions).getAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE)))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
        when(service.verifyContent(1)).thenReturn(java.util.Map.of("status", "VERIFIED"));
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{\"imageId\":1}")
                .requestAttr(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE,
                    request(List.of("system:manage","record:read")).getAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.data.status").value("VERIFIED"));
    }

    @Test
    void persistentCircuitHistoryRequiresSystemReadAndNoStore() throws Exception {
        var service=mock(StorageCircuitHistoryService.class);
        var controller=new com.zjcxph.imgapi.controller.StorageCircuitHistoryController(service);
        var handler=new HandlerMethod(controller,controller.getClass().getMethod("history",int.class,jakarta.servlet.http.HttpServletResponse.class));
        var auth=new AuthorizationInterceptor();
        String path="/api/v1/operations/storage-circuit-history";
        var anonymous=new MockHttpServletResponse();
        assertThat(auth.preHandle(new MockHttpServletRequest("GET",path),anonymous,handler)).isFalse();
        assertThat(anonymous.getStatus()).isEqualTo(401);
        var forbidden=new MockHttpServletResponse();
        assertThat(auth.preHandle(request("GET",path,List.of("record:read")),forbidden,handler)).isFalse();
        assertThat(forbidden.getStatus()).isEqualTo(403);
        assertThat(auth.preHandle(request("GET",path,List.of("system:read")),new MockHttpServletResponse(),handler)).isTrue();
        verifyNoInteractions(service);
        var response=new MockHttpServletResponse();
        controller.history(24,response);
        verify(service).history(24);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("private, no-store");
    }

    @Test
    void qualityCaseReviewRequiresBothManagementPermissions() throws Exception {
        var service=mock(QualityCaseReviewService.class);
        var controller=new com.zjcxph.imgapi.controller.QualityCaseReviewController(service);
        var handler=new HandlerMethod(controller,controller.getClass().getMethod("decide",long.class,int.class,String.class,jakarta.servlet.http.HttpServletResponse.class));
        var auth=new AuthorizationInterceptor();
        String path="/api/v1/system/data-quality/case-reviews/issue/7";
        for(var permissions:List.of(List.<String>of(),List.of("system:manage"),List.of("record:manage"))) {
            var response=new MockHttpServletResponse();
            assertThat(auth.preHandle(request("POST",path,permissions),response,handler)).isFalse();
            assertThat(response.getStatus()).isEqualTo(403);
        }
        assertThat(auth.preHandle(request("POST",path,List.of("system:manage","record:manage")),new MockHttpServletResponse(),handler)).isTrue();
        verifyNoInteractions(service);
        var response=new MockHttpServletResponse();
        controller.context(7,response);
        verify(service).context(7);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("private, no-store");
    }

    @Test
    void archiveScanPageRequiresBothReadPermissionsAndNoStore() throws Exception {
        var service=mock(DataRelationService.class);
        var controller=new com.zjcxph.imgapi.controller.DataRelationController(service);
        var handler=new HandlerMethod(controller,controller.getClass().getMethod("activeScans",long.class,
                Integer.class,Integer.class,int.class,jakarta.servlet.http.HttpServletResponse.class));
        var auth=new AuthorizationInterceptor();
        String path="/api/v1/system/data-relations/archives/7/scans";
        for(var permissions:List.of(List.<String>of(),List.of("system:read"),List.of("record:read"))) {
            var response=new MockHttpServletResponse();
            assertThat(auth.preHandle(request("GET",path,permissions),response,handler)).isFalse();
            assertThat(response.getStatus()).isEqualTo(403);
        }
        assertThat(auth.preHandle(request("GET",path,List.of("system:read","record:read")),
                new MockHttpServletResponse(),handler)).isTrue();
        verifyNoInteractions(service);
        var response=new MockHttpServletResponse();
        controller.activeScans(7,null,null,50,response);
        verify(service).activeScanPage(7,null,null,50);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("private, no-store");
    }

    @Test
    void manifestUsesDualPermissionsAndTrustedActor() throws Exception {
        var service = mock(ArchiveManifestService.class);
        var controller = new ArchiveManifestController(service);
        String path = "/api/v1/operations/archive-manifests";
        var handler = new HandlerMethod(controller, ArchiveManifestController.class.getMethod(
                "start", long.class, jakarta.servlet.http.HttpServletResponse.class));
        var authorization = new AuthorizationInterceptor();
        for (var permissions : List.of(List.of("system:manage"), List.of("record:read"))) {
            var response = new MockHttpServletResponse();
            assertThat(authorization.preHandle(request("POST", path, permissions), response, handler)).isFalse();
            assertThat(response.getStatus()).isEqualTo(403);
        }
        assertThat(authorization.preHandle(request("POST", path, List.of("system:manage","record:read")),
                new MockHttpServletResponse(), handler)).isTrue();
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                controller.start(9L, new MockHttpServletResponse())))
                .isInstanceOf(com.zjcxph.imgapi.exception.BusinessException.class);
        verifyNoInteractions(service);
        var session = new AuthSession();
        session.setId(7L);
        com.zjcxph.imgapi.utils.AuthContext.setCurrentUser(session);
        try {
            var response = new MockHttpServletResponse();
            controller.start(9L, response);
            verify(service).start(9L, 7L);
            assertThat(response.getHeader("Cache-Control")).isEqualTo("private, no-store");
        } finally {
            com.zjcxph.imgapi.utils.AuthContext.clear();
        }
    }

    @Test
    void repairOrderRequiresDualManagePermissionsAndTrustedActor() throws Exception {
        var service = mock(com.zjcxph.imgapi.service.DataQualityRepairOrderService.class);
        var controller = new com.zjcxph.imgapi.controller.DataQualityRepairOrderController(service);
        var authorization = new AuthorizationInterceptor();
        String base = "/api/v1/system/data-quality/repair-orders";
        for (String action : List.of("propose","approve","apply","verify","cancel","compensate")) {
            var handler = new HandlerMethod(controller, controller.getClass().getMethod(
                    action, long.class, jakarta.servlet.http.HttpServletResponse.class));
            String path = action.equals("propose") ? base : base+"/9/"+action;
            for (var permissions : List.of(List.of("system:manage"), List.of("record:manage"),List.of("system:manage","record:read"))) {
                var response = new MockHttpServletResponse();
                assertThat(authorization.preHandle(request("POST",path,permissions),response,handler)).isFalse();
                assertThat(response.getStatus()).isEqualTo(403);
            }
            assertThat(authorization.preHandle(request("POST",path,List.of("system:manage","record:manage")),
                    new MockHttpServletResponse(),handler)).isTrue();
        }
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                controller.apply(9L,new MockHttpServletResponse())))
                .isInstanceOf(com.zjcxph.imgapi.exception.BusinessException.class);
        verifyNoInteractions(service);
        var session=new AuthSession();
        session.setId(7L);
        com.zjcxph.imgapi.utils.AuthContext.setCurrentUser(session);
        try {
            var response=new MockHttpServletResponse();
            controller.apply(9L,response);
            verify(service).apply(9L,7L);
            assertThat(response.getHeader("Cache-Control")).isEqualTo("private, no-store");
        } finally {
            com.zjcxph.imgapi.utils.AuthContext.clear();
        }
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
