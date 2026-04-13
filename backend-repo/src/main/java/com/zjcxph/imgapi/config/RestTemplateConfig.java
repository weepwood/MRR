 package com.zjcxph.imgapi.config;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate 配置类
 * 配置HTTP连接池以提升性能和资源利用率
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        // 创建连接池管理器
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(100); // 最大连接数
        connectionManager.setDefaultMaxPerRoute(20); // 每个路由的最大连接数
        
        // 设置连接超时和读取超时
        Timeout connectTimeout = Timeout.ofSeconds(5);
        Timeout socketTimeout = Timeout.ofSeconds(30);
        
        // 创建请求配置
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(connectTimeout)
                .setResponseTimeout(socketTimeout)
                .build();
        
        // 创建HttpClient
        HttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build();
        
        // 创建请求工厂
        ClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        
        return new RestTemplate(factory);
    }
}
