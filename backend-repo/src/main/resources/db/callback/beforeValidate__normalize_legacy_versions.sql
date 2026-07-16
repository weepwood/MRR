-- 在 Flyway validate 前，将历史版本号映射为 14 位日期时间版本。
-- 这里只更新 Flyway 元数据，不执行或修改任何业务迁移 SQL。
DO $$
DECLARE
    mapping RECORD;
    old_exists BOOLEAN;
    new_exists BOOLEAN;
BEGIN
    IF to_regclass('app.flyway_schema_history') IS NULL THEN
        RETURN;
    END IF;

    FOR mapping IN
        SELECT *
        FROM (VALUES
            ('0',     'V0__baseline_schema.sql',
             '20260715113552', 'V20260715113552__baseline_schema.sql'),
            ('0.0.1', 'V0_0_1__ensure_legacy_statistics_surrogate_key.sql',
             '20260715232200', 'V20260715232200__ensure_legacy_statistics_surrogate_key.sql'),
            ('0.1',   'V0_1__refactor_archive_data_model.sql',
             '20260715232228', 'V20260715232228__refactor_archive_data_model.sql'),
            ('0.2',   'V0_2__enforce_archive_lookup_rules.sql',
             '20260715232620', 'V20260715232620__enforce_archive_lookup_rules.sql'),
            ('0.3',   'V0_3__refresh_archive_links_on_code_change.sql',
             '20260715232837', 'V20260715232837__refresh_archive_links_on_code_change.sql'),
            ('0.4',   'V0_4__optimize_scan_archive_backfill.sql',
             '20260715233205', 'V20260715233205__optimize_scan_archive_backfill.sql')
        ) AS version_mapping(old_version, old_script, new_version, new_script)
    LOOP
        SELECT EXISTS (
            SELECT 1
            FROM app.flyway_schema_history
            WHERE version = mapping.old_version
              AND script = mapping.old_script
        ) INTO old_exists;

        SELECT EXISTS (
            SELECT 1
            FROM app.flyway_schema_history
            WHERE version = mapping.new_version
               OR script = mapping.new_script
        ) INTO new_exists;

        IF old_exists AND new_exists THEN
            RAISE EXCEPTION
                'Flyway 历史同时存在旧迁移 % 和新迁移 %，请先检查 flyway_schema_history',
                mapping.old_script,
                mapping.new_script;
        END IF;

        IF old_exists THEN
            UPDATE app.flyway_schema_history
            SET version = mapping.new_version,
                script = mapping.new_script
            WHERE version = mapping.old_version
              AND script = mapping.old_script;
        END IF;
    END LOOP;
END
$$;
