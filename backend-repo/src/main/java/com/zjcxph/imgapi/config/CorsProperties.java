package com.zjcxph.imgapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "mrr.cors")
public class CorsProperties {
    /**
     * 允许的浏览器 Origin 精确列表。为空时不开放跨域；同源访问不受影响。
     */
    private List<String> allowedOrigins = new ArrayList<>();
}
