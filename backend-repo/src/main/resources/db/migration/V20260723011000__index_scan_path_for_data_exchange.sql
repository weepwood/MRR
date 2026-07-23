-- 数据交换中心以 folder + filename 作为扫描文件定位键。
-- mr_scan 预计约三千万行，必须使用复合索引避免每条导入记录扫描整个目录。
-- CONCURRENTLY 避免索引构建期间阻塞线上 INSERT、UPDATE 和 DELETE。
-- 先删除同名索引，确保上一次并发构建中断留下的 INVALID 索引不会被误判为已完成。

DROP INDEX CONCURRENTLY IF EXISTS app.idx_mr_scan_folder_filename;

CREATE INDEX CONCURRENTLY idx_mr_scan_folder_filename
    ON app.mr_scan (folder, filename);
