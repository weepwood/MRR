package com.zjcxph.imgapi.mapper;

import com.zjcxph.imgapi.entity.SystemSetting;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 系统设置 MyBatis Mapper。
 * 支持键值对 CRUD，使用 UPSERT 语义处理 key 唯一性冲突。
 */
@Mapper
public interface SystemSettingMapper {

    @Select("SELECT * FROM mr_system_settings ORDER BY setting_key")
    List<SystemSetting> findAll();

    @Select("SELECT * FROM mr_system_settings WHERE setting_key = #{key}")
    SystemSetting findByKey(String key);

    @Insert("""
            INSERT INTO mr_system_settings (setting_key, setting_value, description, updated_by)
            VALUES (#{settingKey}, #{settingValue}, #{description}, #{updatedBy})
            ON CONFLICT (setting_key) DO UPDATE SET
                setting_value = EXCLUDED.setting_value,
                description = EXCLUDED.description,
                updated_by = EXCLUDED.updated_by,
                updated_at = DEFAULT
            """)
    int upsert(SystemSetting setting);

    @Update("UPDATE mr_system_settings SET setting_value = #{settingValue}, updated_by = #{updatedBy}, updated_at = DEFAULT WHERE setting_key = #{settingKey}")
    int updateValue(SystemSetting setting);

    @Delete("DELETE FROM mr_system_settings WHERE setting_key = #{key}")
    int deleteByKey(String key);

    /** 批量 UPSERT 多条设置 */
    @Insert("""
            <script>
            INSERT INTO mr_system_settings (setting_key, setting_value, description, updated_by)
            VALUES
            <foreach collection='list' item='item' separator=','>
                (#{item.settingKey}, #{item.settingValue}, #{item.description}, #{item.updatedBy})
            </foreach>
            ON CONFLICT (setting_key) DO UPDATE SET
                setting_value = EXCLUDED.setting_value,
                description = EXCLUDED.description,
                updated_by = EXCLUDED.updated_by,
                updated_at = DEFAULT
            </script>
            """)
    int upsertAll(List<SystemSetting> settings);
}
