package com.zjcxph.imgapi.integration.mapper;

import com.zjcxph.imgapi.config.ImageProperties;
import com.zjcxph.imgapi.config.LogRetentionProperties;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.mapper.SystemSettingMapper;
import com.zjcxph.imgapi.service.*;
import com.zjcxph.imgapi.service.impl.SystemSettingServiceImpl;
import com.zjcxph.imgapi.setting.ImageDirectoryRoutingRules;
import com.zjcxph.imgapi.setting.SettingRegistry;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

@MybatisTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "mybatis.configuration.map-underscore-to-camel-case=true",
        "spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.minimum-idle=0"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ImageDirectoryRoutingPostgresqlIT extends PostgresqlIntegrationTestSupport {
    @Autowired private SystemSettingMapper mapper;

    @Test
    void actualSettingPersistenceRoutingAndDeleteRollbackUsePostgresql() {
        String key = ImageDirectoryRoutingRules.KEY;
        mapper.deleteByKey(key);
        ImageProperties properties = new ImageProperties();
        properties.setUrl("http://fallback.invalid");
        properties.setServerUrlBa01("http://ba01.invalid");
        properties.setServerUrlBa03("http://ba03.invalid");
        var settings = new SystemSettingServiceImpl(mapper, mock(DeveloperModeService.class),
                mock(DeveloperApiAccessService.class), new SettingRegistry(new LogRetentionProperties()), properties);
        var urls = new ImageUrlService(properties, mock(OssService.class), settings);
        Scan scan = new Scan();
        scan.setFolder("2026.06.05"); scan.setBah("123"); scan.setBrxh("42"); scan.setFilename("page.jpg");
        assertThat(urls.buildImageUrl(scan)).startsWith("http://ba03.invalid/");

        String override = "[{\"type\":\"exact\",\"value\":\"2026.06.05\",\"node\":\"ba01\"}]";
        settings.setSetting(key, override, "synthetic-admin");
        assertThat(mapper.findByKey(key).getUpdatedBy()).isEqualTo("synthetic-admin");
        assertThat(urls.buildImageUrl(scan)).startsWith("http://ba01.invalid/");
        assertThatThrownBy(() -> settings.saveSettings(Map.of(key, "[{}]", "routingSyntheticSetting", "changed"), "synthetic-admin"))
                .isInstanceOf(BusinessException.class);
        assertThat(mapper.findByKey(key).getSettingValue()).isEqualTo(override);
        assertThat(mapper.findByKey("routingSyntheticSetting")).isNull();

        settings.setSetting(key, "[]", "synthetic-admin");
        assertThat(urls.buildImageUrl(scan)).startsWith("http://fallback.invalid/");
        settings.deleteSetting(key);
        assertThat(mapper.findByKey(key)).isNull();
        assertThat(urls.buildImageUrl(scan)).startsWith("http://ba03.invalid/");
    }
}
