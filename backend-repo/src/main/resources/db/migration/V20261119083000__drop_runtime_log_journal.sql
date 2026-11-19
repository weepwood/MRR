-- ADR-0008：取消运行日志 journal 功能（生产从未启用，表为空）。
-- 历史迁移 V20261018102000 / V20261119030000 保持不变，本迁移只做前向删除。
-- runtime_error_projection_receipt 保留：运行错误中心 upsert 防重仍在使用。
DROP TABLE IF EXISTS app.runtime_log_event;
DROP TABLE IF EXISTS app.runtime_log_source;
DROP TABLE IF EXISTS app.runtime_log_capacity_sample;
