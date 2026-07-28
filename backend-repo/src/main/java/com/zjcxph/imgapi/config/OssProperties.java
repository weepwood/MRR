package com.zjcxph.imgapi.config;

import jakarta.validation.constraints.AssertTrue;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "oss")
public class OssProperties {

    private String endpoint;
    private String bucket;
    private String accessKeyId;
    private String accessKeySecret;
    private String region;
    private String baseUrl;
    private int urlExpireSeconds = 3600;
    private boolean pathStyleAccess = false;

    @AssertTrue(message = "OSS Access Key ID 与 Secret 必须同时配置")
    public boolean isCredentialPairValid() {
        return hasText(accessKeyId) == hasText(accessKeySecret);
    }

    @AssertTrue(message = "配置 OSS 凭据时 endpoint、bucket 和 region 不能为空")
    public boolean isRequiredLocationValid() {
        if (!hasAnyCredential()) {
            return true;
        }
        return hasText(endpoint) && hasText(bucket) && hasText(region);
    }

    @AssertTrue(message = "OSS 凭据不能包含首尾引号或控制字符")
    public boolean isCredentialFormatValid() {
        return credentialFormatValid(accessKeyId) && credentialFormatValid(accessKeySecret);
    }

    @AssertTrue(message = "配置 OSS 凭据时 URL 有效期必须在 60 至 86400 秒之间")
    public boolean isUrlExpiryValid() {
        return !hasAnyCredential() || (urlExpireSeconds >= 60 && urlExpireSeconds <= 86_400);
    }

    private boolean hasAnyCredential() {
        return hasText(accessKeyId) || hasText(accessKeySecret);
    }

    private boolean credentialFormatValid(String value) {
        if (!hasText(value)) {
            return true;
        }
        String trimmed = value.trim();
        if (!trimmed.equals(value)
                || trimmed.startsWith("\"")
                || trimmed.endsWith("\"")
                || trimmed.startsWith("'")
                || trimmed.endsWith("'")) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
