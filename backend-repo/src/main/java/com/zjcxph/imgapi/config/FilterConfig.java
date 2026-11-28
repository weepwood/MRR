package com.zjcxph.imgapi.config;

import com.zjcxph.imgapi.filter.RequestCachingFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Filter 配置类
 * 确保 RequestCachingFilter 在其他 Filter 之前执行
 */
@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<RequestCachingFilter> requestCachingFilter() {
        FilterRegistrationBean<RequestCachingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RequestCachingFilter());
        registration.addUrlPatterns("/*");
        // 设置较高的优先级,确保在其他 Filter 之前执行
        registration.setOrder(1);
        registration.setName("requestCachingFilter");
        return registration;
    }
    @Bean
    public FilterRegistrationBean<com.zjcxph.imgapi.filter.ExternalArchivePrivacyFilter> externalArchivePrivacyFilter() {
        var registration = new FilterRegistrationBean<com.zjcxph.imgapi.filter.ExternalArchivePrivacyFilter>();
        registration.setFilter(new com.zjcxph.imgapi.filter.ExternalArchivePrivacyFilter());
        registration.addUrlPatterns("/api/v1/external/archive/*", "/api/v1/integration/archive/tickets",
                "/api/v1/patients", "/api/v1/patients/*", "/api/v1/oss/*");
        registration.setOrder(0);
        registration.setAsyncSupported(true);
        registration.setName("externalArchivePrivacyFilter");
        return registration;
    }
}
