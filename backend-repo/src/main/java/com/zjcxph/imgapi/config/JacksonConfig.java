package com.zjcxph.imgapi.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the Jackson 2 mapper used by legacy integration services.
 * Spring Boot 4's web stack defaults to Jackson 3, so this mapper must be
 * declared explicitly for components that still use com.fasterxml.jackson.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
