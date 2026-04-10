package com.zjcxph.imgapi.utils;

import org.apache.commons.codec.digest.DigestUtils;

public final class PasswordUtil {

    private PasswordUtil() {
    }

    /**
     * 对原始密码进行 SHA-256 哈希加密。
     * <p>
     * 使用 Apache Commons Codec 的 DigestUtils 工具类对明文密码进行 SHA-256 加密，
     * 返回十六进制格式的哈希字符串。该方法用于密码存储前的加密处理。
     * </p>
     *
     * @param rawPassword 原始明文密码，如果为 null 则返回 null
     * @return String SHA-256 加密后的十六进制哈希字符串，如果输入为 null 则返回 null
     */
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

    public static String encode(String number) {
        return DigestUtils.sha256Hex(number);
    }
}
