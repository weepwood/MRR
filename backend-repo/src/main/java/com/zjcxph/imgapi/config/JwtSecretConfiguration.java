package com.zjcxph.imgapi.config;

import com.zjcxph.imgapi.utils.JwtUtil;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 在应用启动时从 Spring Environment 注入 JWT 密钥并进行长度校验。
 */
@Component
public class JwtSecretConfiguration {

    public JwtSecretConfiguration(Environment environment) {
        String configured = environment.getProperty("JWT_SECRET_KEY");
        if (configured == null || configured.isBlank()) {
            configured = environment.getProperty("jwt.secret-key");
        }
        JwtUtil.configure(configured);
    }
}
