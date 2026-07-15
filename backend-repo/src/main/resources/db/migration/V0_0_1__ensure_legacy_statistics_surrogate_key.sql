-- ============================================================
-- 旧数据库兼容：为 mr_statistics 补齐稳定行标识
--
-- 部分历史数据库中的 mr_statistics 是直接从 Excel/旧 SQL 创建的，
-- 表存在但没有执行当前 V0 基线中定义的 id 列。V0_1 需要使用该列
-- 稳定追踪缺少上架号的统计记录，因此必须在病案主数据迁移前补齐。
-- ============================================================

DO $$
BEGIN
    IF to_regclass('app.mr_statistics') IS NULL THEN
        RAISE EXCEPTION 'Required table app.mr_statistics does not exist';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'app'
          AND table_name = 'mr_statistics'
          AND column_name = 'id'
    ) THEN
        CREATE SEQUENCE IF NOT EXISTS app.mr_statistics_legacy_id_seq
            AS INTEGER
            START WITH 1
            INCREMENT BY 1
            NO MINVALUE
            NO MAXVALUE
            CACHE 100;

        ALTER TABLE app.mr_statistics
            ADD COLUMN id INTEGER;

        ALTER TABLE app.mr_statistics
            ALTER COLUMN id
            SET DEFAULT nextval('app.mr_statistics_legacy_id_seq'::regclass);

        UPDATE app.mr_statistics
        SET id = nextval('app.mr_statistics_legacy_id_seq'::regclass)
        WHERE id IS NULL;

        ALTER TABLE app.mr_statistics
            ALTER COLUMN id SET NOT NULL;

        ALTER SEQUENCE app.mr_statistics_legacy_id_seq
            OWNED BY app.mr_statistics.id;
    END IF;
END;
$$;

-- PostgreSQL 外键允许引用非主键列，但该列必须具有非部分唯一约束/索引。
-- 当前基线创建的 id 已经是主键；旧库补列时由此索引提供相同唯一性保证。
CREATE UNIQUE INDEX IF NOT EXISTS ux_mr_statistics_id
    ON app.mr_statistics (id);

COMMENT ON COLUMN app.mr_statistics.id
    IS '统计记录数据库行标识；旧数据库由 V0_0_1 兼容迁移补齐';
