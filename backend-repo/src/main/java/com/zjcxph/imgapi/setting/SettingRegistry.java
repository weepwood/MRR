package com.zjcxph.imgapi.setting;

import com.zjcxph.imgapi.config.LogRetentionProperties;
import com.zjcxph.imgapi.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 可配置设置项注册表。
 * <p>
 * 这里是"哪些设置项可以被后台修改"的唯一权威清单，同时承载值域钳制。
 * 未注册的键不受注册表约束，保持既有行为，因此新增注册表不会影响
 * {@code mr_system_settings} 中已有的业务与外观设置键。
 * </p>
 *
 * <h3>关于默认值</h3>
 * 默认值以供应商形式声明，数据库留空时回落。对文件/环境变量可配的项
 * （如 {@code app.log-retention.*}），默认值取自启动时已注入的 Spring 属性对象，
 * 因此含义是"**数据库留空 = 沿用文件与环境变量**"，升级后行为不变。
 */
@Component
public class SettingRegistry {

    /** 安全策略分组：限流与会话，均为敏感项。 */
    public static final String GROUP_SECURITY = "security";
    /** 运维分组：日志留存与清理。 */
    public static final String GROUP_OPS = "ops";

    public static final String LOGIN_MAX_ATTEMPTS = "loginMaxAttempts";
    public static final String LOGIN_LOCK_WINDOW_MINUTES = "loginLockWindowMinutes";
    public static final String REGISTER_MAX_ATTEMPTS_PER_IP = "registerMaxAttemptsPerIp";
    public static final String REGISTER_WINDOW_SECONDS = "registerWindowSeconds";
    public static final String API_RATE_LIMIT_MAX_REQUESTS = "apiRateLimitMaxRequests";
    public static final String API_RATE_LIMIT_WINDOW_SECONDS = "apiRateLimitWindowSeconds";
    public static final String SESSION_EXPIRE_MINUTES = "sessionExpireMinutes";
    public static final String ACCESS_LOG_RETENTION_ENABLED = "accessLogRetentionEnabled";
    public static final String ACCESS_LOG_RETENTION_DAYS = "accessLogRetentionDays";
    public static final String ACCESS_LOG_CLEANUP_BATCH_SIZE = "accessLogCleanupBatchSize";
    public static final String ACCESS_LOG_CLEANUP_MAX_BATCHES = "accessLogCleanupMaxBatches";

    /** 登录失败上限的安全下限：低于此值会明显削弱暴力破解防护。 */
    public static final int MIN_LOGIN_MAX_ATTEMPTS = 3;
    /** 会话有效期的安全上限：24 小时。 */
    public static final int MAX_SESSION_EXPIRE_MINUTES = 24 * 60;
    /** 敏感接口限流的安全下限。 */
    public static final int MIN_API_RATE_LIMIT_MAX_REQUESTS = 10;

    private final Map<String, SettingDefinition> definitions;

    public SettingRegistry(LogRetentionProperties logRetentionProperties) {
        Map<String, SettingDefinition> registered = new LinkedHashMap<>();

        register(registered, SettingDefinition.integer(
                LOGIN_MAX_ATTEMPTS, GROUP_SECURITY, () -> "5", MIN_LOGIN_MAX_ATTEMPTS, 20, "次",
                "登录失败上限",
                "同一账号与 IP 在锁定窗口内允许的最大失败登录次数。低于 3 次会明显削弱暴力破解防护，因此不允许配置。",
                true));
        register(registered, SettingDefinition.integer(
                LOGIN_LOCK_WINDOW_MINUTES, GROUP_SECURITY, () -> "15", 5, 60, "分钟",
                "登录锁定窗口",
                "达到失败上限后，拒绝该账号与 IP 继续尝试登录的时间窗口。",
                true));
        register(registered, SettingDefinition.integer(
                REGISTER_MAX_ATTEMPTS_PER_IP, GROUP_SECURITY, () -> "3", 1, 20, "次",
                "注册频率上限",
                "同一 IP 在注册统计窗口内允许的注册请求次数（成功与失败均计入，用于防止用户名扫描）。",
                true));
        register(registered, SettingDefinition.integer(
                REGISTER_WINDOW_SECONDS, GROUP_SECURITY, () -> "60", 10, 600, "秒",
                "注册统计窗口",
                "注册频率的滑动窗口长度。",
                true));
        register(registered, SettingDefinition.integer(
                API_RATE_LIMIT_MAX_REQUESTS, GROUP_SECURITY,
                () -> "30", MIN_API_RATE_LIMIT_MAX_REQUESTS, 600, "次",
                "敏感接口限流上限",
                "同一 IP 在限流窗口内允许调用登录、检索、导出等敏感接口的总次数。低于 10 次会误伤正常业务。",
                true));
        register(registered, SettingDefinition.integer(
                API_RATE_LIMIT_WINDOW_SECONDS, GROUP_SECURITY, () -> "60", 10, 600, "秒",
                "敏感接口限流窗口",
                "敏感接口限流的滑动窗口长度。",
                true));
        register(registered, SettingDefinition.integer(
                SESSION_EXPIRE_MINUTES, GROUP_SECURITY, () -> "1440", 60, MAX_SESSION_EXPIRE_MINUTES, "分钟",
                "登录会话有效期",
                "仅影响此后新签发的登录令牌；已签发的令牌仍按原有效期有效，不会被提前失效。上限 24 小时。",
                true));

        register(registered, SettingDefinition.bool(
                ACCESS_LOG_RETENTION_ENABLED, GROUP_OPS,
                () -> String.valueOf(logRetentionProperties.isEnabled()),
                "访问日志自动清理",
                "关闭后访问日志将不再自动删除，可能长期保留包含敏感字段的历史记录，请谨慎操作。",
                true));
        register(registered, SettingDefinition.integer(
                ACCESS_LOG_RETENTION_DAYS, GROUP_OPS,
                () -> String.valueOf(logRetentionProperties.getRetentionDays()), 7, 3650, "天",
                "访问日志保留天数",
                "超过该天数的访问日志会被定时任务删除。缩短留存是降低敏感信息长期暴露风险的直接手段。",
                true));
        register(registered, SettingDefinition.integer(
                ACCESS_LOG_CLEANUP_BATCH_SIZE, GROUP_OPS,
                () -> String.valueOf(logRetentionProperties.getBatchSize()), 100, 50_000, "条",
                "单批删除条数",
                "每批删除的日志行数。过大可能造成长时间锁表。",
                false));
        register(registered, SettingDefinition.integer(
                ACCESS_LOG_CLEANUP_MAX_BATCHES, GROUP_OPS,
                () -> String.valueOf(logRetentionProperties.getMaxBatchesPerRun()), 1, 200, "批",
                "单次最多批次数",
                "单次清理任务的最大批次数。达到上限后剩余过期记录留待下次执行。",
                false));

        this.definitions = Collections.unmodifiableMap(registered);
    }

    private void register(Map<String, SettingDefinition> target, SettingDefinition definition) {
        if (target.put(definition.key(), definition) != null) {
            throw new IllegalStateException("重复注册的设置键: " + definition.key());
        }
    }

    /**
     * 查找设置项定义。
     */
    public Optional<SettingDefinition> find(String key) {
        return key == null ? Optional.empty() : Optional.ofNullable(definitions.get(key));
    }

    /**
     * 按分组顺序返回全部设置项定义。
     */
    public List<SettingDefinition> all() {
        return List.copyOf(definitions.values());
    }

    /**
     * 校验一个设置值。
     * <p>
     * 未注册的键直接放行（保持既有设置键的行为）；空值表示"回落默认"，同样放行。
     * </p>
     *
     * @throws BusinessException 值不符合类型或超出安全值域
     */
    public void validate(String key, String value) {
        SettingDefinition definition = definitions.get(key);
        if (definition == null) {
            return;
        }
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return;
        }

        switch (definition.type()) {
            case BOOLEAN -> validateBoolean(definition, normalized);
            case INTEGER -> validateInteger(definition, normalized);
            case DECIMAL -> validateDecimal(definition, normalized);
            case ENUM -> validateEnum(definition, normalized);
            case TEXT -> validateText(definition, normalized);
        }
    }

    private void validateBoolean(SettingDefinition definition, String value) {
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new BusinessException(400, definition.label() + "必须是 true 或 false");
        }
    }

    private void validateInteger(SettingDefinition definition, String value) {
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(400, definition.label() + "必须是整数");
        }
        if (definition.min() != null && parsed < definition.min()) {
            throw new BusinessException(400, rangeMessage(definition));
        }
        if (definition.max() != null && parsed > definition.max()) {
            throw new BusinessException(400, rangeMessage(definition));
        }
    }

    private void validateDecimal(SettingDefinition definition, String value) {
        double parsed;
        try {
            parsed = Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(400, definition.label() + "必须是数字");
        }
        if (!Double.isFinite(parsed)) {
            throw new BusinessException(400, definition.label() + "必须是有限数字");
        }
    }

    private void validateEnum(SettingDefinition definition, String value) {
        if (!definition.allowedValues().contains(value)) {
            throw new BusinessException(400,
                    definition.label() + "只能是: " + String.join(" / ", definition.allowedValues()));
        }
    }

    private void validateText(SettingDefinition definition, String value) {
        if (definition.max() != null && value.length() > definition.max()) {
            throw new BusinessException(400,
                    definition.label() + "长度不得超过 " + definition.max() + " 个字符");
        }
    }

    private String rangeMessage(SettingDefinition definition) {
        String unit = definition.unit() == null || definition.unit().isBlank() ? "" : " " + definition.unit();
        return definition.label() + "必须在 " + definition.min() + " 到 " + definition.max() + unit + " 之间";
    }
}
