package com.zjcxph.imgapi.utils;

import jakarta.servlet.http.HttpServletRequest;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

public final class IpUtil {

    private static final int MAX_FORWARDED_HEADER_LENGTH = 256;
    private static final Pattern IPV4_CHARACTERS = Pattern.compile("^[0-9.]+$");
    private static final Pattern IPV6_CHARACTERS = Pattern.compile("^[0-9A-Fa-f:.]+$");

    private IpUtil() {
    }

    public static String getClientIp(HttpServletRequest request) {
        String remoteIp = getRemoteIp(request);
        if (!isLoopbackProxy(remoteIp)) {
            return remoteIp;
        }

        String forwardedIp = firstForwardedIp(request.getHeader("X-Forwarded-For"));
        if (isValidIp(forwardedIp)) {
            return forwardedIp;
        }

        String realIp = normalize(request.getHeader("X-Real-IP"));
        if (isValidIp(realIp)) {
            return realIp;
        }

        return remoteIp;
    }

    public static String getRemoteIp(HttpServletRequest request) {
        String remoteIp = normalize(request.getRemoteAddr());
        return isMissing(remoteIp) ? "unknown" : remoteIp;
    }

    private static boolean isLoopbackProxy(String remoteIp) {
        return "127.0.0.1".equals(remoteIp)
                || "::1".equals(remoteIp)
                || "0:0:0:0:0:0:0:1".equalsIgnoreCase(remoteIp);
    }

    private static String firstForwardedIp(String value) {
        if (isMissing(value) || value.length() > MAX_FORWARDED_HEADER_LENGTH) {
            return null;
        }
        int separator = value.indexOf(',');
        return normalize(separator >= 0 ? value.substring(0, separator) : value);
    }

    private static boolean isValidIp(String value) {
        if (isMissing(value) || value.length() > 45) {
            return false;
        }
        return value.indexOf(':') >= 0 ? isValidIpv6(value) : isValidIpv4(value);
    }

    private static boolean isValidIpv4(String value) {
        if (!IPV4_CHARACTERS.matcher(value).matches()) {
            return false;
        }
        String[] segments = value.split("\\.", -1);
        if (segments.length != 4) {
            return false;
        }
        for (String segment : segments) {
            if (segment.isEmpty() || segment.length() > 3) {
                return false;
            }
            int number;
            try {
                number = Integer.parseInt(segment);
            } catch (NumberFormatException exception) {
                return false;
            }
            if (number < 0 || number > 255) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidIpv6(String value) {
        if (!IPV6_CHARACTERS.matcher(value).matches()) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(value);
            return address instanceof Inet6Address;
        } catch (UnknownHostException exception) {
            return false;
        }
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private static boolean isMissing(String value) {
        return value == null || value.isBlank() || "unknown".equalsIgnoreCase(value);
    }
}
