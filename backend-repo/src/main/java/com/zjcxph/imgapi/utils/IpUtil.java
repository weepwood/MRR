package com.zjcxph.imgapi.utils;

import jakarta.servlet.http.HttpServletRequest;

public final class IpUtil {

    private IpUtil() {
    }

    public static String getClientIp(HttpServletRequest request) {
        String ip = firstForwardedIp(request.getHeader("X-Forwarded-For"));
        if (isMissing(ip)) {
            ip = normalize(request.getHeader("X-Real-IP"));
        }
        if (isMissing(ip)) {
            ip = normalize(request.getRemoteAddr());
        }
        return isMissing(ip) ? "unknown" : ip;
    }

    private static String firstForwardedIp(String value) {
        if (isMissing(value)) {
            return null;
        }
        int separator = value.indexOf(',');
        return normalize(separator >= 0 ? value.substring(0, separator) : value);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private static boolean isMissing(String value) {
        return value == null || value.isBlank() || "unknown".equalsIgnoreCase(value);
    }
}
