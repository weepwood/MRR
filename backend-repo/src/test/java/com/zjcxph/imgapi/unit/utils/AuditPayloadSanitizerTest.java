package com.zjcxph.imgapi.unit.utils;

import com.zjcxph.imgapi.utils.AuditPayloadSanitizer;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AuditPayloadSanitizerTest {
    @org.junit.jupiter.api.AfterEach void resetKey() { AuditPayloadSanitizer.configureKey(null); }
    @Test void configuredIndependentKeySupportsCompatibleNumericSearchWithoutKeepingRawCodes() {
        AuditPayloadSanitizer.configureKey("synthetic-independent-hmac-key-at-least-32-characters");
        assertThat(AuditPayloadSanitizer.protectedValue("00123")).startsWith("hmac:v1:").isEqualTo(AuditPayloadSanitizer.searchKey("123"));
        assertThat(AuditPayloadSanitizer.json("{\"SYNTHETICPRIVATEKEY\":\"data\"}")).doesNotContain("SYNTHETICPRIVATEKEY");
    }

    @Test void hidesNestedNumericIdentityAndUnknownFields() {
        String safe = AuditPayloadSanitizer.json("{\"patients\":[{\"idCard\":123456789012345678,\"name\":\"SYNTHETIC-PATIENT\",\"bah\":\"123\"}],\"page\":2}");
        assertThat(safe).contains("\"page\":2").doesNotContain("123456789012345678", "SYNTHETIC-PATIENT", "\"123\"");
    }
    @Test void malformedJsonNeverFallsBackToRawBody() {
        assertThat(AuditPayloadSanitizer.json("{\"name\":\"SYNTHETIC-PATIENT")).isEqualTo("[UNPARSEABLE PAYLOAD OMITTED]");
        assertThat(AuditPayloadSanitizer.query("name=SYNTHETIC-PATIENT&token=credential&page=2")).contains("page=2").doesNotContain("SYNTHETIC-PATIENT", "credential");
    }
}
