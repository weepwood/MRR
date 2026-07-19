package com.zjcxph.imgapi.config;

import com.zjcxph.imgapi.interceptors.AuthorizationInterceptor;
import com.zjcxph.imgapi.interceptors.DocumentationSessionCleanupInterceptor;
import com.zjcxph.imgapi.interceptors.LogInterceptor;
import com.zjcxph.imgapi.interceptors.LoginInterceptor;
import com.zjcxph.imgapi.interceptors.PasswordChangeRequiredInterceptor;
import com.zjcxph.imgapi.interceptors.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;
    private final PasswordChangeRequiredInterceptor passwordChangeRequiredInterceptor;
    private final AuthorizationInterceptor authorizationInterceptor;
    private final DocumentationSessionCleanupInterceptor documentationSessionCleanupInterceptor;
    private final LogInterceptor logInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    public WebConfig(LoginInterceptor loginInterceptor,
                     PasswordChangeRequiredInterceptor passwordChangeRequiredInterceptor,
                     AuthorizationInterceptor authorizationInterceptor,
                     DocumentationSessionCleanupInterceptor documentationSessionCleanupInterceptor,
                     LogInterceptor logInterceptor,
                     RateLimitInterceptor rateLimitInterceptor) {
        this.loginInterceptor = loginInterceptor;
        this.passwordChangeRequiredInterceptor = passwordChangeRequiredInterceptor;
        this.authorizationInterceptor = authorizationInterceptor;
        this.documentationSessionCleanupInterceptor = documentationSessionCleanupInterceptor;
        this.logInterceptor = logInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requestedResource = location.createRelative(resourcePath);
                        if (requestedResource.exists() && requestedResource.isReadable()) {
                            return requestedResource;
                        }
                        if (!resourcePath.contains(".") && !resourcePath.startsWith("api/")) {
                            return new ClassPathResource("/static/index.html");
                        }
                        return null;
                    }
                });
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        String[] staticExcludes = {
                "/assets/**", "/browser_upgrade/**", "/favicon.*", "/*.html", "/*.br", "/*.gz"
        };
        String[] baseExcludes = {
                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs.yaml",
                "/v3/api-docs/**", "/docs/**", "/api/v1/documentation/access",
                "/api/v1/public/status/**", "/api/v1/public/config/**", "/error", "/actuator/**"
        };
        String[] authenticationExcludes = {
                "/api/v1/auth/login",
                "/api/v1/img/hello",
                "/api/v1/integration/archive/tickets",
                "/api/v1/external/archive/**"
        };

        registry.addInterceptor(documentationSessionCleanupInterceptor)
                .addPathPatterns("/api/v1/auth/logout");

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
    }
}
