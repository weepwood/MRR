package com.zjcxph.imgapi.interceptors;

import com.zjcxph.imgapi.controller.ImageController;
import com.zjcxph.imgapi.utils.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class LoginInterceptor implements HandlerInterceptor {

    Logger logger = LoggerFactory.getLogger(ImageController.class);

    // 从请求头中提取 token
    private String extractToken(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            // "Bearer ".length() == 7
            authorization = authorization.substring(7);
//            System.out.println( authorization);
            return authorization;
        }
        return null;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String authorization = request.getHeader("Authorization");
        authorization = extractToken(authorization);
        try {
            // username
             JwtUtil.parseToken(authorization);
            return true;
        }catch (Exception e){
            logger.error("token invalid: {}", String.valueOf(e));
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":401,\"message\":\"token invalid\"}");
            return false;
        }
    }

}
