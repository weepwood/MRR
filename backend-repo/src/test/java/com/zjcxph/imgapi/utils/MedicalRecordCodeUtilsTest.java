package com.zjcxph.imgapi.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MedicalRecordCodeUtilsTest {

    @Test
    void shouldPadNumericCodesToEightDigits() {
        assertEquals("00000123", MedicalRecordCodeUtils.normalize("123"));
        assertEquals("00000123", MedicalRecordCodeUtils.normalize(" 123 "));
        assertEquals("00000000", MedicalRecordCodeUtils.normalize("0"));
        assertEquals("12345678", MedicalRecordCodeUtils.normalize("12345678"));
    }

    @Test
    void shouldNotSilentlyRewriteUnsupportedValues() {
        assertEquals("123456789", MedicalRecordCodeUtils.normalize("123456789"));
        assertEquals("SJH001", MedicalRecordCodeUtils.normalize(" SJH001 "));
        assertEquals("", MedicalRecordCodeUtils.normalize("   "));
        assertNull(MedicalRecordCodeUtils.normalize(null));
    }

    @Test
    void shouldProduceSameSearchTermForPaddedAndUnpaddedCodes() {
        assertEquals("123", MedicalRecordCodeUtils.toSearchTerm("123"));
        assertEquals("123", MedicalRecordCodeUtils.toSearchTerm("00000123"));
        assertEquals("0", MedicalRecordCodeUtils.toSearchTerm("00000000"));
        assertEquals("SJH001", MedicalRecordCodeUtils.toSearchTerm(" SJH001 "));
    }

    @Test
    void shouldRequireSjhAtBahUniquenessBoundary() {
        assertFalse(MedicalRecordCodeUtils.requiresSjhForBah("9999999"));
        assertFalse(MedicalRecordCodeUtils.requiresSjhForBah("09999999"));
        assertTrue(MedicalRecordCodeUtils.requiresSjhForBah("10000000"));
        assertTrue(MedicalRecordCodeUtils.requiresSjhForBah("10000001"));
        assertFalse(MedicalRecordCodeUtils.requiresSjhForBah("SJH001"));
        assertFalse(MedicalRecordCodeUtils.requiresSjhForBah(null));
    }

    @Test
    void shouldValidateSupportedNumericCodes() {
        assertTrue(MedicalRecordCodeUtils.isSupportedNumericCode("1"));
        assertTrue(MedicalRecordCodeUtils.isSupportedNumericCode("00000123"));
        assertFalse(MedicalRecordCodeUtils.isSupportedNumericCode(""));
        assertFalse(MedicalRecordCodeUtils.isSupportedNumericCode("123456789"));
        assertFalse(MedicalRecordCodeUtils.isSupportedNumericCode("ABC123"));
    }
}
