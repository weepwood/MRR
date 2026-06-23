package com.zjcxph.imgapi.config;

import com.zjcxph.imgapi.interceptors.AuthorizationInterceptor;
import com.zjcxph.imgapi.interceptors.LogInterceptor;
import com.zjcxph.imgapi.interceptors.LoginInterceptor;
import com.zjcxph.imgapi.interceptors.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/docs/**",
                        "/error",
                        "/favicon.ico",
                        "/actuator/**"
                );

        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/api/v1/auth/login",
                        "/api/v1/auth/register",
                        "/api/v1/img/hello",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/docs/**",
                        "/error",
                        "/favicon.ico",
                        "/actuator/**"
                );

        registry.addInterceptor(authorizationInterceptor)
                .addPathPatterns("/**");

        registry.addInterceptor(logInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/docs/**",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/favicon.ico",
                        "/error",
                        "/actuator/**"
                );
    }
}
