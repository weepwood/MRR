package com.zjcxph.imgapi.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MedicalRecordTypeTest {

    @Test
    void shouldExposeCompleteOrderedTypeRange() {
        List<Integer> codes = MedicalRecordType.orderedValues().stream()
                .map(MedicalRecordType::getCode)
                .toList();

        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15), codes);
    }

    @Test
    void shouldAcceptCurrentBusinessTypesAndRejectUnknownValues() {
        assertTrue(MedicalRecordType.isSupported(0));
        assertTrue(MedicalRecordType.isSupported(11));
        assertTrue(MedicalRecordType.isSupported(15));
        assertFalse(MedicalRecordType.isSupported(null));
        assertFalse(MedicalRecordType.isSupported(-1));
        assertFalse(MedicalRecordType.isSupported(16));
    }
}
