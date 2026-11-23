package com.zjcxph.imgapi.utils;

import java.util.List;
import java.util.Locale;

/**
 * 应用内部密钥的最低配置策略。
 *
 * <p>部署模板（{@code deploy/windows/templates/application-secrets.properties}、{@code .env.example}、
 * {@code application-local.template.properties}）是公开内容，其中的占位值长度足以通过
 * “至少 32 字符”校验。若运维漏改，任何人都能用已知值伪造 JWT、外部调阅 HMAC 票据或解密
 * 身份证参数，因此这类值必须在启动或使用时 fail-closed。</p>
 *
 * <p>拒绝原因只描述问题类型，绝不回显密钥内容。</p>
 */
public final class SecretPolicy {

    private static final List<String> PLACEHOLDER_MARKERS = List.of(
            "CHANGE_ME", "CHANGEME", "CHANGE-ME",
            "REPLACE_WITH", "REPLACE-WITH", "REPLACE_ME", "REPLACE-ME",
            "YOUR_SECRET", "YOUR-SECRET", "YOUR-OWN", "YOUR_OWN");

    /** 随机生成的密钥（hex/base64）字符种类远多于此；低于该值通常是手工填写的重复字符。 */
    static final int MIN_DISTINCT_CHARACTERS = 8;

    private SecretPolicy() {
    }

    /** 是否包含仓库模板中使用的占位标记（大小写不敏感）。 */
    public static boolean isPlaceholder(String value) {
        if (value == null) {
            return false;
        }
        String upper = value.trim().toUpperCase(Locale.ROOT);
        for (String marker : PLACEHOLDER_MARKERS) {
            if (upper.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /** 字符种类是否过少，例如 32 个相同字符。 */
    public static boolean hasLowDiversity(String value) {
        return value != null && value.trim().chars().distinct().count() < MIN_DISTINCT_CHARACTERS;
    }

    /**
     * 校验一个已配置的密钥。
     *
     * @return {@code null} 表示通过；否则返回不含密钥内容的拒绝原因
     */
    public static String rejectionReason(String value) {
        if (isPlaceholder(value)) {
            return "仍为模板占位值";
        }
        if (hasLowDiversity(value)) {
            return "字符种类过少，疑似非随机生成";
        }
        return null;
    }
}
