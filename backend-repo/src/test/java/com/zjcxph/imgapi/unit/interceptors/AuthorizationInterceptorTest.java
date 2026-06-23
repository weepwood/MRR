package com.zjcxph.imgapi.unit.interceptors;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.interceptors.AuthorizationInterceptor;
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
import static org.mockito.Mockito.when;

@DisplayName("AuthorizationInterceptor 权限拦截器测试")
class AuthorizationInterceptorTest {

    private AuthorizationInterceptor interceptor;
    private HttpServletRequest request;
    private HttpServletResponse response;

    @BeforeEach
    void setUp() throws Exception {
        interceptor = new AuthorizationInterceptor();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));
    }

    private AuthSession session(String roleCode, List<String> perms) {
        AuthSession s = new AuthSession();
        s.setRoleCode(roleCode);
        s.setPermissions(perms);
        return s;
    }

    private HandlerMethod handlerWithPerms(String[] perms) throws NoSuchMethodException {
        return new HandlerMethod(new TestController(), TestController.class.getMethod("guarded"));
    }

    private HandlerMethod handlerNoPerms() throws NoSuchMethodException {
        return new HandlerMethod(new TestController(), TestController.class.getMethod("open"));
    }

    @Test
    @DisplayName("非 HandlerMethod 直接放行")
    void preHandle_nonHandlerMethod() throws Exception {
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    @DisplayName("无 @RequirePermissions 注解的端点放行")
    void preHandle_noAnnotation() throws Exception {
        assertThat(interceptor.preHandle(request, response, handlerNoPerms())).isTrue();
    }

    @Test
    @DisplayName("ADMIN 角色绕过权限检查")
    void preHandle_adminBypass() throws Exception {
        when(request.getAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE))
                .thenReturn(session("ADMIN", List.of()));
        assertThat(interceptor.preHandle(request, response, handlerWithPerms(new String[]{"statistics:read"}))).isTrue();
    }

    @Test
    @DisplayName("拥有所需权限的非 ADMIN 用户放行")
    void preHandle_hasPermission() throws Exception {
        when(request.getAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE))
                .thenReturn(session("DOCTOR", List.of("record:manage")));
        assertThat(interceptor.preHandle(request, response, handlerWithPerms(new String[]{"record:read"}))).isTrue();
    }

    @Test
    @DisplayName("缺少所需权限的非 ADMIN 用户被拒绝（403）")
    void preHandle_noPermission() throws Exception {
        when(request.getAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE))
                .thenReturn(session("NURSE", List.of("log:read")));
        assertThat(interceptor.preHandle(request, response, handlerWithPerms(new String[]{"record:read"}))).isFalse();
        org.mockito.Mockito.verify(response).setStatus(403);
    }

    @Test
    @DisplayName("无 session（未登录）返回 401")
    void preHandle_noSession() throws Exception {
        when(request.getAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE)).thenReturn(null);
        assertThat(interceptor.preHandle(request, response, handlerWithPerms(new String[]{"record:read"}))).isFalse();
        org.mockito.Mockito.verify(response).setStatus(401);
    }

    @Test
    @DisplayName("层级继承：拥有 record:manage 通过 record:edit 检查")
    void preHandle_hierarchyInheritance() throws Exception {
        when(request.getAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE))
                .thenReturn(session("DOCTOR", List.of("record:manage")));
        assertThat(interceptor.preHandle(request, response, handlerWithPerms(new String[]{"record:edit"}))).isTrue();
    }

    @org.springframework.stereotype.Component
    static class TestController {
        @org.springframework.web.bind.annotation.GetMapping("/open")
        public void open() {}

        @org.springframework.web.bind.annotation.GetMapping("/guarded")
        @RequirePermissions({"record:read"})
        public void guarded() {}
    }
}
