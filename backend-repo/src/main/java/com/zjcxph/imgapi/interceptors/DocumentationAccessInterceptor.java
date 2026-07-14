package com.zjcxph.imgapi.interceptors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.service.DocumentationAccessService;
import com.zjcxph.imgapi.service.DocumentationAccessService.AccessDecision;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

@Component
public class DocumentationAccessInterceptor implements HandlerInterceptor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DocumentationAccessService documentationAccessService;

    public DocumentationAccessInterceptor(DocumentationAccessService documentationAccessService) {
        this.documentationAccessService = documentationAccessService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = documentationAccessService.findAccessToken(request);
        AccessDecision decision = documentationAccessService.authorizeToken(token, request.getRequestURI());
        if (decision == AccessDecision.ALLOWED) {
            return true;
        }

        int status = decision == AccessDecision.FORBIDDEN ? 403 : 401;
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        OBJECT_MAPPER.writeValue(response.getWriter(), Map.of(
                "code", status,
                "message", status == 403 ? "No permission to access documentation" : "Documentation session required"
        ));
        return false;
    }
}
