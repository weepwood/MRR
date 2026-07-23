package com.zjcxph.imgapi.unit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.annotation.AuthenticatedOnly;
import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.entity.AuthRole;
import com.zjcxph.imgapi.mapper.AuthRoleMapper;
import com.zjcxph.imgapi.service.DeploymentReadinessService;
import com.zjcxph.imgapi.service.OperationsDiagnosticsService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationsDiagnosticsServiceTest {

    @Test
    void permissionMatrixUsesTheSameMethodFirstPrecedenceAsAuthorizationInterceptor() throws Exception {
        RequestMappingHandlerMapping handlerMapping = mock(RequestMappingHandlerMapping.class);
        Map<RequestMappingInfo, HandlerMethod> handlers = new LinkedHashMap<>();
        handlers.put(
                mapping("/api/v1/login-only", RequestMethod.GET),
                handler(new ClassPermissionController(), "loginOnly")
        );
        handlers.put(
                mapping("/api/v1/record-only", RequestMethod.GET),
                handler(new ClassAuthenticatedController(), "recordOnly")
        );
        when(handlerMapping.getHandlerMethods()).thenReturn(handlers);

        OperationsDiagnosticsService service = service(handlerMapping);
        Map<String, Object> matrix = service.permissionMatrix(false);

        Map<String, Object> loginOnly = endpoint(matrix, "GET /api/v1/login-only");
        assertEquals("AUTHENTICATED_ONLY", loginOnly.get("policy"));
        assertEquals(List.of(), loginOnly.get("requiredPermissions"));

        Map<String, Object> recordOnly = endpoint(matrix, "GET /api/v1/record-only");
        assertEquals("PERMISSION", recordOnly.get("policy"));
        assertEquals(List.of("record:read"), recordOnly.get("requiredPermissions"));
    }

    @Test
    void permissionMatrixMarksFormatDependentExportPermissionsAsConditionalAny() throws Exception {
        RequestMappingHandlerMapping handlerMapping = mock(RequestMappingHandlerMapping.class);
        when(handlerMapping.getHandlerMethods()).thenReturn(Map.of(
                mapping("/api/v1/archive-exports/jobs", RequestMethod.POST),
                handler(new ClassPermissionController(), "loginOnly")
        ));

        OperationsDiagnosticsService service = service(handlerMapping);
        Map<String, Object> matrix = service.permissionMatrix(false);
        Map<String, Object> endpoint = endpoint(matrix, "POST /api/v1/archive-exports/jobs");

        assertEquals("CONDITIONAL_PERMISSION", endpoint.get("policy"));
        assertEquals("ANY", endpoint.get("permissionMode"));
        assertEquals(
                List.of("record:download", "record:pdf:export"),
                endpoint.get("requiredPermissions")
        );
    }

    private OperationsDiagnosticsService service(RequestMappingHandlerMapping handlerMapping) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AuthRoleMapper authRoleMapper = mock(AuthRoleMapper.class);
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.anyString())).thenReturn(List.of());
        when(authRoleMapper.findAll()).thenReturn(List.of(role()));
        return new OperationsDiagnosticsService(
                jdbcTemplate,
                authRoleMapper,
                handlerMapping,
                new ObjectMapper(),
                mock(DeploymentReadinessService.class)
        );
    }

    private AuthRole role() {
        AuthRole role = new AuthRole();
        role.setCode("OPERATOR");
        role.setName("操作员");
        role.setPermissions("record:download");
        return role;
    }

    private RequestMappingInfo mapping(String path, RequestMethod method) {
        return RequestMappingInfo.paths(path).methods(method).build();
    }

    private HandlerMethod handler(Object controller, String methodName) throws NoSuchMethodException {
        Method method = controller.getClass().getDeclaredMethod(methodName);
        return new HandlerMethod(controller, method);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> endpoint(Map<String, Object> matrix, String key) {
        List<Map<String, Object>> endpoints = (List<Map<String, Object>>) matrix.get("endpoints");
        return endpoints.stream()
                .filter(item -> key.equals(item.get("key")))
                .findFirst()
                .orElseThrow();
    }

    @RequirePermissions({"system:read"})
    private static class ClassPermissionController {

        @AuthenticatedOnly
        public void loginOnly() {
        }
    }

    @AuthenticatedOnly
    private static class ClassAuthenticatedController {

        @RequirePermissions({"record:read"})
        public void recordOnly() {
        }
    }
}
