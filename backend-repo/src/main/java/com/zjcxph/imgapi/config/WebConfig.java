package com.zjcxph.imgapi.config;

import com.zjcxph.imgapi.interceptors.AuthorizationInterceptor;
import com.zjcxph.imgapi.interceptors.LogInterceptor;
import com.zjcxph.imgapi.interceptors.LoginInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private LoginInterceptor loginInterceptor;

    @Autowired
    private AuthorizationInterceptor authorizationInterceptor;

    @Autowired
    private LogInterceptor logInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/login",
                        "/login/**",
                        "/v1/auth/login",
                        "/v1/auth/login/**",
                        "/v1/img-api/hello",
                        "/v1/system/**",
                        "/v1/statistics-api/**",
                        "/v1/monitoring-api/pressure-tests/**",
                        "/v2/search/**",
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
                .addPathPatterns("/**");
    }
}
