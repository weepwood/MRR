package com.zjcxph.imgapi.utils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 将身份证号转换为可放入 URL 的不透明令牌。
 *
 * <p>令牌采用 AES-GCM 加密并携带随机 IV，既不暴露身份证原文，也能检测参数是否被篡改。</p>
 */
public final class IdCardUrlTokenUtil {
    private static final byte TOKEN_VERSION = 1;
    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final byte[] AAD = "MRR-IDCARD-URL-V1".getBytes(StandardCharsets.UTF_8);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private IdCardUrlTokenUtil() {
    }

    public static String encrypt(String idCard, String secret) {
        if (idCard == null || idCard.isBlank()) {
            throw new IllegalArgumentException("身份证号不能为空");
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(secret), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            cipher.updateAAD(AAD);
            byte[] ciphertext = cipher.doFinal(idCard.trim().getBytes(StandardCharsets.UTF_8));

            ByteBuffer payload = ByteBuffer.allocate(1 + IV_LENGTH + ciphertext.length);
            payload.put(TOKEN_VERSION);
            payload.put(iv);
            payload.put(ciphertext);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.array());
        } catch (Exception e) {
            throw new IllegalStateException("生成身份证查询令牌失败", e);
        }
    }

    public static String decrypt(String token, String secret) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("身份证查询令牌不能为空");
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(token);
            if (payload.length <= 1 + IV_LENGTH || payload[0] != TOKEN_VERSION) {
                throw new IllegalArgumentException("身份证查询令牌格式无效");
            }

            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(payload, 1, iv, 0, IV_LENGTH);
            byte[] ciphertext = new byte[payload.length - 1 - IV_LENGTH];
            System.arraycopy(payload, 1 + IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(secret), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            cipher.updateAAD(AAD);
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("身份证查询令牌无效或已被篡改", e);
        }
    }

    public static String mask(String idCard) {
        if (idCard == null || idCard.isBlank()) {
            return "";
        }
        String value = idCard.trim();
        if (value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4)
                + "*".repeat(value.length() - 8)
                + value.substring(value.length() - 4);
    }

    private static SecretKeySpec deriveKey(String secret) throws Exception {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("身份证令牌密钥未配置");
        }
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(secret.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, "AES");
    }
}
