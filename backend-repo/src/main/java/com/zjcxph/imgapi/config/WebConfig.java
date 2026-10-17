package com.zjcxph.imgapi.config;

import com.zjcxph.imgapi.interceptors.AuthorizationInterceptor;
import com.zjcxph.imgapi.interceptors.DocumentationAccessInterceptor;
import com.zjcxph.imgapi.interceptors.DocumentationSessionCleanupInterceptor;
import com.zjcxph.imgapi.interceptors.LogInterceptor;
import com.zjcxph.imgapi.interceptors.LoginInterceptor;
import com.zjcxph.imgapi.interceptors.PasswordChangeRequiredInterceptor;
import com.zjcxph.imgapi.interceptors.RateLimitInterceptor;
import com.zjcxph.imgapi.interceptors.ReadOnlyDegradationInterceptor;
import com.zjcxph.imgapi.security.ApiAccessPolicy;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 由后端直接提供的文档路径，必须经过文档访问会话校验。
     * <p>
     * 注意：{@code /docs/**} 在正式部署中由 Nginx 直接读取静态目录，不经过后端，
     * 因此本拦截器无法覆盖，需要时应在 Nginx 层另行限制。
     * </p>
     */
    private static final String[] BACKEND_DOCUMENTATION_PATHS = {
            "/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/**",
            "/swagger-ui.html", "/swagger-ui/**"
    };

    private final LoginInterceptor loginInterceptor;
    private final PasswordChangeRequiredInterceptor passwordChangeRequiredInterceptor;
    private final AuthorizationInterceptor authorizationInterceptor;
    private final DocumentationAccessInterceptor documentationAccessInterceptor;
    private final DocumentationSessionCleanupInterceptor documentationSessionCleanupInterceptor;
    private final LogInterceptor logInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;
    private final ReadOnlyDegradationInterceptor readOnlyDegradationInterceptor;

    public WebConfig(LoginInterceptor loginInterceptor,
                     PasswordChangeRequiredInterceptor passwordChangeRequiredInterceptor,
                     AuthorizationInterceptor authorizationInterceptor,
                     DocumentationAccessInterceptor documentationAccessInterceptor,
                     DocumentationSessionCleanupInterceptor documentationSessionCleanupInterceptor,
                     LogInterceptor logInterceptor,
                     RateLimitInterceptor rateLimitInterceptor,
                     ReadOnlyDegradationInterceptor readOnlyDegradationInterceptor) {
        this.loginInterceptor = loginInterceptor;
        this.passwordChangeRequiredInterceptor = passwordChangeRequiredInterceptor;
        this.authorizationInterceptor = authorizationInterceptor;
        this.documentationAccessInterceptor = documentationAccessInterceptor;
        this.documentationSessionCleanupInterceptor = documentationSessionCleanupInterceptor;
        this.logInterceptor = logInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.readOnlyDegradationInterceptor = readOnlyDegradationInterceptor;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new SpaPathResourceResolver());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        String[] staticExcludes = {
                "/assets/**", "/browser_upgrade/**", "/favicon.*", "/*.html", "/*.br", "/*.gz"
        };
        String[] baseExcludes = ApiAccessPolicy.generalExcludes();
        String[] authenticationExcludes = ApiAccessPolicy.authenticationExcludes();

        registry.addInterceptor(documentationSessionCleanupInterceptor)
                .addPathPatterns("/api/v1/auth/logout");

        // 文档访问会话校验。这些路径不属于 /api/**，因此不会被 LoginInterceptor 与
        // AuthorizationInterceptor 覆盖，必须在此单独注册，否则 OpenAPI 规格与
        // Swagger UI 将对未登录访问者完全公开。
        registry.addInterceptor(documentationAccessInterceptor)
                .addPathPatterns(BACKEND_DOCUMENTATION_PATHS);

        registry.addInterceptor(logInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(baseExcludes)
                .excludePathPatterns(staticExcludes);

        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(baseExcludes)
                .excludePathPatterns(staticExcludes);

        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(baseExcludes)
                .excludePathPatterns(authenticationExcludes);

        registry.addInterceptor(passwordChangeRequiredInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(baseExcludes)
                .excludePathPatterns(authenticationExcludes);

        registry.addInterceptor(authorizationInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(baseExcludes)
                .excludePathPatterns(authenticationExcludes);

        registry.addInterceptor(readOnlyDegradationInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(baseExcludes);
    }
}
