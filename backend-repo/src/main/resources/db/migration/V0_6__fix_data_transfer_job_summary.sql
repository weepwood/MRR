-- 导出工作器需要从汇总视图读取 options 中的 ID 范围和分卷大小。
CREATE OR REPLACE VIEW app.v_data_transfer_job_summary AS
SELECT
    j.id,
    j.direction,
    j.entity_type,
    j.status,
    j.import_mode,
    j.source_type,
    j.total_files,
    j.completed_files,
    j.total_rows,
    j.processed_rows,
    j.valid_rows,
    j.invalid_rows,
    j.inserted_rows,
    j.updated_rows,
    j.skipped_rows,
    j.progress,
    j.current_stage,
    j.current_file_no,
    j.options,
    j.error_message,
    j.created_by,
    j.created_at,
    j.started_at,
    j.completed_at,
    j.heartbeat_at,
    j.updated_at,
    COUNT(f.id) FILTER (WHERE f.status = 'FAILED') AS failed_files
FROM app.data_transfer_job j
LEFT JOIN app.data_transfer_file f ON f.job_id = j.id
GROUP BY j.id;
