package com.zjcxph.imgapi.unit.interceptors;

import com.zjcxph.imgapi.annotation.AuthenticatedOnly;
import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.interceptors.AuthorizationInterceptor;
import com.zjcxph.imgapi.service.DeveloperApiAccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AuthorizationInterceptor 权限拦截器测试")
class AuthorizationInterceptorTest {

    private AuthorizationInterceptor interceptor;
    private DeveloperApiAccessService developerApiAccessService;
    private HttpServletRequest request;
    private HttpServletResponse response;

    @BeforeEach
    void setUp() throws Exception {
        developerApiAccessService = mock(DeveloperApiAccessService.class);
        interceptor = new AuthorizationInterceptor(developerApiAccessService);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getContextPath()).thenReturn("");
    }

    private AuthSession session(String roleCode, List<String> permissions) {
        AuthSession session = new AuthSession();
        session.setUsername("test-user");
        session.setRoleCode(roleCode);
        session.setPermissions(permissions);
        return session;
    }

    private HandlerMethod handler(String methodName) throws NoSuchMethodException {
        return new HandlerMethod(new TestController(), TestController.class.getMethod(methodName));
    }

    @Test
    @DisplayName("非 HandlerMethod 直接放行")
    void preHandle_nonHandlerMethod() throws Exception {
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    @DisplayName("无访问策略声明的端点默认拒绝")
    void preHandle_noAnnotation() throws Exception {
        assertThat(interceptor.preHandle(request, response, handler("open"))).isFalse();
        verify(response).setStatus(403);
        verify(developerApiAccessService, never()).isPermissionBypassAllowed(request);
    }

    @Test
    @DisplayName("公开注册路径直接放行")
    void preHandle_publicRegistration() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/auth/register");

        assertThat(interceptor.preHandle(request, response, handler("open"))).isTrue();
    }

    @Test
    @DisplayName("仅登录端点拥有会话即可访问")
    void preHandle_authenticatedOnly() throws Exception {
        when(request.getAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE))
                .thenReturn(session("DOCTOR", List.of()));

        assertThat(interceptor.preHandle(request, response, handler("authenticated"))).isTrue();
    }

    @Test
    @DisplayName("ADMIN 角色绕过业务权限检查")
    void preHandle_adminBypass() throws Exception {
        when(request.getAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE))
                .thenReturn(session("ADMIN", List.of()));

        assertThat(interceptor.preHandle(request, response, handler("guarded"))).isTrue();
    }

    @Test
    @DisplayName("拥有所需权限的非 ADMIN 用户放行")
    void preHandle_hasPermission() throws Exception {
        when(request.getAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE))
                .thenReturn(session("DOCTOR", List.of("record:manage")));

        assertThat(interceptor.preHandle(request, response, handler("guarded"))).isTrue();
    }

    @Test
    @DisplayName("已登录真实用户可以使用开发者权限旁路")
    void preHandle_authenticatedDeveloperBypass() throws Exception {
        AuthSession user = session("NURSE", List.of("log:read"));
        when(request.getAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE)).thenReturn(user);
        when(developerApiAccessService.isPermissionBypassAllowed(request)).thenReturn(true);

        assertThat(interceptor.preHandle(request, response, handler("guarded"))).isTrue();
        verify(response).setHeader("X-MRR-Developer-Mode", "enabled");
        verify(response).setHeader("X-MRR-Access-Mode", "api-permission-bypass");
    }

    @Test
    @DisplayName("开发者权限旁路不能代替登录认证")
    void preHandle_developerBypassWithoutSession() throws Exception {
        when(request.getAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE)).thenReturn(null);
        when(developerApiAccessService.isPermissionBypassAllowed(request)).thenReturn(true);

        assertThat(interceptor.preHandle(request, response, handler("guarded"))).isFalse();
        verify(response).setStatus(401);
        verify(developerApiAccessService, never()).isPermissionBypassAllowed(request);
    }

    @Test
    @DisplayName("关闭权限旁路后缺少权限的用户被拒绝")
    void preHandle_noPermission() throws Exception {
        when(request.getAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE))
                .thenReturn(session("NURSE", List.of("log:read")));
        when(developerApiAccessService.isPermissionBypassAllowed(request)).thenReturn(false);

        assertThat(interceptor.preHandle(request, response, handler("guarded"))).isFalse();
        verify(response).setStatus(403);
    }

    @Test
    @DisplayName("层级继承：拥有 record:manage 通过 record:edit 检查")
    void preHandle_hierarchyInheritance() throws Exception {
        when(request.getAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE))
                .thenReturn(session("DOCTOR", List.of("record:manage")));

        assertThat(interceptor.preHandle(request, response, handler("editable"))).isTrue();
    }

    @org.springframework.stereotype.Component
    static class TestController {
        @org.springframework.web.bind.annotation.GetMapping("/open")
        public void open() {
        }

        @org.springframework.web.bind.annotation.GetMapping("/authenticated")
        @AuthenticatedOnly
        public void authenticated() {
        }

        @org.springframework.web.bind.annotation.GetMapping("/guarded")
        @RequirePermissions({"record:read"})
        public void guarded() {
        }

        @org.springframework.web.bind.annotation.PutMapping("/editable")
        @RequirePermissions({"record:edit"})
        public void editable() {
        }
    }
}
