-- ADR-0010：access_log 按月分区（影子分区表 + 触发器双写 + 后台分批复制 + 运维手动切换）。
--
-- 本迁移只新增对象，不复制或改写 app.access_log 数据。安装触发器需要短时表锁，超时会回滚迁移。
-- 复制由应用后台任务（app.access-log-partition.copy-enabled=true）分批调用 access_log_partition_copy_batch 完成；
-- 复制追平后由运维在低峰调用 access_log_partition_cutover() 交换表名。切换前任何时候都可调用
-- access_log_partition_abort() 放弃；切换后可调用 access_log_partition_rollback() 换回旧表。
-- 分区表本身不支持跨分区唯一索引；保留回退能力期间，由同步写入的旧表维持 request_id 唯一性。

SET LOCAL lock_timeout = '5s';

-- 1. 影子分区表 -------------------------------------------------------------

CREATE SEQUENCE app.access_log_part_id_seq AS BIGINT;

CREATE TABLE app.access_log_partitioned (
    id                BIGINT NOT NULL DEFAULT nextval('app.access_log_part_id_seq'),
    username          TEXT,
    client_ip         TEXT,
    request_uri       TEXT,
    method            TEXT,
    user_agent        TEXT,
    access_time       TIMESTAMP,
    query_string      TEXT,
    request_body      TEXT,
    response_status   TEXT,
    execute_time      BIGINT,
    referer           TEXT,
    audit_action      TEXT,
    audit_target      TEXT,
    audit_description TEXT,
    request_id        TEXT,
    endpoint_template TEXT,
    error_message     TEXT
) PARTITION BY RANGE (access_time);

ALTER SEQUENCE app.access_log_part_id_seq OWNED BY app.access_log_partitioned.id;

-- access_time 为 NULL 或落在尚未建分区的月份时进入默认分区；维护任务会把这些月份迁出为独立分区。
CREATE TABLE app.access_log_default PARTITION OF app.access_log_partitioned DEFAULT;

-- 索引与现有 access_log 的查询一一对应；BRIN 由 (access_time, id) B-tree 与分区裁剪替代。
CREATE INDEX idx_access_log_part_id               ON app.access_log_partitioned (id);
CREATE INDEX idx_access_log_part_time_id          ON app.access_log_partitioned (access_time, id);
CREATE INDEX idx_access_log_part_client_ip        ON app.access_log_partitioned (client_ip);
CREATE INDEX idx_access_log_part_request_uri      ON app.access_log_partitioned (request_uri);
CREATE INDEX idx_access_log_part_method           ON app.access_log_partitioned (method);
CREATE INDEX idx_access_log_part_response_status  ON app.access_log_partitioned (response_status);
CREATE INDEX idx_access_log_part_method_status    ON app.access_log_partitioned (method, response_status);
CREATE INDEX idx_access_log_part_endpoint         ON app.access_log_partitioned (endpoint_template);
CREATE INDEX idx_access_log_part_request_id       ON app.access_log_partitioned (request_id)
    WHERE request_id IS NOT NULL;
CREATE INDEX idx_access_log_part_audit_target     ON app.access_log_partitioned (audit_target)
    WHERE audit_target IS NOT NULL;
CREATE INDEX idx_access_log_part_error_status_time ON app.access_log_partitioned (access_time DESC, response_status)
    WHERE response_status LIKE '4%' OR response_status LIKE '5%';
-- 与历史库 V10 的影像审计索引一致：searchImageAudit 只看四种影像动作并按时间倒序。
CREATE INDEX idx_access_log_part_image_audit      ON app.access_log_partitioned (audit_action, access_time DESC)
    WHERE audit_action IN ('LIST', 'DOWNLOAD', 'VIEW_IMAGE', 'VIEW_OSS_IMAGE');
CREATE INDEX idx_access_log_part_keyword_trgm     ON app.access_log_partitioned
    USING gin ((coalesce(username, '') || chr(1) || coalesce(client_ip, '') || chr(1) || coalesce(request_uri, '') || chr(1) || coalesce(query_string, '') || chr(1) || coalesce(user_agent, '')) public.gin_trgm_ops);
CREATE INDEX idx_access_log_part_referer_trgm     ON app.access_log_partitioned
    USING gin (referer public.gin_trgm_ops);

COMMENT ON TABLE app.access_log_partitioned IS '接口访问与影像访问审计日志（按 access_time 月分区；ADR-0010 切换前为影子表）';
COMMENT ON TABLE app.access_log_default IS 'access_log 默认分区：access_time 为空或所属月份尚未建分区的记录';

-- 2. 迁移状态（单行） --------------------------------------------------------

CREATE TABLE app.access_log_partition_state (
    singleton      BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (singleton),
    phase          VARCHAR(16) NOT NULL CHECK (phase IN ('COPYING', 'READY', 'CUTOVER', 'ABORTED')),
    copy_target_id BIGINT,
    copy_cursor_id BIGINT NOT NULL DEFAULT 0,
    rows_copied    BIGINT NOT NULL DEFAULT 0,
    cutover_max_id BIGINT,
    cutover_at     TIMESTAMPTZ,
    rolled_back_at TIMESTAMPTZ,
    aborted_at     TIMESTAMPTZ,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO app.access_log_partition_state (phase) VALUES ('COPYING');

COMMENT ON TABLE app.access_log_partition_state IS 'ADR-0010 access_log 分区迁移进度：COPYING → READY → CUTOVER（可 rollback 回 READY），或 ABORTED';
COMMENT ON COLUMN app.access_log_partition_state.copy_target_id IS '首次复制批次时旧表的 max(id)；此后新行由触发器双写';
COMMENT ON COLUMN app.access_log_partition_state.copy_cursor_id IS '已复制到的旧表 id（含）；复制按 id 升序推进，中断后从这里继续';

-- 3. 分区维护函数 ------------------------------------------------------------

-- 当前承担分区写入的表：切换前为 access_log_partitioned，切换后为 access_log；放弃后为 NULL。
CREATE FUNCTION app.access_log_partition_parent() RETURNS regclass
    LANGUAGE sql STABLE AS $$
    SELECT c.oid::regclass
    FROM pg_partitioned_table p
    JOIN pg_class c ON c.oid = p.partrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'app' AND c.relname IN ('access_log', 'access_log_partitioned')
    LIMIT 1
$$;

CREATE FUNCTION app.access_log_default_partition(p_parent regclass) RETURNS regclass
    LANGUAGE sql STABLE AS $$
    SELECT c.oid::regclass
    FROM pg_inherits i
    JOIN pg_class c ON c.oid = i.inhrelid
    WHERE i.inhparent = p_parent AND pg_get_expr(c.relpartbound, c.oid) = 'DEFAULT'
$$;

-- 确保 p_month 所在月份的分区存在；新建返回 TRUE。默认分区里已有该月数据时先迁出再 ATTACH，
-- 否则 PostgreSQL 会拒绝建立与默认分区内容重叠的新分区。
CREATE FUNCTION app.access_log_ensure_partition(p_month TIMESTAMP) RETURNS BOOLEAN
    LANGUAGE plpgsql AS $$
DECLARE
    v_parent   regclass := app.access_log_partition_parent();
    v_from     TIMESTAMP := date_trunc('month', p_month);
    v_to       TIMESTAMP := date_trunc('month', p_month) + INTERVAL '1 month';
    v_name     TEXT := 'access_log_y' || to_char(date_trunc('month', p_month), 'YYYY')
                       || 'm' || to_char(date_trunc('month', p_month), 'MM');
    -- 分区边界用固定格式书写，不受会话 DateStyle 影响
    v_from_text TEXT := to_char(date_trunc('month', p_month), 'YYYY-MM-DD HH24:MI:SS');
    v_to_text   TEXT := to_char(date_trunc('month', p_month) + INTERVAL '1 month', 'YYYY-MM-DD HH24:MI:SS');
    v_default  regclass;
    v_has_rows BOOLEAN := FALSE;
    v_mirrors_to_legacy BOOLEAN := FALSE;
BEGIN
    IF v_parent IS NULL OR p_month IS NULL THEN
        RETURN FALSE;
    END IF;
    -- 同一超时约束咨询锁和表锁，失败后由调用方重试。
    PERFORM set_config('lock_timeout', '3s', true);
    PERFORM pg_advisory_xact_lock(hashtext('mrr.access_log_partition'));
    IF to_regclass(format('app.%I', v_name)) IS NOT NULL THEN
        RETURN FALSE;
    END IF;
    v_default := app.access_log_default_partition(v_parent);
    IF v_default IS NOT NULL THEN
        EXECUTE format('SELECT EXISTS (SELECT 1 FROM %s WHERE access_time >= $1 AND access_time < $2)', v_default)
            INTO v_has_rows USING v_from, v_to;
    END IF;
    IF v_has_rows THEN
        SELECT EXISTS (
            SELECT 1 FROM pg_trigger
            WHERE tgrelid = v_default AND tgname = 'trg_access_log_sync_to_legacy' AND tgenabled = 'O'
        ) INTO v_mirrors_to_legacy;
        -- 数据只在分区间移动。事务级 DDL 锁阻止其他会话在同步触发器停用期间修改默认分区。
        IF v_mirrors_to_legacy THEN
            EXECUTE format('ALTER TABLE %s DISABLE TRIGGER trg_access_log_sync_to_legacy', v_default);
        END IF;
        EXECUTE format('CREATE TABLE app.%I (LIKE %s INCLUDING DEFAULTS)', v_name, v_parent);
        EXECUTE format(
            'WITH moved AS (DELETE FROM %s WHERE access_time >= $1 AND access_time < $2 RETURNING *) '
            || 'INSERT INTO app.%I SELECT * FROM moved', v_default, v_name)
            USING v_from, v_to;
        IF v_mirrors_to_legacy THEN
            EXECUTE format('ALTER TABLE %s ENABLE TRIGGER trg_access_log_sync_to_legacy', v_default);
        END IF;
        EXECUTE format('ALTER TABLE %s ATTACH PARTITION app.%I FOR VALUES FROM (%L) TO (%L)',
                       v_parent, v_name, v_from_text, v_to_text);
    ELSE
        EXECUTE format('CREATE TABLE app.%I PARTITION OF %s FOR VALUES FROM (%L) TO (%L)',
                       v_name, v_parent, v_from_text, v_to_text);
    END IF;
    RETURN TRUE;
END
$$;

-- 预建当前及之后 p_months_ahead 个月的分区，并把默认分区中已有月份迁出；返回新建分区数。
-- 月份先汇总成数组再逐个处理：在默认分区上保持游标打开时 ATTACH 会被 PostgreSQL 拒绝。
CREATE FUNCTION app.access_log_maintain_partitions(p_months_ahead INTEGER DEFAULT 2) RETURNS INTEGER
    LANGUAGE plpgsql AS $$
DECLARE
    v_parent  regclass := app.access_log_partition_parent();
    v_default regclass;
    v_months  TIMESTAMP[];
    v_month   TIMESTAMP;
    v_created INTEGER := 0;
BEGIN
    IF v_parent IS NULL THEN
        RETURN 0;
    END IF;
    FOR i IN 0..GREATEST(COALESCE(p_months_ahead, 2), 0) LOOP
        IF app.access_log_ensure_partition(date_trunc('month', LOCALTIMESTAMP) + make_interval(months => i)) THEN
            v_created := v_created + 1;
        END IF;
    END LOOP;
    v_default := app.access_log_default_partition(v_parent);
    IF v_default IS NOT NULL THEN
        EXECUTE format(
            'SELECT array_agg(DISTINCT date_trunc(''month'', access_time)) FROM %s WHERE access_time IS NOT NULL',
            v_default)
            INTO v_months;
    END IF;
    IF v_months IS NOT NULL THEN
        FOREACH v_month IN ARRAY v_months LOOP
            IF app.access_log_ensure_partition(v_month) THEN
                v_created := v_created + 1;
            END IF;
        END LOOP;
    END IF;
    RETURN v_created;
END
$$;

-- 保留期清理：分离并删除上界不晚于 p_cutoff 的整月分区（不碰默认分区），返回删除的分区数。
-- 跨越 cutoff 的那个月份仍由 LogRetentionCleaner 的分批 DELETE 处理。
CREATE FUNCTION app.access_log_drop_expired_partitions(p_cutoff TIMESTAMP) RETURNS INTEGER
    LANGUAGE plpgsql AS $$
DECLARE
    v_parent  regclass := app.access_log_partition_parent();
    v_parts   TEXT[];
    v_part    TEXT;
    v_dropped INTEGER := 0;
BEGIN
    -- 旧表退役前由普通分批 DELETE 清理；删整月分区不会触发逐行同步。
    IF v_parent IS NULL OR p_cutoff IS NULL
            OR v_parent IS DISTINCT FROM to_regclass('app.access_log')
            OR to_regclass('app.access_log_legacy') IS NOT NULL THEN
        RETURN 0;
    END IF;
    PERFORM set_config('lock_timeout', '3s', true);
    PERFORM pg_advisory_xact_lock(hashtext('mrr.access_log_partition'));
    SELECT array_agg(part ORDER BY upper_bound)
    INTO v_parts
    FROM (
        SELECT c.oid::regclass::text AS part,
               substring(pg_get_expr(c.relpartbound, c.oid) FROM 'TO \(''([^'']+)''\)')::TIMESTAMP AS upper_bound
        FROM pg_inherits i
        JOIN pg_class c ON c.oid = i.inhrelid
        WHERE i.inhparent = v_parent
          AND pg_get_expr(c.relpartbound, c.oid) <> 'DEFAULT'
    ) bounds
    WHERE upper_bound <= p_cutoff;
    IF v_parts IS NOT NULL THEN
        FOREACH v_part IN ARRAY v_parts LOOP
            EXECUTE format('ALTER TABLE %s DETACH PARTITION %s', v_parent, v_part);
            EXECUTE format('DROP TABLE %s', v_part);
            v_dropped := v_dropped + 1;
        END LOOP;
    END IF;
    RETURN v_dropped;
END
$$;

-- 4. 双写与分批复制 ----------------------------------------------------------

CREATE FUNCTION app.access_log_copy_to_partitioned() RETURNS TRIGGER
    LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        DELETE FROM app.access_log_partitioned
        WHERE id = OLD.id AND access_time IS NOT DISTINCT FROM OLD.access_time;
        RETURN NULL;
    END IF;
    INSERT INTO app.access_log_partitioned (
        id, username, client_ip, request_uri, method, user_agent, access_time, query_string,
        request_body, response_status, execute_time, referer, audit_action, audit_target,
        audit_description, request_id, endpoint_template, error_message
    ) VALUES (
        NEW.id, NEW.username, NEW.client_ip, NEW.request_uri, NEW.method, NEW.user_agent,
        NEW.access_time, NEW.query_string, NEW.request_body, NEW.response_status,
        NEW.execute_time, NEW.referer, NEW.audit_action, NEW.audit_target, NEW.audit_description,
        NEW.request_id, NEW.endpoint_template, NEW.error_message
    );
    RETURN NULL;
END
$$;

-- 切换后保留回退窗口：写入旧表可继续执行其 request_id 唯一约束，普通删除也同步。
CREATE FUNCTION app.access_log_sync_to_legacy() RETURNS TRIGGER
    LANGUAGE plpgsql AS $$
BEGIN
    IF to_regclass('app.access_log_legacy') IS NULL THEN
        RETURN NULL;
    END IF;
    IF TG_OP = 'DELETE' THEN
        DELETE FROM app.access_log_legacy WHERE id = OLD.id;
        RETURN NULL;
    END IF;
    INSERT INTO app.access_log_legacy (
        id, username, client_ip, request_uri, method, user_agent, access_time, query_string,
        request_body, response_status, execute_time, referer, audit_action, audit_target,
        audit_description, request_id, endpoint_template, error_message
    ) VALUES (
        NEW.id, NEW.username, NEW.client_ip, NEW.request_uri, NEW.method, NEW.user_agent,
        NEW.access_time, NEW.query_string, NEW.request_body, NEW.response_status,
        NEW.execute_time, NEW.referer, NEW.audit_action, NEW.audit_target, NEW.audit_description,
        NEW.request_id, NEW.endpoint_template, NEW.error_message
    );
    RETURN NULL;
END
$$;

CREATE TRIGGER trg_access_log_copy_to_partitioned
    AFTER INSERT OR DELETE ON app.access_log
    FOR EACH ROW EXECUTE FUNCTION app.access_log_copy_to_partitioned();

-- 按 id 升序复制一批旧数据（一次调用一个事务）。已由触发器写入的行按 id 跳过，可重复执行。
CREATE FUNCTION app.access_log_partition_copy_batch(p_limit INTEGER) RETURNS JSONB
    LANGUAGE plpgsql AS $$
DECLARE
    s         app.access_log_partition_state%ROWTYPE;
    v_months  TIMESTAMP[];
    v_ids     BIGINT[];
    v_month   TIMESTAMP;
    v_max     BIGINT;
    v_scanned INTEGER := 0;
    v_copied  INTEGER := 0;
    v_phase   TEXT;
BEGIN
    PERFORM set_config('lock_timeout', '5s', true);
    SELECT * INTO s FROM app.access_log_partition_state FOR UPDATE;
    IF NOT FOUND OR s.phase NOT IN ('COPYING', 'READY') THEN
        RETURN jsonb_build_object('phase', COALESCE(s.phase, 'MISSING'), 'scanned', 0, 'copied', 0);
    END IF;
    IF s.copy_target_id IS NULL THEN
        SELECT COALESCE(max(id), 0) INTO s.copy_target_id FROM app.access_log;
    END IF;

    PERFORM pg_advisory_xact_lock(hashtext('mrr.access_log_partition'));
    SELECT max(id), count(*), array_agg(id),
           array_agg(DISTINCT date_trunc('month', access_time)) FILTER (WHERE access_time IS NOT NULL)
    INTO v_max, v_scanned, v_ids, v_months
    FROM (
        SELECT id, access_time FROM app.access_log
        WHERE id > s.copy_cursor_id
        ORDER BY id
        LIMIT GREATEST(COALESCE(p_limit, 1), 1)
        FOR KEY SHARE
    ) batch;

    IF v_scanned > 0 THEN
        IF v_months IS NOT NULL THEN
            FOREACH v_month IN ARRAY v_months LOOP
                PERFORM app.access_log_ensure_partition(v_month);
            END LOOP;
        END IF;
        INSERT INTO app.access_log_partitioned (
        id, username, client_ip, request_uri, method, user_agent, access_time, query_string,
        request_body, response_status, execute_time, referer, audit_action, audit_target,
        audit_description, request_id, endpoint_template, error_message
        )
        SELECT
        l.id, l.username, l.client_ip, l.request_uri, l.method, l.user_agent, l.access_time,
        l.query_string, l.request_body, l.response_status, l.execute_time, l.referer,
        l.audit_action, l.audit_target, l.audit_description, l.request_id, l.endpoint_template,
        l.error_message
        FROM app.access_log l
        WHERE l.id = ANY(v_ids)
          AND NOT EXISTS (SELECT 1 FROM app.access_log_partitioned p WHERE p.id = l.id);
        GET DIAGNOSTICS v_copied = ROW_COUNT;
        s.copy_cursor_id := v_max;
    ELSE
        -- 目标行可能已到期删除。被延迟提交的新增行仍由触发器同步。
        s.copy_cursor_id := GREATEST(s.copy_cursor_id, s.copy_target_id);
    END IF;

    v_phase := CASE WHEN s.copy_cursor_id >= s.copy_target_id THEN 'READY' ELSE s.phase END;
    UPDATE app.access_log_partition_state
    SET phase = v_phase,
        copy_target_id = s.copy_target_id,
        copy_cursor_id = s.copy_cursor_id,
        rows_copied = rows_copied + v_copied,
        updated_at = CURRENT_TIMESTAMP;
    RETURN jsonb_build_object('phase', v_phase, 'scanned', v_scanned, 'copied', v_copied,
                              'cursorId', s.copy_cursor_id, 'targetId', s.copy_target_id);
END
$$;

-- 5. 切换、回退与放弃 --------------------------------------------------------

-- READY 后调用：短暂锁住两张表，补齐游标之后的尾部，删除双写触发器并交换表名。
-- 旧表保留为 access_log_legacy，观察期结束后由后续迁移删除。
CREATE FUNCTION app.access_log_partition_cutover() RETURNS JSONB
    LANGUAGE plpgsql AS $$
DECLARE
    s        app.access_log_partition_state%ROWTYPE;
    v_months TIMESTAMP[];
    v_month  TIMESTAMP;
    v_tail   INTEGER := 0;
    v_max    BIGINT;
BEGIN
    PERFORM set_config('lock_timeout', '5s', true);
    SELECT * INTO s FROM app.access_log_partition_state FOR UPDATE;
    IF NOT FOUND OR s.phase <> 'READY' THEN
        RAISE EXCEPTION 'ACCESS_LOG_PARTITION_NOT_READY'
            USING DETAIL = format('phase=%s', COALESCE(s.phase, 'MISSING'));
    END IF;
    -- 先拿分区咨询锁再锁表：补尾部时可能建分区，与维护任务按同一顺序加锁才不会死锁。
    PERFORM pg_advisory_xact_lock(hashtext('mrr.access_log_partition'));
    LOCK TABLE app.access_log, app.access_log_partitioned IN ACCESS EXCLUSIVE MODE;

    SELECT array_agg(DISTINCT date_trunc('month', access_time)) FILTER (WHERE access_time IS NOT NULL)
    INTO v_months
    FROM app.access_log WHERE id > s.copy_cursor_id;
    IF v_months IS NOT NULL THEN
        FOREACH v_month IN ARRAY v_months LOOP
            PERFORM app.access_log_ensure_partition(v_month);
        END LOOP;
    END IF;
    INSERT INTO app.access_log_partitioned (
        id, username, client_ip, request_uri, method, user_agent, access_time, query_string,
        request_body, response_status, execute_time, referer, audit_action, audit_target,
        audit_description, request_id, endpoint_template, error_message
    )
    SELECT
        l.id, l.username, l.client_ip, l.request_uri, l.method, l.user_agent, l.access_time,
        l.query_string, l.request_body, l.response_status, l.execute_time, l.referer,
        l.audit_action, l.audit_target, l.audit_description, l.request_id, l.endpoint_template,
        l.error_message
    FROM app.access_log l
    WHERE l.id > s.copy_cursor_id
      AND NOT EXISTS (SELECT 1 FROM app.access_log_partitioned p WHERE p.id = l.id);
    GET DIAGNOSTICS v_tail = ROW_COUNT;

    v_max := GREATEST(COALESCE((SELECT max(id) FROM app.access_log), 0),
                      COALESCE((SELECT max(id) FROM app.access_log_partitioned), 0));

    DROP TRIGGER trg_access_log_copy_to_partitioned ON app.access_log;
    ALTER TABLE app.access_log RENAME TO access_log_legacy;
    ALTER TABLE app.access_log_partitioned RENAME TO access_log;
    CREATE TRIGGER trg_access_log_sync_to_legacy
        AFTER INSERT OR DELETE ON app.access_log
        FOR EACH ROW EXECUTE FUNCTION app.access_log_sync_to_legacy();
    PERFORM setval('app.access_log_part_id_seq', GREATEST(v_max, 1), v_max > 0);

    UPDATE app.access_log_partition_state
    SET phase = 'CUTOVER',
        cutover_max_id = v_max,
        cutover_at = CURRENT_TIMESTAMP,
        copy_cursor_id = v_max,
        rows_copied = rows_copied + v_tail,
        updated_at = CURRENT_TIMESTAMP;
    RETURN jsonb_build_object('phase', 'CUTOVER', 'tailCopied', v_tail, 'cutoverMaxId', v_max);
END
$$;

-- CUTOVER 后调用：把切换后写入的新行补回旧表，换回表名并恢复双写，状态回到 READY（可再次切换）。
CREATE FUNCTION app.access_log_partition_rollback() RETURNS JSONB
    LANGUAGE plpgsql AS $$
DECLARE
    s      app.access_log_partition_state%ROWTYPE;
    v_back INTEGER := 0;
    v_max  BIGINT;
BEGIN
    PERFORM set_config('lock_timeout', '5s', true);
    SELECT * INTO s FROM app.access_log_partition_state FOR UPDATE;
    IF NOT FOUND OR s.phase <> 'CUTOVER' OR to_regclass('app.access_log_legacy') IS NULL THEN
        RAISE EXCEPTION 'ACCESS_LOG_PARTITION_NOT_CUTOVER'
            USING DETAIL = format('phase=%s', COALESCE(s.phase, 'MISSING'));
    END IF;
    PERFORM pg_advisory_xact_lock(hashtext('mrr.access_log_partition'));
    LOCK TABLE app.access_log, app.access_log_legacy IN ACCESS EXCLUSIVE MODE;

    INSERT INTO app.access_log_legacy (
        id, username, client_ip, request_uri, method, user_agent, access_time, query_string,
        request_body, response_status, execute_time, referer, audit_action, audit_target,
        audit_description, request_id, endpoint_template, error_message
    )
    SELECT
        l.id, l.username, l.client_ip, l.request_uri, l.method, l.user_agent, l.access_time,
        l.query_string, l.request_body, l.response_status, l.execute_time, l.referer,
        l.audit_action, l.audit_target, l.audit_description, l.request_id, l.endpoint_template,
        l.error_message
    FROM app.access_log l
    WHERE l.id > s.cutover_max_id
      AND NOT EXISTS (SELECT 1 FROM app.access_log_legacy o WHERE o.id = l.id);
    GET DIAGNOSTICS v_back = ROW_COUNT;

    v_max := GREATEST(COALESCE((SELECT max(id) FROM app.access_log), 0),
                      COALESCE((SELECT max(id) FROM app.access_log_legacy), 0));

    DROP TRIGGER trg_access_log_sync_to_legacy ON app.access_log;
    ALTER TABLE app.access_log RENAME TO access_log_partitioned;
    ALTER TABLE app.access_log_legacy RENAME TO access_log;
    PERFORM setval(pg_get_serial_sequence('app.access_log', 'id'), GREATEST(v_max, 1), v_max > 0);
    CREATE TRIGGER trg_access_log_copy_to_partitioned
        AFTER INSERT OR DELETE ON app.access_log
        FOR EACH ROW EXECUTE FUNCTION app.access_log_copy_to_partitioned();

    UPDATE app.access_log_partition_state
    SET phase = 'READY',
        copy_target_id = GREATEST(COALESCE(copy_target_id, 0), v_max),
        copy_cursor_id = v_max,
        rolled_back_at = CURRENT_TIMESTAMP,
        updated_at = CURRENT_TIMESTAMP;
    RETURN jsonb_build_object('phase', 'READY', 'rowsReturned', v_back, 'legacyMaxId', v_max);
END
$$;

-- 切换前放弃：删除双写触发器与影子表（含全部分区），旧表不受影响。重新启用需要新的前向迁移。
CREATE FUNCTION app.access_log_partition_abort() RETURNS JSONB
    LANGUAGE plpgsql AS $$
DECLARE
    s app.access_log_partition_state%ROWTYPE;
BEGIN
    PERFORM set_config('lock_timeout', '5s', true);
    SELECT * INTO s FROM app.access_log_partition_state FOR UPDATE;
    IF NOT FOUND OR s.phase NOT IN ('COPYING', 'READY') THEN
        RAISE EXCEPTION 'ACCESS_LOG_PARTITION_NOT_ABORTABLE'
            USING DETAIL = format('phase=%s', COALESCE(s.phase, 'MISSING'));
    END IF;
    DROP TRIGGER IF EXISTS trg_access_log_copy_to_partitioned ON app.access_log;
    DROP TABLE IF EXISTS app.access_log_partitioned;
    UPDATE app.access_log_partition_state
    SET phase = 'ABORTED', aborted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP;
    RETURN jsonb_build_object('phase', 'ABORTED');
END
$$;

-- 6. 状态 -------------------------------------------------------------------

CREATE FUNCTION app.access_log_partition_status() RETURNS JSONB
    LANGUAGE plpgsql STABLE AS $$
DECLARE
    s            app.access_log_partition_state%ROWTYPE;
    v_parent     regclass := app.access_log_partition_parent();
    v_default    regclass;
    v_partitions INTEGER := 0;
    v_default_rows BOOLEAN := FALSE;
    v_source_max BIGINT;
BEGIN
    SELECT * INTO s FROM app.access_log_partition_state;
    IF v_parent IS NOT NULL THEN
        SELECT count(*) INTO v_partitions FROM pg_inherits WHERE inhparent = v_parent;
        v_default := app.access_log_default_partition(v_parent);
        IF v_default IS NOT NULL THEN
            EXECUTE format('SELECT EXISTS (SELECT 1 FROM %s)', v_default) INTO v_default_rows;
        END IF;
    END IF;
    IF s.phase IN ('COPYING', 'READY') THEN
        SELECT max(id) INTO v_source_max FROM app.access_log;
    END IF;
    RETURN jsonb_build_object(
        'phase', s.phase,
        'partitionedTable', (SELECT c.relname::text FROM pg_class c WHERE c.oid = v_parent),
        'partitionCount', v_partitions,
        'defaultPartitionHasRows', v_default_rows,
        'copyTargetId', s.copy_target_id,
        'copyCursorId', s.copy_cursor_id,
        'sourceMaxId', v_source_max,
        'rowsCopied', s.rows_copied,
        'cutoverMaxId', s.cutover_max_id,
        'cutoverAt', s.cutover_at,
        'rolledBackAt', s.rolled_back_at,
        'abortedAt', s.aborted_at,
        'updatedAt', s.updated_at,
        'legacyTablePresent', to_regclass('app.access_log_legacy') IS NOT NULL
    );
END
$$;

-- 7. 预建当前与之后两个月的分区 ----------------------------------------------

SELECT app.access_log_maintain_partitions(2);
