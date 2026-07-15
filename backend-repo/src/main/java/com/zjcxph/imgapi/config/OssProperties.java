package com.zjcxph.imgapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "oss")
public class OssProperties {

    private String endpoint;
    private String bucket;
    private String accessKeyId;
    private String accessKeySecret;
    private String region;
    private String baseUrl;
    private int urlExpireSeconds = 3600;
    private int maxConnections = 64;
    private int connectionTimeoutMs = 30000;
    private int socketTimeoutMs = 60000;
    private int maxErrorRetry = 5;
}
