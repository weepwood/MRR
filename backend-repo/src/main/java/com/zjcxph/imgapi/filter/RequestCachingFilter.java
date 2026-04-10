package com.zjcxph.imgapi.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;

/**
 * 请求缓存过滤器
 * 使用 ContentCachingRequestWrapper 包装请求,以便后续读取请求体内容
 */
public class RequestCachingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest) {
            // 只缓存 POST、PUT、PATCH 等可能有请求体的请求
            String method = ((HttpServletRequest) request).getMethod();
            if ("POST".equalsIgnoreCase(method) || 
                "PUT".equalsIgnoreCase(method) || 
                "PATCH".equalsIgnoreCase(method)) {
                ContentCachingRequestWrapper wrappedRequest = 
                    new ContentCachingRequestWrapper((HttpServletRequest) request, 10240);
                chain.doFilter(wrappedRequest, response);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
