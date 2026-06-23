-- ============================================================
-- V2: OSS 迁移支持 — image_migration_log 表 + mr_scan 增强
-- ============================================================

-- 1. 迁移日志表
CREATE TABLE IF NOT EXISTS app.image_migration_log (
    id              BIGSERIAL PRIMARY KEY,
    scan_id         INTEGER NOT NULL REFERENCES app.mr_scan (id) ON DELETE CASCADE,
    local_path      TEXT NOT NULL,
    oss_url         TEXT,
    migration_status VARCHAR(20) NOT NULL DEFAULT 'pending',
    error_message   TEXT,
    file_size       BIGINT,
    checksum_md5    VARCHAR(32),
    migrated_at     TIMESTAMP WITHOUT TIME ZONE,
    verified_at     TIMESTAMP WITHOUT TIME ZONE,
    created_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_migration_status         ON app.image_migration_log (migration_status);
CREATE INDEX IF NOT EXISTS idx_migration_scan_id        ON app.image_migration_log (scan_id);
CREATE INDEX IF NOT EXISTS idx_migration_created_at     ON app.image_migration_log (created_at);
CREATE INDEX IF NOT EXISTS idx_migration_status_created ON app.image_migration_log (migration_status, created_at);

COMMENT ON TABLE  app.image_migration_log IS '图片迁移日志表 — 追踪每张图片的迁移状态';
COMMENT ON COLUMN app.image_migration_log.scan_id         IS '关联的扫描记录ID';
COMMENT ON COLUMN app.image_migration_log.local_path      IS '本地文件路径';
COMMENT ON COLUMN app.image_migration_log.oss_url         IS 'OSS 文件URL (object key)';
COMMENT ON COLUMN app.image_migration_log.migration_status IS '迁移状态: pending/migrating/success/failed/verified/rollback';
COMMENT ON COLUMN app.image_migration_log.error_message   IS '错误信息（失败时）';
COMMENT ON COLUMN app.image_migration_log.file_size       IS '文件大小（字节）';
COMMENT ON COLUMN app.image_migration_log.checksum_md5    IS '文件MD5校验值';

-- 2. mr_scan 表增强字段
ALTER TABLE app.mr_scan ADD COLUMN IF NOT EXISTS oss_url           TEXT;
ALTER TABLE app.mr_scan ADD COLUMN IF NOT EXISTS file_size         BIGINT;
ALTER TABLE app.mr_scan ADD COLUMN IF NOT EXISTS checksum_md5      VARCHAR(32);
ALTER TABLE app.mr_scan ADD COLUMN IF NOT EXISTS migration_status  VARCHAR(20) DEFAULT 'not_migrated';
ALTER TABLE app.mr_scan ADD COLUMN IF NOT EXISTS migrated_at       TIMESTAMP WITHOUT TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_mr_scan_migration_status ON app.mr_scan (migration_status);
CREATE INDEX IF NOT EXISTS idx_mr_scan_oss_url          ON app.mr_scan (oss_url) WHERE oss_url IS NOT NULL;

COMMENT ON COLUMN app.mr_scan.oss_url          IS 'OSS 对象 key';
COMMENT ON COLUMN app.mr_scan.file_size        IS '文件大小（字节）';
COMMENT ON COLUMN app.mr_scan.checksum_md5     IS 'MD5 校验值';
COMMENT ON COLUMN app.mr_scan.migration_status IS '迁移状态: not_migrated/migrated/verified';
COMMENT ON COLUMN app.mr_scan.migrated_at      IS '迁移完成时间';

-- 3. 迁移统计视图
CREATE OR REPLACE VIEW app.v_migration_statistics AS
SELECT
    migration_status,
    COUNT(*) AS count,
    ROUND(COUNT(*) * 100.0 / NULLIF(SUM(COUNT(*)) OVER (), 0), 2) AS percentage
FROM app.image_migration_log
GROUP BY migration_status;

COMMENT ON VIEW app.v_migration_statistics IS '迁移状态统计视图';

-- 4. 待迁移图片列表视图
CREATE OR REPLACE VIEW app.v_pending_migrations AS
SELECT
    s.id   AS scan_id,
    s.bah,
    s.brxh,
    s.filename,
    s.folder,
    CONCAT('./data/img/', LEFT(s.folder, 5), '/', s.folder, '/', s.brxh, '-', s.bah, '/', s.filename) AS local_path,
    s.oss_url,
    s.migration_status
FROM app.mr_scan s
WHERE s.uploadflag != 0
  AND (s.oss_url IS NULL OR s.oss_url = '')
ORDER BY s.id;

COMMENT ON VIEW app.v_pending_migrations IS '待迁移图片列表视图';

-- 5. 初始化迁移日志函数
CREATE OR REPLACE FUNCTION app.init_migration_logs()
RETURNS VOID AS $$
DECLARE
    rec RECORD;
    inserted INT := 0;
BEGIN
    FOR rec IN
        SELECT id, bah, brxh, filename, folder
        FROM app.mr_scan
        WHERE uploadflag != 0
          AND (oss_url IS NULL OR oss_url = '')
          AND id NOT IN (SELECT scan_id FROM app.image_migration_log)
    LOOP
        INSERT INTO app.image_migration_log (scan_id, local_path, migration_status)
        VALUES (
            rec.id,
            CONCAT('./data/img/', LEFT(rec.folder, 5), '/', rec.folder, '/', rec.brxh, '-', rec.bah, '/', rec.filename),
            'pending'
        );
        inserted := inserted + 1;
    END LOOP;
    RAISE NOTICE 'Migration logs initialized: % records', inserted;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION app.init_migration_logs() IS '初始化迁移日志 — 为所有待迁移图片创建日志记录';

-- 6. 更新迁移状态函数
CREATE OR REPLACE FUNCTION app.update_migration_status(
    p_scan_id       INTEGER,
    p_status        VARCHAR(20),
    p_oss_url       TEXT DEFAULT NULL,
    p_error_message TEXT DEFAULT NULL,
    p_checksum_md5  VARCHAR(32) DEFAULT NULL,
    p_file_size     BIGINT DEFAULT NULL
) RETURNS VOID AS $$
BEGIN
    UPDATE app.image_migration_log
    SET
        migration_status = p_status,
        oss_url          = COALESCE(p_oss_url, oss_url),
        error_message    = p_error_message,
        checksum_md5     = COALESCE(p_checksum_md5, checksum_md5),
        file_size        = COALESCE(p_file_size, file_size),
        migrated_at      = CASE WHEN p_status = 'success'  THEN NOW() ELSE migrated_at END,
        verified_at      = CASE WHEN p_status = 'verified' THEN NOW() ELSE verified_at END,
        updated_at       = NOW()
    WHERE scan_id = p_scan_id;

    UPDATE app.mr_scan
    SET
        oss_url          = COALESCE(p_oss_url, oss_url),
        file_size        = COALESCE(p_file_size, file_size),
        checksum_md5     = COALESCE(p_checksum_md5, checksum_md5),
        migration_status = CASE
            WHEN p_status = 'success'  THEN 'migrated'
            WHEN p_status = 'verified' THEN 'verified'
            WHEN p_status = 'rollback' THEN 'not_migrated'
            ELSE migration_status
        END,
        migrated_at = CASE WHEN p_status = 'success' THEN NOW() ELSE migrated_at END
    WHERE id = p_scan_id;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION app.update_migration_status(INTEGER, VARCHAR, TEXT, TEXT, VARCHAR, BIGINT)
    IS '更新迁移状态 — 同时更新日志表和主表';
