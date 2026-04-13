package com.zjcxph.imgapi.interceptors;

import com.zjcxph.imgapi.entity.Log;
import com.zjcxph.imgapi.service.AsyncLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.UnsupportedEncodingException;
import java.util.Date;

@Component
public class LogInterceptor implements HandlerInterceptor {

    private final AsyncLogService asyncLogService;

    public LogInterceptor(AsyncLogService asyncLogService) {
        this.asyncLogService = asyncLogService;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        if (shouldSkipLogging(request, handler)) {
            return true;
        }

        request.setAttribute("startTime", System.currentTimeMillis());
        return true;
    }

    @Override
    public void postHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, ModelAndView modelAndView) {
        // no-op
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, Exception ex) {
        if (shouldSkipLogging(request, handler)) {
            return;
        }

        Long startTime = (Long) request.getAttribute("startTime");
        long executeTime = startTime != null ? System.currentTimeMillis() - startTime : 0;

        Log log = new Log();
        log.setClientIp(getClientIP(request));
        log.setRequestUri(request.getRequestURI());
        log.setMethod(request.getMethod());
        log.setUserAgent(request.getHeader("User-Agent"));
        log.setAccessTime(new Date());
        log.setQueryString(request.getQueryString());
        log.setRequestBody(getRequestBody(request));
        log.setResponseStatus(String.valueOf(response.getStatus()));
        log.setExecuteTime(executeTime);
        log.setReferer(request.getHeader("Referer"));

        // 异步保存日志,不阻塞请求响应
        asyncLogService.saveLogAsync(log);
    }

    private boolean shouldSkipLogging(HttpServletRequest request, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (handler instanceof ResourceHttpRequestHandler) {
            return true;
        }

        String uri = request.getRequestURI();
        return uri != null && (
                uri.startsWith("/docs/")
                        || uri.startsWith("/swagger-ui/")
                        || uri.startsWith("/v3/api-docs/")
                        || uri.startsWith("/actuator/")
                        || "/favicon.ico".equals(uri)
                        || "/error".equals(uri)
        );
    }

    private String getClientIP(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    /**
     * 从 ContentCachingRequestWrapper 中读取请求体
     */
    private String getRequestBody(HttpServletRequest request) {
        if (!(request instanceof ContentCachingRequestWrapper)) {
            return "";
        }

        ContentCachingRequestWrapper wrapper = (ContentCachingRequestWrapper) request;
        byte[] content = wrapper.getContentAsByteArray();

        if (content.length == 0) {
            return "";
        }

        try {
            // 限制请求体大小,避免日志过大 (最大 10KB)
            int maxLength = 10240;
            String body = new String(content, 0, Math.min(content.length, maxLength), 
                    wrapper.getCharacterEncoding());
            
            // 如果内容被截断,添加提示
            if (content.length > maxLength) {
                body += "... [请求体过大,已截断]";
            }
            
            return body;
        } catch (UnsupportedEncodingException e) {
            return "[无法解码请求体]";
        }
    }
}
