package com.zjcxph.imgapi.config;

import com.zjcxph.imgapi.interceptors.AuthorizationInterceptor;
import com.zjcxph.imgapi.interceptors.LogInterceptor;
import com.zjcxph.imgapi.interceptors.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;
    private final AuthorizationInterceptor authorizationInterceptor;
    private final LogInterceptor logInterceptor;

    public WebConfig(LoginInterceptor loginInterceptor,
                     AuthorizationInterceptor authorizationInterceptor,
                     LogInterceptor logInterceptor) {
        this.loginInterceptor = loginInterceptor;
        this.authorizationInterceptor = authorizationInterceptor;
        this.logInterceptor = logInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/api/v1/auth/login",
                        "/api/v1/img/hello",
                        "/api/v1/system/**",
                        "/api/v1/statistics/**",
                        "/api/v1/monitoring/pressure-tests/**",
                        "/api/v1/search/**",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/docs/**",
                        "/error",
                        "/favicon.ico",
                        "/actuator/health",
                        "/actuator/info"
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
