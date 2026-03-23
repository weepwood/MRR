package com.zjcxph.imgapi.interceptors;

import com.zjcxph.imgapi.pojo.Log;
import com.zjcxph.imgapi.service.LogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Date;

@Component
public class LogInterceptor implements HandlerInterceptor {

    @Autowired
    private LogService logService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 记录请求开始时间
        long startTime = System.currentTimeMillis();
        request.setAttribute("startTime", startTime);
        
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        // 在视图渲染之前可以添加处理逻辑
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 获取请求开始时间
        Long startTime = (Long) request.getAttribute("startTime");
        long endTime = System.currentTimeMillis();
        long executeTime = startTime != null ? endTime - startTime : 0;

        // 创建日志对象
        Log log = new Log();
        log.setClientIp(getClientIP(request));
        log.setRequestUri(request.getRequestURI());
        log.setMethod(request.getMethod());
        log.setUserAgent(request.getHeader("User-Agent"));
        log.setAccessTime(new Date());
        log.setQueryString(request.getQueryString());
        log.setRequestBody(""); // 不再尝试读取请求体，避免与其它组件冲突
        log.setResponseStatus(String.valueOf(response.getStatus()));
        log.setExecuteTime(executeTime);
        log.setReferer(request.getHeader("Referer"));

        // 保存日志
        logService.saveLog(log);
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

}