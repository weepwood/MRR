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
        String combinedKey = userId + "_" + key;
        // 确保密钥长度为 32 字节
        if (combinedKey.length() > 32) {
            return combinedKey.substring(0, 32);
        } else {
            // 如果长度不足 32 字节，用 0 填充
            return String.format("%-32s", combinedKey).replace(' ', '0');
        }
    }
    
    /**
     * 根据用户ID、时间戳和密钥生成用户特定的密钥（新版本，包含时间戳）
     * @param userId 用户ID
     * @param timestamp 时间戳
     * @param key 基础密钥
     * @return 用户特定密钥（32字节）
     */
    private static String generateUserSpecificKeyWithTimestamp(String userId, String timestamp, String key) {
        String combinedKey = userId + "_" + timestamp + "_" + key;
        // 确保密钥长度为32字节
        if (combinedKey.length() > 32) {
            return combinedKey.substring(0, 32);
        } else {
            // 如果长度不足32字节，用0填充
            return String.format("%-32s", combinedKey).replace(' ', '0');
        }
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
        try {
            // 生成用户特定密钥
            String userSpecificKey = generateUserSpecificKey(userId, key);
            
            // 将十六进制字符串转换为字节数组
            byte[] ciphertextBytes = Hex.decodeHex(ciphertext);
            byte[] ivBytes = Hex.decodeHex(iv);
            byte[] keyBytes = userSpecificKey.getBytes(StandardCharsets.UTF_8);
            
            // 创建密钥规范
            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, ALGORITHM);
            
            // 创建初始化向量规范
            IvParameterSpec ivParameterSpec = new IvParameterSpec(ivBytes);
            
            // 创建密码器
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);
            
            // 解密
            byte[] decryptedBytes = cipher.doFinal(ciphertextBytes);
            
            return new String(decryptedBytes, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            logger.error("解密身份证号码失败: {}", e.getMessage(), e);
            throw new RuntimeException("解密失败", e);
        }
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
        try {
            // 生成用户特定密钥（包含时间戳）
            String userSpecificKey = generateUserSpecificKeyWithTimestamp(userId, timestamp, key);
            
            // 将十六进制字符串转换为字节数组
            byte[] ciphertextBytes = Hex.decodeHex(ciphertext);
            byte[] ivBytes = Hex.decodeHex(iv);
            byte[] keyBytes = userSpecificKey.getBytes(StandardCharsets.UTF_8);
            
            // 创建密钥规范
            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, ALGORITHM);
            
            // 创建初始化向量规范
            IvParameterSpec ivParameterSpec = new IvParameterSpec(ivBytes);
            
            // 创建密码器
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);
            
            // 解密
            byte[] decryptedBytes = cipher.doFinal(ciphertextBytes);
            
            return new String(decryptedBytes, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            logger.error("解密身份证号码失败: {}", e.getMessage(), e);
            throw new RuntimeException("解密失败", e);
        }
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
