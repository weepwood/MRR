-- 运行日志补齐线程名。
--
-- 背景：PrivateJsonLogEncoder 早已把 thread_name 写进日志文件，但采集端
-- RuntimeLogJournalService.readAndCommit 只取 level/时间/logger/requestId/errorId/
-- message/stack，线程名被整条丢弃，明细表也没有该列；project() 在投影错误组时
-- 写死 threadName="journal"，使错误中心的「线程」字段成为恒定假值。
--
-- 影响：异步任务、线程池、定时任务（scheduling-*、pool-*、lettuce-nioEventLoop-*）
-- 的问题无法与 HTTP 工作线程区分，这是排障最常用的第一刀。
--
-- 加列安全性：runtime_log_event 是 RANGE 分区表，PG 11+ 的 ADD COLUMN 带常量默认值
-- 走 missing value 优化，只改元数据，不重写历史分区；分区自动继承父表列定义。
ALTER TABLE app.runtime_log_event ADD COLUMN thread_name VARCHAR(160) NOT NULL DEFAULT '';

COMMENT ON COLUMN app.runtime_log_event.thread_name IS
    '产生该条日志的线程名；采集时按字符白名单清洗，含控制字符或超过160字符时置空，不再伪造固定值';
