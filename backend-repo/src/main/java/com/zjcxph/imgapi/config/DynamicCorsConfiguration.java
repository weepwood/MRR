package com.zjcxph.imgapi.config;

import com.zjcxph.imgapi.service.DeveloperModeService;
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
 * <p>正常模式只允许配置中的精确 Origin；开发者模式恢复旧版任意 Origin 调试能力。
 * 配置源按请求读取，因此切换系统设置后无需重启。</p>
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
            "Content-Disposition",
            "X-MRR-Developer-Mode"
    );

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public CorsFilter mrrDynamicCorsFilter(
            CorsProperties corsProperties,
            DeveloperModeService developerModeService
    ) {
        CorsConfigurationSource source = request -> {
            if (!request.getRequestURI().startsWith("/api/")) {
                return null;
            }
            return createConfiguration(corsProperties, developerModeService);
        };
        return new CorsFilter(source);
    }

    private CorsConfiguration createConfiguration(CorsProperties corsProperties,
                                                  DeveloperModeService developerModeService) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedMethods(ALLOWED_METHODS);
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(EXPOSED_HEADERS);
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        if (developerModeService.isEnabled()) {
            configuration.setAllowedOriginPatterns(List.of("*"));
            return configuration;
        }

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
