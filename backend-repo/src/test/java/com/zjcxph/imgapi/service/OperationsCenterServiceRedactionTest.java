package com.zjcxph.imgapi.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OperationsCenterServiceRedactionTest {

    @Test
    void redactsIdentityNetworkAndErrorDetailsFromDiagnosticReport() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("id", 12L);
        source.put("request_id", "req-123");
        source.put("username", "administrator");
        source.put("client_ip", "10.0.0.8");
        source.put("request_uri", "/api/v1/operations/image-source?bah=123456&idcard=3301#detail");
        source.put("method", "GET");
        source.put("response_status", "500");
        source.put("execute_time", 25L);
        source.put("access_time", "2026-07-23T00:00:00Z");
        source.put("error_message", "patient 3301 failed with token secret");

        Map<String, Object> result = OperationsCenterService.redactOperationForReport(source);

        assertThat(result.get("username")).isEqualTo("[REDACTED]");
        assertThat(result.get("client_ip")).isEqualTo("[REDACTED]");
        assertThat(result.get("error_message")).isEqualTo("[REDACTED]");
        assertThat(result.get("request_uri")).isEqualTo("/api/v1/operations/image-source");
        assertThat(result.get("request_id")).isEqualTo("req-123");
        assertThat(result.toString()).doesNotContain("3301", "secret", "administrator", "10.0.0.8");
    }
}
