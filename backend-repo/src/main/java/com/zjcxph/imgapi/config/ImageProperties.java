package com.zjcxph.imgapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "image")
public class ImageProperties {

    private String url;
    private String username;
    private String password;
    private String basePath;

    private String serverUrlDefault;
    private String serverUrlBa01;
    private String serverUrlBa02;
    private String serverUrlBa03;

}