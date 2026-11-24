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
    void shouldRedactSensitiveRelativeRequestTargetsWithoutLosingStatus() {
        for (String path : new String[]{
                "/idcard/synthetic-marker",
                "/api/v1/search/getBAHByID/%30%30%30",
                "/api/v1/patients/idcard/synthetic-marker?ticket=secret-marker",
                "/api/v1/img/content/synthetic-marker",
                "/api/v1/search/getBAHByEncryptID?encryptId=synthetic-marker",
                "/api/v1/search/archive-cases?id=synthetic-marker",
                "/archive?id=synthetic-marker",
                "/api/v1/patients?keyword=synthetic-marker"}) {
            String text = "Request rejected: " + path + " status=403";
            for (String sanitized : new String[]{
                    RuntimeErrorSanitizer.sanitizeSummary(text),
                    RuntimeErrorSanitizer.sanitizeLogMessage(text),
                    RuntimeErrorSanitizer.sanitizeStackTrace(text)}) {
                assertFalse(sanitized.contains("synthetic-marker"), path);
                assertFalse(sanitized.contains("%30%30%30"), path);
                assertFalse(sanitized.contains("secret-marker"), path);
                assertTrue(sanitized.contains("[REDACTED_PATH]"), path);
                assertTrue(sanitized.contains("status=403"), path);
                assertEquals(sanitized, RuntimeErrorSanitizer.sanitizeLogMessage(sanitized), path);
            }
        }
    }

    @Test
    void shouldKeepFixedRouteTemplatesAndHideAbsoluteUrls() {
        String route = "GET /api/v1/operations/overview status=200";
        assertEquals(route, RuntimeErrorSanitizer.sanitizeLogMessage(route));
        String full = "Request rejected: https://synthetic.invalid/idcard/synthetic-marker status=403";
        for (String sanitized : new String[]{
                RuntimeErrorSanitizer.sanitizeSummary(full),
                RuntimeErrorSanitizer.sanitizeLogMessage(full)}) {
            assertFalse(sanitized.contains("synthetic.invalid"));
            assertFalse(sanitized.contains("synthetic-marker"));
            assertTrue(sanitized.contains("status=403"));
        }
    }

    @Test
    void shouldGenerateReadableErrorId() {
        assertTrue(RuntimeErrorSanitizer.newErrorId().matches("ERR-\\d{8}-[A-F0-9]{8}"));
    }
}
