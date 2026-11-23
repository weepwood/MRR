package com.zjcxph.imgapi.interceptors;

import com.zjcxph.imgapi.security.ImageAccessCookieService;
import com.zjcxph.imgapi.service.DocumentationAccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * 登出时清除浏览器侧的会话 Cookie：文档访问会话与影像访问 Cookie（ADR-0009）。
 *
 * <p>两者在服务端也会随访问令牌撤销而失效，这里清除只是不让失效 Cookie 继续随请求发送。</p>
 */
@Component
public class DocumentationSessionCleanupInterceptor implements HandlerInterceptor {

    private final ImageAccessCookieService imageAccessCookieService;

    public DocumentationSessionCleanupInterceptor() {
        this(ImageAccessCookieService.disabled());
    }

    @Autowired
    public DocumentationSessionCleanupInterceptor(ImageAccessCookieService imageAccessCookieService) {
        this.imageAccessCookieService = imageAccessCookieService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        boolean secure = request.isSecure()
                || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
        ResponseCookie expiredCookie = ResponseCookie.from(DocumentationAccessService.COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie.toString());
        // 直连模式下也清除：开关从 gateway 切回 direct 后，浏览器里可能还留有旧 Cookie。
        imageAccessCookieService.expire(request, response);
        return true;
    }
}
