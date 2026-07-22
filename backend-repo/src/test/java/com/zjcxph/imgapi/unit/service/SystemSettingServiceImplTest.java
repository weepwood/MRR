package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.mapper.SystemSettingMapper;
import com.zjcxph.imgapi.service.DeveloperApiAccessService;
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

    @Mock private SystemSettingMapper mapper;
    @Mock private DeveloperModeService developerModeService;
    @Mock private DeveloperApiAccessService developerApiAccessService;
    private SystemSettingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SystemSettingServiceImpl(mapper, developerModeService, developerApiAccessService);
    }

    @Test
    void saveSettings_shouldUpsertAll() {
        when(mapper.upsertAll(anyList())).thenReturn(2);
        service.saveSettings(Map.of("k1", "v1", "k2", "v2"), "admin");
        verify(mapper).upsertAll(anyList());
    }

    @Test
    void saveSettings_shouldRefreshDeveloperModeAndApiAccess() {
        Map<String, String> settings = Map.of(
                DeveloperModeService.SETTING_KEY, "true",
                DeveloperModeService.ALLOWED_SOURCES_SETTING_KEY, "192.168.10.0/24",
                DeveloperApiAccessService.SETTING_KEY, "true"
        );
        when(mapper.upsertAll(anyList())).thenReturn(3);
        when(developerModeService.isEnabled()).thenReturn(true);

        service.saveSettings(settings, "admin");

        verify(developerModeService).refreshFromValue("true");
        verify(developerModeService).refreshAllowedSourcesFromValue("192.168.10.0/24");
        verify(developerApiAccessService).refreshFromValue("true");
    }

    @Test
    void saveSettings_shouldDisableApiAccessWhenDeveloperModeIsOff() {
        when(mapper.upsertAll(anyList())).thenReturn(1);
        when(developerModeService.isEnabled()).thenReturn(false);
        service.saveSettings(Map.of(DeveloperModeService.SETTING_KEY, "false"), "admin");
        verify(developerApiAccessService).disableImmediately();
    }

    @Test
    void setSetting_shouldUpsert() {
        when(mapper.upsert(any())).thenReturn(1);
        service.setSetting("maxFileSize", "20", "admin");
        verify(mapper).upsert(any());
    }

    @Test
    void setSetting_shouldRefreshDeveloperAllowedSources() {
        when(mapper.upsert(any())).thenReturn(1);
        service.setSetting(DeveloperModeService.ALLOWED_SOURCES_SETTING_KEY, "10.20.0.0/16", "admin");
        verify(developerModeService).refreshAllowedSourcesFromValue("10.20.0.0/16");
    }

    @Test
    void setSetting_shouldRefreshDeveloperApiAccess() {
        when(mapper.upsert(any())).thenReturn(1);
        service.setSetting(DeveloperApiAccessService.SETTING_KEY, "true", "admin");
        verify(developerApiAccessService).refreshFromValue("true");
    }

    @Test
    void getAllSettings_shouldReturnMap() {
        when(mapper.findAll()).thenReturn(List.of());
        assertThat(service.getAllSettings()).isEmpty();
        var e1 = new com.zjcxph.imgapi.entity.SystemSetting("k1", "v1", "desc1");
        var e2 = new com.zjcxph.imgapi.entity.SystemSetting("k2", "v2", "desc2");
        when(mapper.findAll()).thenReturn(List.of(e1, e2));
        assertThat(service.getAllSettings()).containsEntry("k1", "v1").containsEntry("k2", "v2");
    }

    @Test
    void getSetting_shouldReturnValue() {
        when(mapper.findByKey("exists")).thenReturn(
                new com.zjcxph.imgapi.entity.SystemSetting("exists", "value", "desc"));
        when(mapper.findByKey("missing")).thenReturn(null);
        assertThat(service.getSetting("exists")).isEqualTo("value");
        assertThat(service.getSetting("missing")).isNull();
    }

    @Test
    void deleteSetting_shouldDelegate() {
        when(mapper.deleteByKey("old.key")).thenReturn(1);
        service.deleteSetting("old.key");
        verify(mapper).deleteByKey("old.key");
    }
}
