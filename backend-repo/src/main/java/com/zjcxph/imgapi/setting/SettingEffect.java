package com.zjcxph.imgapi.setting;

/**
 * 设置项的生效方式。
 */
public enum SettingEffect {

    /**
     * 保存后立即影响后续请求，无需重启进程。
     * <p>
     * 本项目的设置读取为"请求时懒读、无缓存"，因此 IMMEDIATE 是常态。
     * </p>
     */
    IMMEDIATE,

    /**
     * 保存后仅写入数据库，需要重启进程才能生效。
     * <p>
     * 前端必须把这类项渲染为只读或明确标注"需重启"，避免形成"改了没反应"的误导性开关。
     * </p>
     */
    RESTART_REQUIRED
}
