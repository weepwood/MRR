package com.zjcxph.imgapi.setting;

import com.zjcxph.imgapi.entity.SystemSetting;
import com.zjcxph.imgapi.mapper.SystemSettingMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 运行参数读取入口 —— 数据库覆盖层。
 * <p>
 * 解析顺序为 {@code 数据库显式值 → 注册表默认值}，其中"注册表默认值"对文件/环境变量可配的项
 * 又回落自 Spring 属性对象。因此：
 * </p>
 * <ul>
 *     <li>数据库留空（或键不存在）时，配置完全由文件与环境变量决定，升级后行为不变；</li>
 *     <li>只有管理员在后台显式填写后，数据库值才覆盖文件值。</li>
 * </ul>
 * <p>
 * 与 {@code ArchiveAccessService}、{@code ImageUrlService} 的既有做法一致，这里**不做本地缓存**：
 * 每次调用都重新读取，因此后台保存后立即影响后续请求，不需要重启或刷新机制。
 * 代价是每次读取有一次按主键的数据库查询；这些调用点都位于登录、限流与定时清理等
 * 低频路径上，可以接受。
 * </p>
 */
@Component
public class RuntimeSettingStore {

    private static final Logger logger = LoggerFactory.getLogger(RuntimeSettingStore.class);

    private final SystemSettingMapper systemSettingMapper;
    private final SettingRegistry registry;

    public RuntimeSettingStore(SystemSettingMapper systemSettingMapper, SettingRegistry registry) {
        this.systemSettingMapper = systemSettingMapper;
        this.registry = registry;
    }

    /**
     * 解析设置项的生效原始值（数据库优先，回落默认）。
     */
    public String resolveRaw(String key) {
        String override = readOverride(key);
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        return registry.find(key).map(SettingDefinition::defaultRawValue).orElse("");
    }

    /**
     * 读取数据库中的显式覆盖值，未配置时返回 null。
     */
    public String readOverride(String key) {
        try {
            SystemSetting setting = systemSettingMapper.findByKey(key);
            return setting == null ? null : setting.getSettingValue();
        } catch (Exception exception) {
            // 配置读取失败不应成为新的故障入口：记录后回落默认值。
            logger.warn("读取设置项失败，将回落到默认值: key={}, reason={}", key, exception.getMessage());
            return null;
        }
    }

    /**
     * 以整数读取设置项，越界或非法值时回落到默认值。
     * <p>
     * 后台写入已经过 {@link SettingRegistry#validate} 钳制，此处再做一次兜底，
     * 用于防御直接改库等绕过校验的情形。
     * </p>
     */
    public int intValue(String key) {
        SettingDefinition definition = registry.find(key).orElse(null);
        String raw = resolveRaw(key);
        int parsed;
        try {
            parsed = Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            parsed = parseDefaultInt(definition);
            logger.warn("设置项 {} 的值不是整数，按默认值 {} 处理", key, parsed);
            return parsed;
        }
        if (definition != null) {
            if (definition.min() != null && parsed < definition.min()) {
                logger.warn("设置项 {} 的值 {} 低于下界 {}，按默认值处理", key, parsed, definition.min());
                return parseDefaultInt(definition);
            }
            if (definition.max() != null && parsed > definition.max()) {
                logger.warn("设置项 {} 的值 {} 高于上界 {}，按默认值处理", key, parsed, definition.max());
                return parseDefaultInt(definition);
            }
        }
        return parsed;
    }

    /**
     * 以长整数读取设置项。
     */
    public long longValue(String key) {
        return intValue(key);
    }

    /**
     * 以布尔读取设置项，无法识别时回落到默认值。
     */
    public boolean booleanValue(String key) {
        String raw = resolveRaw(key);
        if ("true".equalsIgnoreCase(raw)) {
            return true;
        }
        if ("false".equalsIgnoreCase(raw)) {
            return false;
        }
        boolean fallback = Boolean.parseBoolean(
                registry.find(key).map(SettingDefinition::defaultRawValue).orElse("false"));
        logger.warn("设置项 {} 的值无法识别为布尔，按 {} 处理", key, fallback);
        return fallback;
    }

    private int parseDefaultInt(SettingDefinition definition) {
        if (definition == null) {
            return 0;
        }
        try {
            return Integer.parseInt(definition.defaultRawValue());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}
