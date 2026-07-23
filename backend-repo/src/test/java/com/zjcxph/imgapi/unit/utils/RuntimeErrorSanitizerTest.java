package com.zjcxph.imgapi.unit.utils;

import com.zjcxph.imgapi.utils.RuntimeErrorSanitizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeErrorSanitizerTest {

    @Test
    void shouldRedactSecretsTokensAndLongIdentityNumbers() {
        String sanitized = RuntimeErrorSanitizer.sanitizeSummary(
                "password=plain-secret token:abc.def.ghi Authorization=Bearer eyJabcdefghijk.abcdefghijk.abcdefghijk id=110101199001011234"
        );

        assertFalse(sanitized.contains("plain-secret"));
        assertFalse(sanitized.contains("abc.def.ghi"));
        assertFalse(sanitized.contains("110101199001011234"));
        assertTrue(sanitized.contains("[REDACTED]"));
        assertTrue(sanitized.contains("[REDACTED_ID]"));
    }

    @Test
    void shouldRedactQuotedJsonSecrets() {
        String sanitized = RuntimeErrorSanitizer.sanitizeSummary(
                "{\"password\":\"plain-secret\",\"access_key\":\"key-value\"}"
        );

        assertFalse(sanitized.contains("plain-secret"));
        assertFalse(sanitized.contains("key-value"));
        assertTrue(sanitized.contains("[REDACTED]"));
    }

    @Test
    void shouldGroupMessagesThatOnlyDifferByVariableValues() {
        String first = RuntimeErrorSanitizer.fingerprint(
                "ERROR",
                "com.zjcxph.imgapi.storage.Reader",
                "java.io.IOException",
                "读取图片 123456 失败 ERR-20260723-ABCDEF12"
        );
        String second = RuntimeErrorSanitizer.fingerprint(
                "ERROR",
                "com.zjcxph.imgapi.storage.Reader",
                "java.io.IOException",
                "读取图片 987654 失败 ERR-20260723-1234ABCD"
        );

        assertEquals(first, second);
    }

    @Test
    void shouldGenerateReadableErrorId() {
        assertTrue(RuntimeErrorSanitizer.newErrorId().matches("ERR-\\d{8}-[A-F0-9]{8}"));
    }
}
