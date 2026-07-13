package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.entity.SystemSetting;
import com.zjcxph.imgapi.mapper.SystemSettingMapper;
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

/**
 * 系统设置服务实现。
 * <p>
 * 读取时将所有设置项转换为 Map 返回，写入时支持批量 UPSERT。
 * 所有写操作记录设置来源（当前登录用户名或 dev 默认值）。
 * </p>
 */
@Service
public class SystemSettingServiceImpl implements SystemSettingService {

    private static final Logger logger = LoggerFactory.getLogger(SystemSettingServiceImpl.class);

    private final SystemSettingMapper systemSettingMapper;

    public SystemSettingServiceImpl(SystemSettingMapper systemSettingMapper) {
        this.systemSettingMapper = systemSettingMapper;
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
        String operator = resolveOperator(updatedBy);
        List<SystemSetting> entities = settings.entrySet().stream()
                .map(e -> new SystemSetting(e.getKey(), e.getValue(), null))
                .peek(s -> s.setUpdatedBy(operator))
                .toList();
        systemSettingMapper.upsertAll(entities);
        logger.info("系统设置已批量保存: {} 项, 操作者: {}", entities.size(), operator);
    }

    @Override
    public void setSetting(String key, String value, String updatedBy) {
        String operator = resolveOperator(updatedBy);
        SystemSetting setting = new SystemSetting(key, value, null);
        setting.setUpdatedBy(operator);
        systemSettingMapper.upsert(setting);
        logger.info("系统设置已更新: {} = ..., 操作者: {}", key, operator);
    }

    @Override
    public void deleteSetting(String key) {
        systemSettingMapper.deleteByKey(key);
        logger.info("系统设置已删除: {}", key);
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
