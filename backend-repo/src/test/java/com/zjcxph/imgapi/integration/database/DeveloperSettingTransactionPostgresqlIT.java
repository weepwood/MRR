package com.zjcxph.imgapi.integration.database;

import com.zjcxph.imgapi.config.ImageDeliveryProperties;
import com.zjcxph.imgapi.config.ImageProperties;
import com.zjcxph.imgapi.config.LogRetentionProperties;
import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.mapper.SystemSettingMapper;
import com.zjcxph.imgapi.service.DeveloperApiAccessService;
import com.zjcxph.imgapi.service.DeveloperModeService;
import com.zjcxph.imgapi.service.SystemSettingService;
import com.zjcxph.imgapi.service.impl.SystemSettingServiceImpl;
import com.zjcxph.imgapi.setting.SettingRegistry;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest(properties = "mybatis.configuration.map-underscore-to-camel-case=true")
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({SystemSettingServiceImpl.class, DeveloperSettingTransactionPostgresqlIT.Dependencies.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DeveloperSettingTransactionPostgresqlIT extends PostgresqlIntegrationTestSupport {
    @Autowired SystemSettingService settings;
    @Autowired DeveloperModeService mode;
    @Autowired DeveloperApiAccessService api;
    @Autowired PlatformTransactionManager manager;

    @TestConfiguration
    static class Dependencies {
        @Bean SettingRegistry registry() { return new SettingRegistry(new LogRetentionProperties()); }
        @Bean ImageProperties imageProperties() { return new ImageProperties(); }
        @Bean DeveloperModeService developerModeService(SystemSettingMapper mapper) {
            return new DeveloperModeService(mapper, true, "127.0.0.1,::1", new ImageDeliveryProperties());
        }
        @Bean DeveloperApiAccessService developerApiAccessService(SystemSettingMapper mapper, DeveloperModeService mode) {
            return new DeveloperApiAccessService(mapper, mode);
        }
    }

    @Test
    void rollbackAndCommitDoNotPublishUncommittedSettingsToAnotherThread() throws Exception {
        assertThat(AopUtils.isAopProxy(settings)).isTrue();
        var tx = new TransactionTemplate(manager);
        var disabled = Map.of(DeveloperModeService.SETTING_KEY, "false", DeveloperApiAccessService.SETTING_KEY, "false");
        var enabled = Map.of(DeveloperModeService.SETTING_KEY, "true", DeveloperApiAccessService.SETTING_KEY, "true");
        var reader = Executors.newSingleThreadExecutor();
        try {
            settings.saveSettings(disabled, "synthetic-test");
            assertThat(reader.submit(api::isEnabled).get(5, TimeUnit.SECONDS)).isFalse();
            tx.executeWithoutResult(status -> {
                settings.saveSettings(enabled, "synthetic-test");
                try {
                    assertThat(reader.submit(api::isEnabled).get(5, TimeUnit.SECONDS)).isFalse();
                } catch (Exception e) { throw new AssertionError(e); }
                status.setRollbackOnly();
            });
            assertThat(settings.getSetting(DeveloperApiAccessService.SETTING_KEY)).isEqualTo("false");
            assertThat(reader.submit(api::isEnabled).get(5, TimeUnit.SECONDS)).isFalse();

            tx.executeWithoutResult(status -> {
                settings.saveSettings(enabled, "synthetic-test");
                try {
                    assertThat(reader.submit(api::isEnabled).get(5, TimeUnit.SECONDS)).isFalse();
                } catch (Exception e) { throw new AssertionError(e); }
            });
            assertThat(reader.submit(api::isEnabled).get(5, TimeUnit.SECONDS)).isTrue();
            tx.executeWithoutResult(status -> {
                settings.deleteSetting(DeveloperApiAccessService.SETTING_KEY);
                status.setRollbackOnly();
            });
            assertThat(reader.submit(api::isEnabled).get(5, TimeUnit.SECONDS)).isTrue();
            settings.deleteSetting(DeveloperApiAccessService.SETTING_KEY);
            assertThat(reader.submit(api::isEnabled).get(5, TimeUnit.SECONDS)).isFalse();
            settings.setSetting(DeveloperApiAccessService.SETTING_KEY, "true", "synthetic-test");
            assertThat(reader.submit(api::isEnabled).get(5, TimeUnit.SECONDS)).isTrue();
            settings.setSetting(DeveloperModeService.SETTING_KEY, "false", "synthetic-test");
            assertThat(reader.submit(api::isEnabled).get(5, TimeUnit.SECONDS)).isFalse();
        } finally {
            reader.shutdownNow();
            settings.saveSettings(disabled, "synthetic-test");
        }
    }
}
