package com.zjcxph.imgapi.config;

import com.zjcxph.imgapi.interceptors.AuthorizationInterceptor;
import com.zjcxph.imgapi.interceptors.DocumentationAccessInterceptor;
import com.zjcxph.imgapi.interceptors.DocumentationSessionCleanupInterceptor;
import com.zjcxph.imgapi.interceptors.LogInterceptor;
import com.zjcxph.imgapi.interceptors.LoginInterceptor;
import com.zjcxph.imgapi.interceptors.PasswordChangeRequiredInterceptor;
import com.zjcxph.imgapi.interceptors.RateLimitInterceptor;
import com.zjcxph.imgapi.interceptors.ReadOnlyDegradationInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 守护文档访问拦截器确实被注册的不变量。
 *
 * <p>{@link DocumentationAccessInterceptor} 曾经只标注 {@code @Component}
 * 却没有在 {@link WebConfig} 中注册，成为死代码，直接造成 OpenAPI 规格与
 * Swagger UI 未鉴权公开（安全审计 H1）。单纯的类存在性测试无法发现这类缺陷，
 * 因此这里直接断言 {@code addInterceptors} 的实际注册结果。</p>
 */
class WebConfigDocumentationAccessRegistrationTest {

    private static final List<String> EXPECTED_DOCUMENTATION_PATTERNS = List.of(
            "/v3/api-docs",
            "/v3/api-docs.yaml",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**"
    );

    @Test
    void registersDocumentationAccessInterceptorWithNarrowPathPatterns() throws Exception {
        DocumentationAccessInterceptor interceptor = mock(DocumentationAccessInterceptor.class);
        InterceptorRegistry registry = new InterceptorRegistry();

        newWebConfig(interceptor).addInterceptors(registry);

        List<String> includePatterns = includePatternsOf(registry, interceptor);

        assertThat(includePatterns)
                .as("文档访问拦截器必须被注册，且路径必须显式限定在文档路径上")
                .containsExactlyElementsOf(EXPECTED_DOCUMENTATION_PATTERNS);
    }

    @Test
    void doesNotRequireDocumentationSessionForBusinessApi() throws Exception {
        DocumentationAccessInterceptor interceptor = mock(DocumentationAccessInterceptor.class);
        InterceptorRegistry registry = new InterceptorRegistry();

        newWebConfig(interceptor).addInterceptors(registry);

        assertThat(includePatternsOf(registry, interceptor))
                .as("不得把文档访问会话扩大到业务接口或全站路径，否则所有请求都会要求文档 Cookie")
                .noneMatch(pattern -> pattern.equals("/**") || pattern.startsWith("/api/"));
    }

    private WebConfig newWebConfig(DocumentationAccessInterceptor documentationAccessInterceptor) {
        return new WebConfig(
                mock(LoginInterceptor.class),
                mock(PasswordChangeRequiredInterceptor.class),
                mock(AuthorizationInterceptor.class),
                documentationAccessInterceptor,
                mock(DocumentationSessionCleanupInterceptor.class),
                mock(LogInterceptor.class),
                mock(RateLimitInterceptor.class),
                mock(ReadOnlyDegradationInterceptor.class)
        );
    }

    private List<String> includePatternsOf(InterceptorRegistry registry, HandlerInterceptor target)
            throws ReflectiveOperationException {
        Field registrationsField = InterceptorRegistry.class.getDeclaredField("registrations");
        registrationsField.setAccessible(true);
        List<?> registrations = (List<?>) registrationsField.get(registry);

        Field interceptorField = InterceptorRegistration.class.getDeclaredField("interceptor");
        interceptorField.setAccessible(true);
        Field includePatternsField = InterceptorRegistration.class.getDeclaredField("includePatterns");
        includePatternsField.setAccessible(true);

        for (Object registration : registrations) {
            if (interceptorField.get(registration) != target) {
                continue;
            }
            Object patterns = includePatternsField.get(registration);
            if (patterns == null) {
                // 未声明 includePatterns 时拦截器会匹配全部请求，属于严重过度注册。
                return List.of("/**");
            }
            return ((List<?>) patterns).stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
