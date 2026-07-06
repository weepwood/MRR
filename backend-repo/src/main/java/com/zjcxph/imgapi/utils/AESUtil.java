package com.zjcxph.imgapi.utils;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * AES加密解密工具类
 * 对应JavaScript的encryptIdCardWithUserKey方法
 */
public class AESUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(AESUtil.class);
    
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final String SECRET_KEY = "your-32-byte-secret-key-12345678"; // 默认密钥
    
    /**
     * 根据用户ID和密钥生成用户特定的密钥（旧版本，不包含时间戳）
     * @param userId 用户ID
     * @param key 基础密钥
     * @return 用户特定密钥（32 字节）
     */
    private static String generateUserSpecificKey(String userId, String key) {
        return padOrTruncateTo32(userId + "_" + key);
    }

    /**
     * 根据用户ID、时间戳和密钥生成用户特定的密钥（新版本，包含时间戳）
     * @param userId 用户ID
     * @param timestamp 时间戳
     * @param key 基础密钥
     * @return 用户特定密钥（32字节）
     */
    private static String generateUserSpecificKeyWithTimestamp(String userId, String timestamp, String key) {
        return padOrTruncateTo32(userId + "_" + timestamp + "_" + key);
    }

    /**
     * 将密钥材料规范为 32 字节：超长截断，不足补 0。
     * <p>
     * 抽取自原 generateUserSpecificKey / generateUserSpecificKeyWithTimestamp 中重复的长度处理逻辑。
     * </p>
     */
    private static String padOrTruncateTo32(String combinedKey) {
        if (combinedKey.length() > 32) {
            return combinedKey.substring(0, 32);
        }
        return String.format("%-32s", combinedKey).replace(' ', '0');
    }

    /**
     * 解密身份证号码
     * @param ciphertext 密文（十六进制字符串）
     * @param iv 初始化向量（十六进制字符串）
     * @param userId 用户ID
     * @param key 基础密钥
     * @return 解密后的身份证号码
     */
    public static String decryptIdCard(String ciphertext, String iv, String userId, String key) {
        String userSpecificKey = generateUserSpecificKey(userId, key);
        return doDecrypt(ciphertext, iv, userSpecificKey);
    }

    /**
     * 解密身份证号码（使用默认密钥）
     * @param ciphertext 密文（十六进制字符串）
     * @param iv 初始化向量（十六进制字符串）
     * @param userId 用户ID
     * @return 解密后的身份证号码
     */
    public static String decryptIdCard(String ciphertext, String iv, String userId) {
        return decryptIdCard(ciphertext, iv, userId, SECRET_KEY);
    }

    /**
     * 解密身份证号码（新版本，包含时间戳）
     * @param ciphertext 密文（十六进制字符串）
     * @param iv 初始化向量（十六进制字符串）
     * @param userId 用户ID
     * @param timestamp 时间戳
     * @param key 基础密钥
     * @return 解密后的身份证号码
     */
    public static String decryptIdCardWithTimestamp(String ciphertext, String iv, String userId, String timestamp, String key) {
        String userSpecificKey = generateUserSpecificKeyWithTimestamp(userId, timestamp, key);
        return doDecrypt(ciphertext, iv, userSpecificKey);
    }

    /**
     * 解密身份证号码（新版本，包含时间戳，使用默认密钥）
     * @param ciphertext 密文（十六进制字符串）
     * @param iv 初始化向量（十六进制字符串）
     * @param userId 用户ID
     * @param timestamp 时间戳
     * @return 解密后的身份证号码
     */
    public static String decryptIdCardWithTimestamp(String ciphertext, String iv, String userId, String timestamp) {
        return decryptIdCardWithTimestamp(ciphertext, iv, userId, timestamp, SECRET_KEY);
    }

    /**
     * 实际的 AES/CBC/PKCS5Padding 解密逻辑。
     * <p>
     * 抽取自原 decryptIdCard 与 decryptIdCardWithTimestamp 中完全一致的 30 行代码，
     * 消除重复，后续如需切换为 AES-GCM 或更换 KDF 只需改这一处。
     * </p>
     *
     * @param ciphertext      密文（十六进制字符串）
     * @param iv              初始化向量（十六进制字符串）
     * @param userSpecificKey 已派生的 32 字节用户密钥
     * @return 解密后的明文
     */
    private static String doDecrypt(String ciphertext, String iv, String userSpecificKey) {
        try {
            byte[] ciphertextBytes = Hex.decodeHex(ciphertext);
            byte[] ivBytes = Hex.decodeHex(iv);
            byte[] keyBytes = userSpecificKey.getBytes(StandardCharsets.UTF_8);

            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, ALGORITHM);
            IvParameterSpec ivParameterSpec = new IvParameterSpec(ivBytes);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);

            byte[] decryptedBytes = cipher.doFinal(ciphertextBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            logger.error("解密身份证号码失败: {}", e.getMessage(), e);
            throw new RuntimeException("解密失败", e);
        }
    }
    
    /**
     * 从加密的ID字符串中解析出密文和IV
     * 假设加密的ID格式为: ciphertext_iv 或者 JSON格式
     * @param encryptID 加密的ID字符串
     * @return 包含密文和IV的数组 [ciphertext, iv]
     */
    public static String[] parseEncryptID(String encryptID) {
        try {
            // 尝试解析JSON格式
            if (encryptID.startsWith("{") && encryptID.endsWith("}")) {
                // 简单的JSON解析，假设格式为 {"ciphertext":"...","iv":"..."}
                String ciphertext = extractJsonValue(encryptID, "ciphertext");
                String iv = extractJsonValue(encryptID, "iv");
                return new String[]{ciphertext, iv};
            } else {
                // 假设格式为 ciphertext_iv
                String[] parts = encryptID.split("_");
                if (parts.length >= 2) {
                    return new String[]{parts[0], parts[1]};
                } else {
                    throw new IllegalArgumentException("无效的加密ID格式");
                }
            }
        } catch (Exception e) {
            logger.error("解析加密ID失败: {}", e.getMessage(), e);
            throw new RuntimeException("解析加密ID失败", e);
        }
    }
    
    /**
     * 从加密的ID字符串中解析出密文、IV和时间戳（新版本）
     * 假设加密的ID格式为JSON格式: {"ciphertext":"...","iv":"...","timestamp":"..."}
     * @param encryptID 加密的ID字符串
     * @return 包含密文、IV和时间戳的数组 [ciphertext, iv, timestamp]
     */
    public static String[] parseEncryptIDWithTimestamp(String encryptID) {
        try {
            // 尝试解析JSON格式
            if (encryptID.startsWith("{") && encryptID.endsWith("}")) {
                // 简单的JSON解析，假设格式为 {"ciphertext":"...","iv":"...","timestamp":"..."}
                String ciphertext = extractJsonValue(encryptID, "ciphertext");
                String iv = extractJsonValue(encryptID, "iv");
                String timestamp = extractJsonValue(encryptID, "timestamp");
                return new String[]{ciphertext, iv, timestamp};
            } else {
                throw new IllegalArgumentException("新版本加密ID必须使用JSON格式");
            }
        } catch (Exception e) {
            logger.error("解析加密ID失败: {}", e.getMessage(), e);
            throw new RuntimeException("解析加密ID失败", e);
        }
    }
    
    /**
     * 从JSON字符串中提取指定字段的值
     * @param json JSON字符串
     * @param key 字段名
     * @return 字段值
     */
    private static String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        throw new IllegalArgumentException("无法找到字段: " + key);
    }
}
