package com.zjcxph.imgapi.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;

/**
 * 请求缓存过滤器。
 *
 * <p>包装可能携带请求体的方法，供访问审计在请求完成后读取文本正文。
 * 缓存上限与审计正文上限保持一致，上传文件和二进制内容仍由审计层省略。</p>
 */
public class RequestCachingFilter implements Filter {

    private static final int AUDIT_BODY_CACHE_LIMIT = 16384;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest && hasRequestBody(httpRequest.getMethod())) {
            ContentCachingRequestWrapper wrappedRequest =
                    new ContentCachingRequestWrapper(httpRequest, AUDIT_BODY_CACHE_LIMIT);
            chain.doFilter(wrappedRequest, response);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean hasRequestBody(String method) {
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method);
    }
}
