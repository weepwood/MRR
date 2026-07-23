package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.entity.SystemSetting;
import com.zjcxph.imgapi.mapper.SystemSettingMapper;
import com.zjcxph.imgapi.service.DeveloperApiAccessService;
import com.zjcxph.imgapi.service.DeveloperModeService;
import com.zjcxph.imgapi.service.SystemSettingService;
import com.zjcxph.imgapi.utils.AuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SystemSettingServiceImpl implements SystemSettingService {

    private static final Logger logger = LoggerFactory.getLogger(SystemSettingServiceImpl.class);

    private final SystemSettingMapper systemSettingMapper;
    private final DeveloperModeService developerModeService;
    private final DeveloperApiAccessService developerApiAccessService;

    public SystemSettingServiceImpl(SystemSettingMapper systemSettingMapper,
                                    DeveloperModeService developerModeService,
                                    DeveloperApiAccessService developerApiAccessService) {
        this.systemSettingMapper = systemSettingMapper;
        this.developerModeService = developerModeService;
        this.developerApiAccessService = developerApiAccessService;
    }

    @Override
    public Map<String, String> getAllSettings() {
        List<SystemSetting> settings = systemSettingMapper.findAll();
        return settings.stream()
                .filter(s -> s.getSettingKey() != null && !s.getSettingKey().isEmpty())
                .collect(Collectors.toMap(
                        SystemSetting::getSettingKey,
                        s -> s.getSettingValue() != null ? s.getSettingValue() : "",
                        (a, b) -> b,
                        LinkedHashMap::new
                ));
    }

    @Override
    public String getSetting(String key) {
        SystemSetting setting = systemSettingMapper.findByKey(key);
        return setting != null ? setting.getSettingValue() : null;
    }

    @Override
    @Transactional
    public void saveSettings(Map<String, String> settings, String updatedBy) {
        validateRuntimeSettings(settings);
        String operator = resolveOperator(updatedBy);
        List<SystemSetting> entities = settings.entrySet().stream()
                .map(e -> new SystemSetting(e.getKey(), e.getValue(), null))
                .peek(s -> s.setUpdatedBy(operator))
                .toList();
        systemSettingMapper.upsertAll(entities);
        refreshRuntimeSettings(settings);
        logger.info("系统设置已批量保存: {} 项, 操作者: {}", entities.size(), operator);
    }

    @Override
    public void setSetting(String key, String value, String updatedBy) {
        if (DeveloperModeService.ALLOWED_SOURCES_SETTING_KEY.equals(key)) {
            developerModeService.validateAllowedSourcesValue(value);
        }
        String operator = resolveOperator(updatedBy);
        SystemSetting setting = new SystemSetting(key, value, null);
        setting.setUpdatedBy(operator);
        systemSettingMapper.upsert(setting);
        if (DeveloperModeService.SETTING_KEY.equals(key)) {
            developerModeService.refreshFromValue(value);
            if (!developerModeService.isEnabled()) {
                developerApiAccessService.disableImmediately();
            }
        } else if (DeveloperModeService.ALLOWED_SOURCES_SETTING_KEY.equals(key)) {
            developerModeService.refreshAllowedSourcesFromValue(value);
        } else if (DeveloperApiAccessService.SETTING_KEY.equals(key)) {
            developerApiAccessService.refreshFromValue(value);
        }
        logger.info("系统设置已更新: {} = ..., 操作者: {}", key, operator);
    }

    @Override
    public void deleteSetting(String key) {
        systemSettingMapper.deleteByKey(key);
        if (DeveloperModeService.SETTING_KEY.equals(key)) {
            developerModeService.disableImmediately();
            developerApiAccessService.disableImmediately();
        } else if (DeveloperModeService.ALLOWED_SOURCES_SETTING_KEY.equals(key)) {
            developerModeService.refreshAllowedSourcesFromValue("");
        } else if (DeveloperApiAccessService.SETTING_KEY.equals(key)) {
            developerApiAccessService.disableImmediately();
        }
        logger.info("系统设置已删除: {}", key);
    }

    private void validateRuntimeSettings(Map<String, String> settings) {
        if (settings.containsKey(DeveloperModeService.ALLOWED_SOURCES_SETTING_KEY)) {
            developerModeService.validateAllowedSourcesValue(
                    settings.get(DeveloperModeService.ALLOWED_SOURCES_SETTING_KEY));
        }
    }

    private void refreshRuntimeSettings(Map<String, String> settings) {
        if (settings.containsKey(DeveloperModeService.SETTING_KEY)) {
            developerModeService.refreshFromValue(settings.get(DeveloperModeService.SETTING_KEY));
            if (!developerModeService.isEnabled()) {
                developerApiAccessService.disableImmediately();
            }
        }
        if (settings.containsKey(DeveloperModeService.ALLOWED_SOURCES_SETTING_KEY)) {
            developerModeService.refreshAllowedSourcesFromValue(
                    settings.get(DeveloperModeService.ALLOWED_SOURCES_SETTING_KEY));
        }
        if (settings.containsKey(DeveloperApiAccessService.SETTING_KEY)) {
            developerApiAccessService.refreshFromValue(
                    settings.get(DeveloperApiAccessService.SETTING_KEY));
        }
    }

    private String resolveOperator(String provided) {
        if (provided != null && !provided.isBlank()) {
            return provided;
        }
        try {
            var session = AuthContext.getCurrentUser();
            if (session != null && session.getUsername() != null) {
                return session.getUsername();
            }
        } catch (Exception ignored) {
            // auth context unavailable
        }
        return "system";
    }
}
