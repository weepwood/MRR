package com.zjcxph.imgapi.unit.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Flyway 已发布迁移校验和测试")
class FlywayMigrationChecksumTest {

    private static final String DATA_QUALITY_MIGRATION =
            "db/migration/V202607141800__database_monitoring_and_data_quality.sql";
    private static final int EXPECTED_CHECKSUM = -948436209;

    @Test
    @DisplayName("V202607141800 保持数据库已记录的原始校验和")
    void dataQualityMigrationRemainsImmutable() throws Exception {
        CRC32 crc32 = new CRC32();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        try (InputStream inputStream = classLoader.getResourceAsStream(DATA_QUALITY_MIGRATION)) {
            assertThat(inputStream)
                    .as("Flyway migration resource %s", DATA_QUALITY_MIGRATION)
                    .isNotNull();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    crc32.update(line.getBytes(StandardCharsets.UTF_8));
                }
            }
        }

        assertThat((int) crc32.getValue())
                .as("Do not edit an already-applied Flyway migration; add a new migration instead")
                .isEqualTo(EXPECTED_CHECKSUM);
    }
}
