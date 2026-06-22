package com.zjcxph.imgapi.utils;

import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码工具类 — bcrypt 为主，遗留 SHA-256 仅用于兼容。
 * <p>
 * 自 v0.1.0 起，新密码/重置密码统一使用 bcrypt (strength=12)。
 * 旧密码的 SHA-256 哈希仅用于迁移检查（未来版本将移除）。
 * </p>
 */
public final class PasswordUtil {

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(12);

    private PasswordUtil() {
    }

    // ==================== bcrypt（主力） ====================

    /**
     * 对原始密码进行 bcrypt 哈希。
     *
     * @param rawPassword 明文密码，null 返回 null
     * @return bcrypt 哈希字符串（$2a$12$...）
     */
    public static String encode(String rawPassword) {
        if (rawPassword == null) {
            return null;
        }
        return BCRYPT.encode(rawPassword);
    }

    /**
     * 校验原始密码与 bcrypt 哈希是否匹配。
     *
     * @param rawPassword  明文密码
     * @param encodedHash  已存储的 bcrypt 哈希
     * @return true 如果匹配
     */
    public static boolean matches(String rawPassword, String encodedHash) {
        if (rawPassword == null || encodedHash == null) {
            return false;
        }
        return BCRYPT.matches(rawPassword, encodedHash);
    }

    // ==================== 遗留 SHA-256（仅兼容） ====================

    /**
     * @deprecated 仅用于旧密码迁移校验，新代码请使用 {@link #encode(String)}。
     */
    @Deprecated
    public static String sha256(String rawPassword) {
        if (rawPassword == null) {
            return null;
        }
        return DigestUtils.sha256Hex(rawPassword);
    }

    /**
     * @deprecated 仅用于旧密码迁移校验，新代码请使用 {@link #matches(String, String)}。
     */
    @Deprecated
    public static boolean sha256Matches(String rawPassword, String sha256Hash) {
        if (rawPassword == null || sha256Hash == null) {
            return false;
        }
        return sha256(rawPassword).equalsIgnoreCase(sha256Hash);
    }
}
