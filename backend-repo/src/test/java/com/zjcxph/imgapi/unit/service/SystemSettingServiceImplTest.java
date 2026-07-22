package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.mapper.SystemSettingMapper;
import com.zjcxph.imgapi.service.DeveloperModeService;
import com.zjcxph.imgapi.service.impl.SystemSettingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SystemSettingService 系统设置服务测试")
@ExtendWith(MockitoExtension.class)
class SystemSettingServiceImplTest {

    @Mock
    private SystemSettingMapper mapper;

    @Mock
    private DeveloperModeService developerModeService;

    private SystemSettingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SystemSettingServiceImpl(mapper, developerModeService);
    }

    @Test
    @DisplayName("saveSettings — 批量保存设置，调用 upsertAll")
    void saveSettings_shouldUpsertAll() {
        Map<String, String> settings = Map.of("k1", "v1", "k2", "v2");
        when(mapper.upsertAll(anyList())).thenReturn(2);

        service.saveSettings(settings, "admin");

        verify(mapper).upsertAll(anyList());
    }

    @Test
    @DisplayName("saveSettings — 开发者模式保存后立即刷新运行时设置")
    void saveSettings_shouldRefreshDeveloperMode() {
        Map<String, String> settings = Map.of(
                DeveloperModeService.SETTING_KEY, "true",
                DeveloperModeService.ALLOWED_SOURCES_SETTING_KEY, "192.168.10.0/24"
        );
        when(mapper.upsertAll(anyList())).thenReturn(2);

        service.saveSettings(settings, "admin");

        verify(developerModeService).refreshFromValue("true");
        verify(developerModeService).refreshAllowedSourcesFromValue("192.168.10.0/24");
    }

    @Test
    @DisplayName("setSetting — 单项设置走 upsert")
    void setSetting_shouldUpsert() {
        when(mapper.upsert(any())).thenReturn(1);

        service.setSetting("maxFileSize", "20", "admin");

        verify(mapper).upsert(any());
    }

    @Test
    @DisplayName("setSetting — 可信来源单项设置即时刷新")
    void setSetting_shouldRefreshDeveloperAllowedSources() {
        when(mapper.upsert(any())).thenReturn(1);

        service.setSetting(DeveloperModeService.ALLOWED_SOURCES_SETTING_KEY, "10.20.0.0/16", "admin");

        verify(developerModeService).refreshAllowedSourcesFromValue("10.20.0.0/16");
    }

    @Test
    @DisplayName("getAllSettings — 返回 key-value Map")
    void getAllSettings_shouldReturnMap() {
        when(mapper.findAll()).thenReturn(List.of());
        assertThat(service.getAllSettings()).isEmpty();

        var e1 = new com.zjcxph.imgapi.entity.SystemSetting("k1", "v1", "desc1");
        var e2 = new com.zjcxph.imgapi.entity.SystemSetting("k2", "v2", "desc2");
        when(mapper.findAll()).thenReturn(List.of(e1, e2));

        Map<String, String> all = service.getAllSettings();
        assertThat(all).hasSize(2).containsEntry("k1", "v1").containsEntry("k2", "v2");
    }

    @Test
    @DisplayName("getSetting — 返回单个值或 null")
    void getSetting_shouldReturnValue() {
        when(mapper.findByKey("exists")).thenReturn(
                new com.zjcxph.imgapi.entity.SystemSetting("exists", "value", "desc"));
        when(mapper.findByKey("missing")).thenReturn(null);

        assertThat(service.getSetting("exists")).isEqualTo("value");
        assertThat(service.getSetting("missing")).isNull();
    }

    @Test
    @DisplayName("deleteSetting — 委托 mapper 删除")
    void deleteSetting_shouldDelegate() {
        when(mapper.deleteByKey("old.key")).thenReturn(1);
        service.deleteSetting("old.key");
        verify(mapper).deleteByKey("old.key");
    }
}
