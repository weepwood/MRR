-- mr_statistics 的 BEFORE INSERT 触发器会先创建 mr_archive，并引用 NEW.id。
-- PostgreSQL 在原统计行落库前检查非延迟外键会失败，因此将该外键改为事务级延迟校验。
-- 该迁移使用高于 0.6.3 基线的版本号，确保已执行 V20260722221000 的环境也能顺序升级。
-- 直接修改现有外键属性，避免删除重建约束和重新扫描大表。

ALTER TABLE app.mr_archive
    ALTER CONSTRAINT mr_archive_source_statistics_id_fkey
    DEFERRABLE INITIALLY DEFERRED;
