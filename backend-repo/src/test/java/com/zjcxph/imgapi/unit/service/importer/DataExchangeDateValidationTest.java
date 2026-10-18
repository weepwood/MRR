package com.zjcxph.imgapi.unit.service.importer;

import com.zjcxph.imgapi.service.importer.DataExchangeImportSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DataExchangeDateValidationTest {
    @ParameterizedTest
    @ValueSource(strings = {"2026-02-29", "2026-02-30", "2026-04-31", "2026/2/29", "2026.4.31"})
    void rejectsNonexistentDates(String value) {
        var errors = new DataExchangeImportSupport.ErrorCollector();
        assertThat(DataExchangeImportSupport.parseDate(2, "date", value, errors)).isNull();
        assertThat(errors.errors()).singleElement().satisfies(error -> {
            assertThat(error.rowNumber()).isEqualTo(2);
            assertThat(error.field()).isEqualTo("date");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"2024-02-29", "2024/2/29", "2024.2.29", "2024-02-29T12:30:00"})
    void acceptsLeapDaysAndExistingDateFormats(String value) {
        var errors = new DataExchangeImportSupport.ErrorCollector();
        assertThat(DataExchangeImportSupport.parseDate(2, "date", value, errors))
                .isEqualTo(LocalDate.of(2024, 2, 29));
        assertThat(errors.hasErrors()).isFalse();
    }
}
