package com.zjcxph.imgapi.monitoring;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Getter
public class PressureTestRequest {

    @NotBlank(message = "Test name cannot be blank")
    private String name;

    @Setter
    @NotBlank(message = "Target URL cannot be blank")
    private String targetUrl;

    @Pattern(regexp = "GET|POST|PUT|PATCH|DELETE", message = "Method must be one of GET/POST/PUT/PATCH/DELETE")
    private String method;

    @Setter
    @Min(value = 1, message = "Concurrency must be greater than 0")
    @Max(value = 128, message = "Concurrency must not exceed 128")
    private int concurrency;

    @Setter
    @Min(value = 1, message = "Total requests must be greater than 0")
    @Max(value = 10000, message = "Total requests must not exceed 10000")
    private int totalRequests;

    @Setter
    @Min(value = 100, message = "Timeout must be at least 100ms")
    @Max(value = 30000, message = "Timeout must not exceed 30000ms")
    private int timeoutMillis;

    private String body;
    private Map<String, String> headers;

    public PressureTestRequest() {
        this.name = "pressure-test";
        this.method = "GET";
        this.concurrency = 1;
        this.totalRequests = 1;
        this.timeoutMillis = 3000;
        this.headers = new LinkedHashMap<>();
    }

    public PressureTestRequest(
            String name,
            String targetUrl,
            String method,
            int concurrency,
            int totalRequests,
            int timeoutMillis,
            String body,
            Map<String, String> headers
    ) {
        this.name = normalize(name, "pressure-test");
        this.targetUrl = targetUrl;
        this.method = normalize(method, "GET").toUpperCase(Locale.ROOT);
        this.concurrency = concurrency;
        this.totalRequests = totalRequests;
        this.timeoutMillis = timeoutMillis;
        this.body = normalizeNullable(body);
        setHeaders(headers);
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

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = normalize(name, "pressure-test");
    }

    public String method() {
        return method;
    }

    public void setMethod(String method) {
        this.method = normalize(method, "GET").toUpperCase(Locale.ROOT);
    }

    public String body() {
        return body;
    }

    public void setHeaders(Map<String, String> headers) {
        if (headers == null) {
            this.headers = new LinkedHashMap<>();
        } else {
            this.headers = new LinkedHashMap<>(headers);
        }
    }
}
