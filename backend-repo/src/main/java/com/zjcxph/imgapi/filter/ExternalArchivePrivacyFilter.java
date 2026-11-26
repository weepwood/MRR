package com.zjcxph.imgapi.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;

import java.io.IOException;

/** Bound only to external archive/session/ticket endpoints; applies before MVC validation. */
public final class ExternalArchivePrivacyFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (response instanceof HttpServletResponse http) {
            http.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
            http.setHeader(HttpHeaders.PRAGMA, "no-cache");
            http.setHeader("Referrer-Policy", "no-referrer");
        }
        chain.doFilter(request, response);
    }
}
