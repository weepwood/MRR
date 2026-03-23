package com.zjcxph.imgapi.config;

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
    private LogInterceptor logInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 登录拦截器
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/**")
                .excludePathPatterns("/v1/user/login")
                .excludePathPatterns("/v2/search/hello")
                .excludePathPatterns("/swagger-ui/index.html")
                .excludePathPatterns("/v2/logs/*")
        ;

        // 日志拦截器
        registry.addInterceptor(logInterceptor)
                .addPathPatterns("/**");
    }
}
