package com.zjcxph.imgapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "archive.image-source")
public class ArchiveImageSourceProperties {

    private boolean preferOss = true;
    private Duration acquireTimeout = Duration.ofSeconds(30);
    private int localMaxConcurrency = 16;
    private int nasMaxConcurrency = 8;
    private int httpMaxConcurrency = 8;
    private int ossMaxConcurrency = 8;
    private Map<String, NasNode> nasNodes = new LinkedHashMap<>();
    private Map<String, HttpNode> httpNodes = new LinkedHashMap<>();

    @Data
    public static class NasNode {
        private String root;
    }

    @Data
    public static class HttpNode {
        private String baseUrl;
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(60);
        private String authHeader = "Authorization";
        private String authValue;
    }
}
