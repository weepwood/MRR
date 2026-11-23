package com.zjcxph.imgapi.config;

import com.zjcxph.imgapi.utils.JwtUtil;
import com.zjcxph.imgapi.utils.SecretPolicy;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 在应用启动时从 Spring Environment 注入 JWT 密钥并进行长度与占位值校验。
 *
 * <p>同时校验身份证加解密密钥 {@code aes.secret.key}：该值允许留空（对应功能按需
 * fail-closed），但一旦配置为模板占位值或明显弱值，必须阻止启动，而不是带着公开密钥运行。</p>
 */
@Component
public class JwtSecretConfiguration {

    public JwtSecretConfiguration(Environment environment) {
        String configured = environment.getProperty("JWT_SECRET_KEY");
        if (configured == null || configured.isBlank()) {
            configured = environment.getProperty("jwt.secret-key");
        }
        JwtUtil.configure(configured);
        requireUsableOptionalSecret("aes.secret.key / AES_SECRET_KEY", environment.getProperty("aes.secret.key"));
    }

    static void requireUsableOptionalSecret(String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String rejection = SecretPolicy.rejectionReason(value);
        if (rejection != null) {
            throw new IllegalStateException(name + " " + rejection + "，请改为随机生成的密钥");
        }
    }
}
