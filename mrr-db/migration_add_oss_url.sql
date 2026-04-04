-- 为 mr_scan 表添加 oss_url 字段
-- 执行此脚本以在现有数据库中添加 OSS URL 支持

-- SQLite 版本
ALTER TABLE mr_scan ADD COLUMN oss_url TEXT;

-- PostgreSQL 版本（如果使用 PostgreSQL，请取消下面的注释并注释上面的语句）
-- ALTER TABLE app.mr_scan ADD COLUMN oss_url TEXT;

-- 为 oss_url 字段添加索引（可选，如果需要按 OSS URL 查询）
-- CREATE INDEX idx_mr_scan_oss_url ON mr_scan (oss_url);
-- PostgreSQL: CREATE INDEX idx_mr_scan_oss_url ON app.mr_scan (oss_url);
