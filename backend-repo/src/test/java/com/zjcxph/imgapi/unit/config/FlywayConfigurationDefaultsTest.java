package com.zjcxph.imgapi.unit.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Flyway 配置边界测试")
class FlywayConfigurationDefaultsTest {

    @Test
    @DisplayName("生产默认禁止乱序并且不忽略缺失或未来迁移")
    void productionDefaultsAreStrict() throws IOException {
        Properties properties = load("/application.properties");

        assertThat(properties.getProperty("spring.flyway.out-of-order"))
                .isEqualTo("${SPRING_FLYWAY_OUT_OF_ORDER:false}");
        assertThat(properties.getProperty("spring.flyway.ignore-migration-patterns"))
                .isEqualTo("${SPRING_FLYWAY_IGNORE_MIGRATION_PATTERNS:}");
        assertThat(properties.getProperty("spring.flyway.validate-on-migrate")).isEqualTo("true");
        assertThat(properties.getProperty("spring.flyway.validate-migration-naming")).isEqualTo("true");
    }

    @Test
    @DisplayName("历史库兼容只在本地模板中显式开启")
    void localTemplateOptsIntoCompatibility() throws IOException {
        Properties properties = load("/application-local.template.properties");

        assertThat(properties.getProperty("spring.flyway.out-of-order")).isEqualTo("true");
        assertThat(properties.getProperty("spring.flyway.ignore-migration-patterns"))
                .isEqualTo("*:missing,*:future");
    }

    private Properties load(String resource) throws IOException {
        try (InputStream input = FlywayConfigurationDefaultsTest.class.getResourceAsStream(resource)) {
            assertThat(input).as("配置资源 %s 应存在", resource).isNotNull();
            Properties properties = new Properties();
            properties.load(input);
            return properties;
        }
    }
}
