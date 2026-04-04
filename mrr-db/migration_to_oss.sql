-- ============================================
-- 图片迁移到 OSS - 数据库迁移脚本
-- 执行前请务必备份数据！
-- ============================================

-- 1. 创建迁移日志表
CREATE TABLE IF NOT EXISTS app.image_migration_log (
    id BIGSERIAL PRIMARY KEY,
    scan_id INTEGER NOT NULL REFERENCES app.mr_scan(id) ON DELETE CASCADE,
    local_path TEXT NOT NULL,
    oss_url TEXT,
    migration_status VARCHAR(20) NOT NULL DEFAULT 'pending',
    error_message TEXT,
    file_size BIGINT,
    checksum_md5 VARCHAR(32),
    migrated_at TIMESTAMP WITHOUT TIME ZONE,
    verified_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

-- 2. 创建索引
CREATE INDEX IF NOT EXISTS idx_migration_status ON app.image_migration_log(migration_status);
CREATE INDEX IF NOT EXISTS idx_migration_scan_id ON app.image_migration_log(scan_id);
CREATE INDEX IF NOT EXISTS idx_migration_created_at ON app.image_migration_log(created_at);
CREATE INDEX IF NOT EXISTS idx_migration_status_created ON app.image_migration_log(migration_status, created_at);

-- 3. 添加注释
COMMENT ON TABLE app.image_migration_log IS '图片迁移日志表 - 追踪每张图片的迁移状态';
COMMENT ON COLUMN app.image_migration_log.scan_id IS '关联的扫描记录ID';
COMMENT ON COLUMN app.image_migration_log.local_path IS '本地文件路径';
COMMENT ON COLUMN app.image_migration_log.oss_url IS 'OSS 文件URL';
COMMENT ON COLUMN app.image_migration_log.migration_status IS '迁移状态: pending/migrating/success/failed/verified/rollback';
COMMENT ON COLUMN app.image_migration_log.error_message IS '错误信息（失败时）';
COMMENT ON COLUMN app.image_migration_log.file_size IS '文件大小（字节）';
COMMENT ON COLUMN app.image_migration_log.checksum_md5 IS '文件MD5校验值';
COMMENT ON COLUMN app.image_migration_log.migrated_at IS '迁移完成时间';
COMMENT ON COLUMN app.image_migration_log.verified_at IS '验证完成时间';

-- 4. mr_scan 表增强（如果字段不存在则添加）
DO $$ 
BEGIN
    -- 添加 oss_url 字段（之前可能已添加）
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'app' 
          AND table_name = 'mr_scan' 
          AND column_name = 'oss_url'
    ) THEN
        ALTER TABLE app.mr_scan ADD COLUMN oss_url TEXT;
        RAISE NOTICE 'Added column oss_url to mr_scan';
    END IF;
    
    -- 添加 migration_status 字段
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'app' 
          AND table_name = 'mr_scan' 
          AND column_name = 'migration_status'
    ) THEN
        ALTER TABLE app.mr_scan ADD COLUMN migration_status VARCHAR(20) DEFAULT 'not_migrated';
        RAISE NOTICE 'Added column migration_status to mr_scan';
    END IF;
    
    -- 添加 migrated_at 字段
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'app' 
          AND table_name = 'mr_scan' 
          AND column_name = 'migrated_at'
    ) THEN
        ALTER TABLE app.mr_scan ADD COLUMN migrated_at TIMESTAMP WITHOUT TIME ZONE;
        RAISE NOTICE 'Added column migrated_at to mr_scan';
    END IF;
END $$;

-- 5. 创建 mr_scan 表的索引
CREATE INDEX IF NOT EXISTS idx_mr_scan_migration_status ON app.mr_scan(migration_status);
CREATE INDEX IF NOT EXISTS idx_mr_scan_oss_url ON app.mr_scan(oss_url) WHERE oss_url IS NOT NULL;

-- 6. 添加 mr_scan 表字段注释
COMMENT ON COLUMN app.mr_scan.oss_url IS 'OSS 图片地址';
COMMENT ON COLUMN app.mr_scan.migration_status IS '迁移状态: not_migrated/migrated/verified';
COMMENT ON COLUMN app.mr_scan.migrated_at IS '迁移完成时间';

-- 7. 创建视图：迁移统计
CREATE OR REPLACE VIEW app.v_migration_statistics AS
SELECT 
    migration_status,
    COUNT(*) as count,
    ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER (), 2) as percentage
FROM app.image_migration_log
GROUP BY migration_status;

COMMENT ON VIEW app.v_migration_statistics IS '迁移状态统计视图';

-- 8. 创建视图：待迁移图片列表
CREATE OR REPLACE VIEW app.v_pending_migrations AS
SELECT 
    s.id as scan_id,
    s.bah,
    s.brxh,
    s.filename,
    s.folder,
    CONCAT(
        './data/img/',
        LEFT(s.folder, 5), '/',
        s.folder, '/',
        s.brxh, '-', s.bah, '/',
        s.filename
    ) as local_path,
    s.oss_url,
    s.migration_status
FROM app.mr_scan s
WHERE s.uploadflag != 0
  AND (s.oss_url IS NULL OR s.oss_url = '')
ORDER BY s.id;

COMMENT ON VIEW app.v_pending_migrations IS '待迁移图片列表视图';

-- 9. 创建函数：初始化迁移日志
CREATE OR REPLACE FUNCTION app.init_migration_logs()
RETURNS VOID AS $$
DECLARE
    rec RECORD;
BEGIN
    -- 为所有未迁移的图片创建迁移日志记录
    FOR rec IN 
        SELECT id, bah, brxh, filename, folder
        FROM app.mr_scan
        WHERE uploadflag != 0
          AND (oss_url IS NULL OR oss_url = '')
          AND id NOT IN (SELECT scan_id FROM app.image_migration_log)
    LOOP
        INSERT INTO app.image_migration_log (
            scan_id,
            local_path,
            migration_status
        ) VALUES (
            rec.id,
            CONCAT(
                './data/img/',
                LEFT(rec.folder, 5), '/',
                rec.folder, '/',
                rec.brxh, '-', rec.bah, '/',
                rec.filename
            ),
            'pending'
        );
    END LOOP;
    
    RAISE NOTICE 'Migration logs initialized';
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION app.init_migration_logs() IS '初始化迁移日志 - 为所有待迁移图片创建日志记录';

-- 10. 创建函数：更新迁移状态
CREATE OR REPLACE FUNCTION app.update_migration_status(
    p_scan_id INTEGER,
    p_status VARCHAR(20),
    p_oss_url TEXT DEFAULT NULL,
    p_error_message TEXT DEFAULT NULL,
    p_checksum_md5 VARCHAR(32) DEFAULT NULL
)
RETURNS VOID AS $$
BEGIN
    -- 更新迁移日志
    UPDATE app.image_migration_log
    SET 
        migration_status = p_status,
        oss_url = COALESCE(p_oss_url, oss_url),
        error_message = p_error_message,
        checksum_md5 = COALESCE(p_checksum_md5, checksum_md5),
        migrated_at = CASE WHEN p_status = 'success' THEN NOW() ELSE migrated_at END,
        verified_at = CASE WHEN p_status = 'verified' THEN NOW() ELSE verified_at END,
        updated_at = NOW()
    WHERE scan_id = p_scan_id;
    
    -- 更新 mr_scan 表
    UPDATE app.mr_scan
    SET 
        oss_url = COALESCE(p_oss_url, oss_url),
        migration_status = CASE 
            WHEN p_status = 'success' THEN 'migrated'
            WHEN p_status = 'verified' THEN 'verified'
            WHEN p_status = 'rollback' THEN 'not_migrated'
            ELSE migration_status
        END,
        migrated_at = CASE WHEN p_status = 'success' THEN NOW() ELSE migrated_at END
    WHERE id = p_scan_id;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION app.update_migration_status(INTEGER, VARCHAR, TEXT, TEXT, VARCHAR) IS '更新迁移状态 - 同时更新日志表和主表';

-- ============================================
-- 执行说明：
-- 1. 此脚本会创建必要的表和字段
-- 2. 不会删除或修改现有数据
-- 3. 可以安全地重复执行
-- 4. 执行后调用 SELECT app.init_migration_logs(); 初始化日志
-- ============================================
