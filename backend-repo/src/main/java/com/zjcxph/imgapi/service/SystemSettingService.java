package com.zjcxph.imgapi.service;

import java.util.Map;

/**
 * 系统设置服务接口。
 * 以键值对方式管理全局系统配置，支持批量读写和单个值查询。
 */
public interface SystemSettingService {

    /** 获取全部设置，返回 key → value 映射 */
    Map<String, String> getAllSettings();

    /** 获取单个设置值 */
    String getSetting(String key);

    /** 批量保存（UPSERT）设置 */
    void saveSettings(Map<String, String> settings, String updatedBy);

    /** 设置单个值 */
    void setSetting(String key, String value, String updatedBy);

    /** 删除设置 */
    void deleteSetting(String key);
}
