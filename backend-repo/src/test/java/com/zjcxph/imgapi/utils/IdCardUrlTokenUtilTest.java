package com.zjcxph.imgapi.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdCardUrlTokenUtilTest {

    private static final String SECRET = "test-secret-for-id-card-url-token";

    @Test
    void shouldRoundTripIdCardWithoutExposingPlaintext() {
        String idCard = "11010519491231002X";

        String token = IdCardUrlTokenUtil.encrypt(idCard, SECRET);

        assertFalse(token.contains(idCard));
        assertEquals(idCard, IdCardUrlTokenUtil.decrypt(token, SECRET));
    }

    @Test
    void shouldUseRandomIvForRepeatedEncryption() {
        String idCard = "11010519491231002X";

        String first = IdCardUrlTokenUtil.encrypt(idCard, SECRET);
        String second = IdCardUrlTokenUtil.encrypt(idCard, SECRET);

        assertFalse(first.equals(second));
        assertEquals(idCard, IdCardUrlTokenUtil.decrypt(first, SECRET));
        assertEquals(idCard, IdCardUrlTokenUtil.decrypt(second, SECRET));
    }

    @Test
    void shouldRejectTamperedToken() {
        String token = IdCardUrlTokenUtil.encrypt("11010519491231002X", SECRET);
        char replacement = token.endsWith("A") ? 'B' : 'A';
        String tampered = token.substring(0, token.length() - 1) + replacement;

        assertThrows(IllegalArgumentException.class,
                () -> IdCardUrlTokenUtil.decrypt(tampered, SECRET));
    }

    @Test
    void shouldMaskIdCard() {
        assertEquals("1101**********002X", IdCardUrlTokenUtil.mask("11010519491231002X"));
    }
}
