package com.zjcxph.imgapi.monitoring;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public record PressureTestRequest(
        @NotBlank(message = "测试名称不能为空")
        String name,

        @NotBlank(message = "目标地址不能为空")
        String targetUrl,

        @Pattern(regexp = "GET|POST|PUT|PATCH|DELETE", message = "请求方法仅支持 GET/POST/PUT/PATCH/DELETE")
        String method,

        @Min(value = 1, message = "并发数必须大于 0")
        @Max(value = 128, message = "并发数不能超过 128")
        int concurrency,

        @Min(value = 1, message = "请求总数必须大于 0")
        @Max(value = 10000, message = "请求总数不能超过 10000")
        int totalRequests,

        @Min(value = 100, message = "超时时间不能低于 100ms")
        @Max(value = 30000, message = "超时时间不能高于 30000ms")
        int timeoutMillis,

        String body,

        Map<String, String> headers
) {
    public PressureTestRequest {
        name = normalize(name, "pressure-test");
        method = normalize(method, "GET").toUpperCase(Locale.ROOT);
        body = normalizeNullable(body);
        headers = headers == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(headers));
    }

    private static String normalize(String value, String fallback) {
        String raw = normalizeNullable(value);
        return raw == null ? fallback : raw;
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
