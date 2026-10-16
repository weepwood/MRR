package com.zjcxph.imgapi.setting;

import com.zjcxph.imgapi.config.LogRetentionProperties;
import com.zjcxph.imgapi.entity.SystemSetting;
import com.zjcxph.imgapi.mapper.SystemSettingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("RuntimeSettingStore 运行参数覆盖层")
class RuntimeSettingStoreTest {

    private SystemSettingMapper mapper;
    private RuntimeSettingStore store;

    @BeforeEach
    void setUp() {
        mapper = mock(SystemSettingMapper.class);
        store = new RuntimeSettingStore(mapper, new SettingRegistry(new LogRetentionProperties()));
    }

    private void givenStoredValue(String key, String value) {
        when(mapper.findByKey(key)).thenReturn(new SystemSetting(key, value, null));
    }

    @Test
    @DisplayName("数据库留空时回落代码默认值，保证升级后行为不变")
    void shouldFallBackToDefaultWhenNoOverride() {
        when(mapper.findByKey(anyString())).thenReturn(null);

        assertThat(store.intValue(SettingRegistry.LOGIN_MAX_ATTEMPTS)).isEqualTo(5);
        assertThat(store.intValue(SettingRegistry.LOGIN_LOCK_WINDOW_MINUTES)).isEqualTo(15);
        assertThat(store.intValue(SettingRegistry.REGISTER_MAX_ATTEMPTS_PER_IP)).isEqualTo(3);
        assertThat(store.intValue(SettingRegistry.REGISTER_WINDOW_SECONDS)).isEqualTo(60);
        assertThat(store.intValue(SettingRegistry.API_RATE_LIMIT_MAX_REQUESTS)).isEqualTo(30);
        assertThat(store.intValue(SettingRegistry.API_RATE_LIMIT_WINDOW_SECONDS)).isEqualTo(60);
        assertThat(store.intValue(SettingRegistry.SESSION_EXPIRE_MINUTES)).isEqualTo(1440);
        assertThat(store.booleanValue(SettingRegistry.ACCESS_LOG_RETENTION_ENABLED)).isFalse();
        assertThat(store.intValue(SettingRegistry.ACCESS_LOG_RETENTION_DAYS)).isEqualTo(1095);
        assertThat(store.intValue(SettingRegistry.ACCESS_LOG_CLEANUP_BATCH_SIZE)).isEqualTo(5000);
        assertThat(store.intValue(SettingRegistry.ACCESS_LOG_CLEANUP_MAX_BATCHES)).isEqualTo(20);
    }

    @Test
    @DisplayName("数据库显式值覆盖默认值")
    void shouldPreferStoredOverride() {
        givenStoredValue(SettingRegistry.LOGIN_MAX_ATTEMPTS, "8");
        givenStoredValue(SettingRegistry.SESSION_EXPIRE_MINUTES, "480");
        givenStoredValue(SettingRegistry.ACCESS_LOG_RETENTION_ENABLED, "true");

        assertThat(store.intValue(SettingRegistry.LOGIN_MAX_ATTEMPTS)).isEqualTo(8);
        assertThat(store.intValue(SettingRegistry.SESSION_EXPIRE_MINUTES)).isEqualTo(480);
        assertThat(store.booleanValue(SettingRegistry.ACCESS_LOG_RETENTION_ENABLED)).isTrue();
    }

    @Test
    @DisplayName("数据库中的空白值视为未配置")
    void shouldTreatBlankOverrideAsInherit() {
        givenStoredValue(SettingRegistry.LOGIN_MAX_ATTEMPTS, "   ");

        assertThat(store.intValue(SettingRegistry.LOGIN_MAX_ATTEMPTS)).isEqualTo(5);
        assertThat(store.readOverride(SettingRegistry.LOGIN_MAX_ATTEMPTS)).isEqualTo("   ");
    }

    @Test
    @DisplayName("直接改库写入越界值时兜底回落到默认，避免绕过后台校验")
    void shouldClampOutOfRangeStoredValue() {
        givenStoredValue(SettingRegistry.LOGIN_MAX_ATTEMPTS, "1");
        givenStoredValue(SettingRegistry.SESSION_EXPIRE_MINUTES, "99999");
        givenStoredValue(SettingRegistry.API_RATE_LIMIT_MAX_REQUESTS, "abc");

        assertThat(store.intValue(SettingRegistry.LOGIN_MAX_ATTEMPTS)).isEqualTo(5);
        assertThat(store.intValue(SettingRegistry.SESSION_EXPIRE_MINUTES)).isEqualTo(1440);
        assertThat(store.intValue(SettingRegistry.API_RATE_LIMIT_MAX_REQUESTS)).isEqualTo(30);
    }

    @Test
    @DisplayName("读取设置失败时回落默认，配置读取不成为新的故障入口")
    void shouldFallBackWhenMapperThrows() {
        when(mapper.findByKey(anyString())).thenThrow(new IllegalStateException("database unavailable"));

        assertThat(store.intValue(SettingRegistry.LOGIN_MAX_ATTEMPTS)).isEqualTo(5);
        assertThat(store.booleanValue(SettingRegistry.ACCESS_LOG_RETENTION_ENABLED)).isFalse();
    }

    @Test
    @DisplayName("未注册的键也能读取数据库覆盖值")
    void shouldReadUnregisteredKey() {
        givenStoredValue("systemName", "MRR 测试系统");

        assertThat(store.resolveRaw("systemName")).isEqualTo("MRR 测试系统");
    }

    @Test
    @DisplayName("未注册且未配置的键解析为空串")
    void shouldResolveUnknownKeyAsEmpty() {
        when(mapper.findByKey(anyString())).thenReturn(null);

        assertThat(store.resolveRaw("notRegisteredKey")).isEmpty();
    }
}
