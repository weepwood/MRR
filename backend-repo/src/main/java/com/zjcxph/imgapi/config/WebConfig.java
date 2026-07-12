package com.zjcxph.imgapi.config;

import com.zjcxph.imgapi.interceptors.AuthorizationInterceptor;
import com.zjcxph.imgapi.interceptors.LogInterceptor;
import com.zjcxph.imgapi.interceptors.LoginInterceptor;
import com.zjcxph.imgapi.interceptors.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;
    private final AuthorizationInterceptor authorizationInterceptor;
    private final LogInterceptor logInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    public WebConfig(LoginInterceptor loginInterceptor,
                     AuthorizationInterceptor authorizationInterceptor,
                     LogInterceptor logInterceptor,
                     RateLimitInterceptor rateLimitInterceptor) {
        this.loginInterceptor = loginInterceptor;
        this.authorizationInterceptor = authorizationInterceptor;
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
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .exposedHeaders("X-Request-Id", "X-Endpoint-Template", "Server-Timing")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        String[] staticExcludes = {
                "/assets/**",
                "/browser_upgrade/**",
                "/favicon.*",
                "/*.html",
                "/*.br",
                "/*.gz"
        };

        String[] baseExcludes = {
                "/swagger-ui/**",
                "/v3/api-docs/**",
                "/docs/**",
                "/error",
                "/actuator/**"
        };

        registry.addInterceptor(logInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(baseExcludes)
                .excludePathPatterns(staticExcludes);

        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(baseExcludes)
                .excludePathPatterns(staticExcludes);

        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(baseExcludes)
                .excludePathPatterns(staticExcludes)
                .excludePathPatterns(
                        "/api/v1/auth/login",
                        "/api/v1/auth/register",
                        "/api/v1/img/hello"
                );

        registry.addInterceptor(authorizationInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(staticExcludes);

    }
}
