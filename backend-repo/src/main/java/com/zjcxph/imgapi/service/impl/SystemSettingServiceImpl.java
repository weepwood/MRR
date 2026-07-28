package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.entity.SystemSetting;
import com.zjcxph.imgapi.exception.BusinessException;
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

    private static final String OCR_ENABLED = "ocrEnabled";
    private static final String OCR_PROFILE = "ocrProfile";
    private static final String OCR_LANGUAGES = "ocrLanguages";
    private static final String OCR_MAX_CONCURRENCY = "ocrMaxConcurrency";
    private static final String OCR_PAGE_TIMEOUT_SECONDS = "ocrPageTimeoutSeconds";
    private static final String OCR_MAX_OUTPUT_BYTES = "ocrMaxOutputBytes";
    private static final String OCR_AUTO_PROCESS_NEW_SCANS = "ocrAutoProcessNewScans";
    private static final String OCR_LOW_CONFIDENCE_THRESHOLD = "ocrLowConfidenceThreshold";
    private static final String CLASSIFICATION_BATCH_REVIEW_THRESHOLD = "classificationBatchReviewThreshold";
    private static final double MIN_BATCH_REVIEW_THRESHOLD = 0.90D;

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
        validateRuntimeSettings(Map.of(key, value == null ? "" : value));
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

        validateBoolean(settings, OCR_ENABLED, "是否启用 OCR");
        validateBoolean(settings, OCR_AUTO_PROCESS_NEW_SCANS, "新扫描自动 OCR");
        validateOcrProfile(settings.get(OCR_PROFILE));
        validateOcrLanguages(settings.get(OCR_LANGUAGES));
        validateInteger(settings, OCR_MAX_CONCURRENCY, 1, 4, "OCR 最大并发数");
        validateInteger(settings, OCR_PAGE_TIMEOUT_SECONDS, 5, 300, "OCR 单页超时");
        validateInteger(settings, OCR_MAX_OUTPUT_BYTES, 65_536, 16_777_216, "OCR 最大输出大小");
        validateDecimal(settings, OCR_LOW_CONFIDENCE_THRESHOLD, 0D, 1D, "OCR 低质量阈值");
        validateDecimal(settings, CLASSIFICATION_BATCH_REVIEW_THRESHOLD,
                MIN_BATCH_REVIEW_THRESHOLD, 1D, "批量确认阈值");

        boolean effectiveEnabled = settings.containsKey(OCR_ENABLED)
                ? parseBoolean(settings.get(OCR_ENABLED))
                : parseStoredBoolean(getSetting(OCR_ENABLED));
        String effectiveProfile = normalize(settings.containsKey(OCR_PROFILE)
                ? settings.get(OCR_PROFILE)
                : getSetting(OCR_PROFILE));
        if (effectiveEnabled && effectiveProfile.isEmpty()) {
            throw new BusinessException(400, "启用 OCR 前必须选择服务端白名单配置");
        }
    }

    private void validateBoolean(Map<String, String> settings, String key, String label) {
        if (!settings.containsKey(key)) {
            return;
        }
        String normalized = normalize(settings.get(key));
        if (!"true".equalsIgnoreCase(normalized) && !"false".equalsIgnoreCase(normalized)) {
            throw new BusinessException(400, label + "必须是 true 或 false");
        }
    }

    private boolean parseBoolean(String value) {
        return "true".equalsIgnoreCase(normalize(value));
    }

    private boolean parseStoredBoolean(String value) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) {
            return false;
        }
        if (!"true".equalsIgnoreCase(normalized) && !"false".equalsIgnoreCase(normalized)) {
            logger.warn("数据库中的 OCR 开关值无效，按关闭处理: {}", normalized);
            return false;
        }
        return Boolean.parseBoolean(normalized);
    }

    private void validateOcrProfile(String value) {
        if (value == null) {
            return;
        }
        String normalized = normalize(value);
        if (!normalized.isEmpty() && !normalized.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new BusinessException(400, "OCR 配置名称仅允许字母、数字、点、下划线和短横线");
        }
    }

    private void validateOcrLanguages(String value) {
        if (value == null) {
            return;
        }
        String normalized = normalize(value);
        if (normalized.isEmpty() || !normalized.matches("[A-Za-z0-9_+,-]{1,64}")) {
            throw new BusinessException(400, "OCR 识别语言格式不正确");
        }
    }

    private void validateInteger(Map<String, String> settings,
                                 String key,
                                 int min,
                                 int max,
                                 String label) {
        if (!settings.containsKey(key)) {
            return;
        }
        try {
            int parsed = Integer.parseInt(normalize(settings.get(key)));
            if (parsed < min || parsed > max) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            throw new BusinessException(400, label + "必须在 " + min + " 到 " + max + " 之间");
        }
    }

    private void validateDecimal(Map<String, String> settings,
                                 String key,
                                 double min,
                                 double max,
                                 String label) {
        if (!settings.containsKey(key)) {
            return;
        }
        try {
            double parsed = Double.parseDouble(normalize(settings.get(key)));
            if (!Double.isFinite(parsed) || parsed < min || parsed > max) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            throw new BusinessException(400, label + "必须在 " + min + " 到 " + max + " 之间");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
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
