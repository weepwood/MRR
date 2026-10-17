package com.zjcxph.imgapi.setting;

import java.util.List;
import java.util.function.Supplier;

/**
 * 单个可配置设置项的声明式定义。
 * <p>
 * 引入本类型的目的：把"键存在性、类型、值域、生效方式、是否敏感"集中声明在一处，
 * 取代 {@code SystemSettingServiceImpl} 中逐项手写的 if 校验链，以及前端各面板中
 * 重复维护的默认值。新增一个可配置项只需要在 {@link SettingRegistry} 注册一条定义。
 * </p>
 *
 * @param key          设置键，同时是 {@code mr_system_settings.setting_key} 的值
 * @param group        前端分组（security / ops ...）
 * @param type         值类型
 * @param defaultValue 默认值提供者。数据库留空时回落此值，因此对"文件/环境变量可配"的项，
 *                     应由 Spring 属性对象提供，保证环境变量仍然有效
 * @param min          整数下界（含），非整数类型为 null
 * @param max          整数上界（含），非整数类型为 null
 * @param allowedValues 枚举可选值
 * @param unit         单位，仅用于前端展示
 * @param label        前端标签
 * @param description  前端说明文案
 * @param effect       生效方式
 * @param sensitive    是否敏感项。敏感项变更需要二次确认并进入运维审计
 */
public record SettingDefinition(
        String key,
        String group,
        SettingType type,
        Supplier<String> defaultValue,
        Integer min,
        Integer max,
        List<String> allowedValues,
        String unit,
        String label,
        String description,
        SettingEffect effect,
        boolean sensitive
) {

    /**
     * 声明一个整数设置项，立即生效。
     */
    public static SettingDefinition integer(String key,
                                            String group,
                                            Supplier<String> defaultValue,
                                            int min,
                                            int max,
                                            String unit,
                                            String label,
                                            String description,
                                            boolean sensitive) {
        return new SettingDefinition(key, group, SettingType.INTEGER, defaultValue, min, max,
                List.of(), unit, label, description, SettingEffect.IMMEDIATE, sensitive);
    }

    /**
     * 声明一个布尔设置项，立即生效。
     */
    public static SettingDefinition bool(String key,
                                         String group,
                                         Supplier<String> defaultValue,
                                         String label,
                                         String description,
                                         boolean sensitive) {
        return new SettingDefinition(key, group, SettingType.BOOLEAN, defaultValue, null, null,
                List.of(), "", label, description, SettingEffect.IMMEDIATE, sensitive);
    }

    /**
     * 解析默认值的原始字符串形式。供应商返回 null 时统一按空串处理。
     */
    public String defaultRawValue() {
        String value = defaultValue == null ? null : defaultValue.get();
        return value == null ? "" : value.trim();
    }
}
