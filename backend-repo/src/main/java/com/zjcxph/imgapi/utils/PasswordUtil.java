package com.zjcxph.imgapi.utils;

import org.apache.commons.codec.digest.DigestUtils;

public final class PasswordUtil {

    private PasswordUtil() {
    }

    public static String sha256(String rawPassword) {
        if (rawPassword == null) {
            return null;
        }
        return DigestUtils.sha256Hex(rawPassword);
    }

    public static boolean matches(String rawPassword, String passwordHash) {
        if (rawPassword == null || passwordHash == null) {
            return false;
        }
        return sha256(rawPassword).equalsIgnoreCase(passwordHash);
    }
}
