package com.zjcxph.imgapi.service.impl;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DataRelationComparisonUtilsTest {

    @Test
    void numericCodesWithDifferentPaddingAreFormatOnlyDifferences() {
        Map<String, Object> comparison = DataRelationComparisonUtils.compareCode(
                "BAH", "00000123", "mr_patient", "123"
        );

        assertThat(comparison)
                .containsEntry("canonicalValue", "00000123")
                .containsEntry("sourceValue", "123")
                .containsEntry("status", "FORMAT_ONLY");
    }

    @Test
    void differentNumericCodesAreRealConflicts() {
        Map<String, Object> comparison = DataRelationComparisonUtils.compareCode(
                "SJH", "00000123", "mr_scan", "00000456"
        );

        assertThat(comparison).containsEntry("status", "CONFLICT");
    }

    @Test
    void blankSourceValueIsReportedAsMissing() {
        Map<String, Object> comparison = DataRelationComparisonUtils.compareText(
                "患者姓名", "张三", "mr_statistics", "   "
        );

        assertThat(comparison)
                .containsEntry("sourceValue", null)
                .containsEntry("status", "MISSING");
    }

    @Test
    void searchTypeIsNormalizedWithoutChangingSearchValueRules() {
        assertThat(DataRelationComparisonUtils.normalizeSearchType(" sjh ")).isEqualTo("SJH");
        assertThat(DataRelationComparisonUtils.normalizeSearchType(null)).isEqualTo("BAH");
    }
}
