package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.mapper.SystemSettingMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

/**
 * 旧版开发者模式兼容服务。
 *
 * <p>开发者模式曾允许无 Token 请求获得虚拟管理员会话，并放宽跨域策略。
 * 该能力不能由数据库运行时设置控制，现已永久按关闭处理。保留本服务仅用于
 * 兼容已有依赖和公共状态接口，后续版本可在完成前端入口清理后删除。</p>
 */
@Service
public class DeveloperModeService {

    public static final String SETTING_KEY = "developerModeEnabled";

    private static final Logger logger = LoggerFactory.getLogger(DeveloperModeService.class);
    private static final Set<String> TRUE_VALUES = Set.of("true", "1", "yes", "on", "enabled");

    public DeveloperModeService(SystemSettingMapper ignoredSystemSettingMapper) {
        // 保留构造参数以兼容现有 Spring 装配和单元测试；不再读取数据库安全开关。
    }

    /**
     * 旧版开发者模式始终关闭。
     */
    public boolean isEnabled() {
        return false;
    }

    /**
     * 运行时设置不再能够启用认证旁路。
     */
    public void refreshFromValue(String value) {
        if (parseEnabled(value)) {
            logger.warn("忽略已停用的开发者模式启用请求；该设置不再影响认证或跨域策略");
        }
    }

    public void disableImmediately() {
        // 已永久关闭，无需维护运行时状态。
    }

    public void invalidate() {
        // 已永久关闭，无需维护运行时缓存。
    }

    static boolean parseEnabled(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return TRUE_VALUES.contains(value.trim().toLowerCase(Locale.ROOT));
    }
}
