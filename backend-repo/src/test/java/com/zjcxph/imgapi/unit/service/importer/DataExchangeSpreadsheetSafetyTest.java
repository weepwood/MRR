package com.zjcxph.imgapi.unit.service.importer;

import com.zjcxph.imgapi.service.importer.DataExchangeImportSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataExchangeSpreadsheetSafetyTest {

    @Test
    void protectsFormulaLikeValues() {
        assertRoundTrip("=HYPERLINK(\"https://example.invalid\")");
        assertRoundTrip("+SUM(1,2)");
        assertRoundTrip("-1+2");
        assertRoundTrip("@SUM(1,2)");
    }

    @Test
    void preservesLeadingZerosAndLongIdentifiers() {
        assertRoundTrip("00001234");
        assertRoundTrip("110101199001011234");
    }

    @Test
    void preservesExistingApostrophesAndOrdinaryText() {
        assertRoundTrip("'=SUM(1,2)");
        assertRoundTrip("普通文本");
        assertEquals("普通文本", DataExchangeImportSupport.protectSpreadsheetValue("普通文本"));
    }

    private void assertRoundTrip(String value) {
        String protectedValue = DataExchangeImportSupport.protectSpreadsheetValue(value);
        assertEquals(value, DataExchangeImportSupport.restoreSpreadsheetProtectedValue(protectedValue));
    }
}
