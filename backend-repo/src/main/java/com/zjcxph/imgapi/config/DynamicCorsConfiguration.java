package com.zjcxph.imgapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * API 跨域策略。
 *
 * <p>只允许配置中的精确 Origin。运行时系统设置不得放宽跨域边界，
 * 开发和联调环境应通过独立配置文件显式声明允许的 Origin。</p>
 */
@Configuration
public class DynamicCorsConfiguration {

    private static final List<String> ALLOWED_METHODS = List.of(
            "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
    );
    private static final List<String> EXPOSED_HEADERS = List.of(
            "X-Request-Id",
            "X-Endpoint-Template",
            "Server-Timing",
            "Content-Disposition"
    );

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public CorsFilter mrrDynamicCorsFilter(CorsProperties corsProperties) {
        CorsConfigurationSource source = request -> {
            if (!request.getRequestURI().startsWith("/api/")) {
                return null;
            }
            return createConfiguration(corsProperties);
        };
        return new CorsFilter(source);
    }

    private CorsConfiguration createConfiguration(CorsProperties corsProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedMethods(ALLOWED_METHODS);
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(EXPOSED_HEADERS);
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        List<String> allowedOrigins = corsProperties.getAllowedOrigins() == null
                ? List.of()
                : corsProperties.getAllowedOrigins().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(origin -> !"*".equals(origin))
                .distinct()
                .toList();
        configuration.setAllowedOrigins(allowedOrigins);
        return configuration;
    }
}
