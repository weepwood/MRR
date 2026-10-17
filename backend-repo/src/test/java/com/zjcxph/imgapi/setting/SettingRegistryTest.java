package com.zjcxph.imgapi.setting;

import com.zjcxph.imgapi.config.LogRetentionProperties;
import com.zjcxph.imgapi.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SettingRegistry 可配置项注册表")
class SettingRegistryTest {

    private SettingRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SettingRegistry(new LogRetentionProperties());
    }

    @Test
    @DisplayName("注册表包含安全策略与运维两组共 11 个设置项")
    void shouldRegisterExpectedKeys() {
        Map<String, SettingDefinition> byKey = registry.all().stream()
                .collect(Collectors.toMap(SettingDefinition::key, Function.identity()));

        assertThat(byKey).containsKeys(
                SettingRegistry.LOGIN_MAX_ATTEMPTS,
                SettingRegistry.LOGIN_LOCK_WINDOW_MINUTES,
                SettingRegistry.REGISTER_MAX_ATTEMPTS_PER_IP,
                SettingRegistry.REGISTER_WINDOW_SECONDS,
                SettingRegistry.API_RATE_LIMIT_MAX_REQUESTS,
                SettingRegistry.API_RATE_LIMIT_WINDOW_SECONDS,
                SettingRegistry.SESSION_EXPIRE_MINUTES,
                SettingRegistry.ACCESS_LOG_RETENTION_ENABLED,
                SettingRegistry.ACCESS_LOG_RETENTION_DAYS,
                SettingRegistry.ACCESS_LOG_CLEANUP_BATCH_SIZE,
                SettingRegistry.ACCESS_LOG_CLEANUP_MAX_BATCHES);
        assertThat(registry.all()).hasSize(11);
    }

    @Test
    @DisplayName("未注册的键不受约束，保持既有设置键行为")
    void shouldNotValidateUnregisteredKeys() {
        assertThatCode(() -> registry.validate("systemName", "任意内容"))
                .doesNotThrowAnyException();
        assertThatCode(() -> registry.validate("ocrMaxConcurrency", "0"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("空值表示回落默认，应放行")
    void shouldAllowBlankValueAsInherit() {
        assertThatCode(() -> registry.validate(SettingRegistry.LOGIN_MAX_ATTEMPTS, ""))
                .doesNotThrowAnyException();
        assertThatCode(() -> registry.validate(SettingRegistry.LOGIN_MAX_ATTEMPTS, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("登录失败上限不得低于 3 次，防止削弱暴力破解防护")
    void shouldRejectLoginAttemptsBelowSafetyFloor() {
        assertThatThrownBy(() -> registry.validate(SettingRegistry.LOGIN_MAX_ATTEMPTS, "2"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("登录失败上限");
        assertThatCode(() -> registry.validate(SettingRegistry.LOGIN_MAX_ATTEMPTS, "3"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> registry.validate(SettingRegistry.LOGIN_MAX_ATTEMPTS, "21"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("敏感接口限流不得低于 10 次，避免误伤正常业务")
    void shouldRejectApiRateLimitBelowSafetyFloor() {
        assertThatThrownBy(() -> registry.validate(SettingRegistry.API_RATE_LIMIT_MAX_REQUESTS, "9"))
                .isInstanceOf(BusinessException.class);
        assertThatCode(() -> registry.validate(SettingRegistry.API_RATE_LIMIT_MAX_REQUESTS, "10"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("会话有效期上限为 24 小时")
    void shouldCapSessionExpireTo24Hours() {
        assertThatCode(() -> registry.validate(SettingRegistry.SESSION_EXPIRE_MINUTES, "1440"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> registry.validate(SettingRegistry.SESSION_EXPIRE_MINUTES, "1441"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("登录会话有效期");
        assertThatThrownBy(() -> registry.validate(SettingRegistry.SESSION_EXPIRE_MINUTES, "59"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("非法类型应被拒绝")
    void shouldRejectWrongTypes() {
        assertThatThrownBy(() -> registry.validate(SettingRegistry.LOGIN_MAX_ATTEMPTS, "abc"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须是整数");
        assertThatThrownBy(() -> registry.validate(SettingRegistry.ACCESS_LOG_RETENTION_ENABLED, "yes"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须是 true 或 false");
    }

    @Test
    @DisplayName("日志保留天数有 7 到 3650 天的区间约束")
    void shouldBoundLogRetentionDays() {
        assertThatThrownBy(() -> registry.validate(SettingRegistry.ACCESS_LOG_RETENTION_DAYS, "6"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> registry.validate(SettingRegistry.ACCESS_LOG_RETENTION_DAYS, "3651"))
                .isInstanceOf(BusinessException.class);
        assertThatCode(() -> registry.validate(SettingRegistry.ACCESS_LOG_RETENTION_DAYS, "180"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("默认值来自启动时注入的日志留存属性，保证环境变量仍然有效")
    void shouldTakeLogRetentionDefaultsFromProperties() {
        LogRetentionProperties properties = new LogRetentionProperties();
        properties.setEnabled(true);
        properties.setRetentionDays(365);
        properties.setBatchSize(1000);
        properties.setMaxBatchesPerRun(5);

        SettingRegistry custom = new SettingRegistry(properties);

        assertThat(custom.find(SettingRegistry.ACCESS_LOG_RETENTION_ENABLED).orElseThrow().defaultRawValue())
                .isEqualTo("true");
        assertThat(custom.find(SettingRegistry.ACCESS_LOG_RETENTION_DAYS).orElseThrow().defaultRawValue())
                .isEqualTo("365");
        assertThat(custom.find(SettingRegistry.ACCESS_LOG_CLEANUP_BATCH_SIZE).orElseThrow().defaultRawValue())
                .isEqualTo("1000");
        assertThat(custom.find(SettingRegistry.ACCESS_LOG_CLEANUP_MAX_BATCHES).orElseThrow().defaultRawValue())
                .isEqualTo("5");
    }

    @Test
    @DisplayName("安全策略项全部标记为敏感，运维批大小类项不敏感")
    void shouldMarkSensitiveItems() {
        assertThat(registry.find(SettingRegistry.LOGIN_MAX_ATTEMPTS).orElseThrow().sensitive()).isTrue();
        assertThat(registry.find(SettingRegistry.SESSION_EXPIRE_MINUTES).orElseThrow().sensitive()).isTrue();
        assertThat(registry.find(SettingRegistry.ACCESS_LOG_RETENTION_DAYS).orElseThrow().sensitive()).isTrue();
        assertThat(registry.find(SettingRegistry.ACCESS_LOG_CLEANUP_BATCH_SIZE).orElseThrow().sensitive()).isFalse();
    }

    @Test
    @DisplayName("全部设置项均为立即生效")
    void shouldBeImmediateEffect() {
        assertThat(registry.all())
                .allSatisfy(definition -> assertThat(definition.effect()).isEqualTo(SettingEffect.IMMEDIATE));
    }
}
