-- 历史 mr_scan CSV 常见列：上架号、病案号、病人序号、文件夹、文件名、病案类型、文件大小。
ALTER TABLE app.stg_mr_scan_import
    ADD COLUMN IF NOT EXISTS file_size_raw TEXT,
    ADD COLUMN IF NOT EXISTS file_size BIGINT;
