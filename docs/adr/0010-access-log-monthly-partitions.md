# ADR-0010：access_log 按月分区（影子表双写 + 手动切换）

- 状态：Proposed
- 日期：2026-11-23
- 决策者：待评审（MRR 维护者）
- 关联 Issue：#694
- 关联 PR：待创建
- 替代 ADR：无

## 背景

**已确认事实**

- `app.access_log` 由 `LogInterceptor` → `AsyncLogService` 对每个 API 请求写一行，默认保留 1095 天（`app.log-retention.retention-days`）。
- `LogRetentionCleaner` 每晚 02:30 以 `DELETE … WHERE id IN (SELECT id … LIMIT 5000)` 分批删除，单次最多 20 批（10 万行）。日增量超过 10 万行时清理永远追不上；大批量 DELETE 产生死元组，需要 VACUUM 回收，表与索引持续膨胀。
- 表上有 13 个索引（含两个 GIN trigram），每次 DELETE 都要维护全部索引。
- 仓库已有按月分区的先例（`V20261018102000__recoverable_runtime_log_journal.sql`，后由 ADR-0008 删除）。
- 仓库内没有代码依赖 `uk_access_log_request_id` 的唯一性（无 `ON CONFLICT (request_id)`、无 `DuplicateKeyException` 处理）。
- 仓库内没有对 `access_log` 的运行期 `UPDATE`；读路径均为 `LogMapper`、`ResponseMetricMapper`、`OperationsCenterService`、`QueryDiagnosticsService` 与数据库浏览注册表。

**推断**

- 正式库 access_log 在千万行量级，`ALTER TABLE … PARTITION` 无法原地转换；一次性 `INSERT … SELECT` 复制需要长事务和数倍 WAL，不适合在停机窗口内完成。

**未知项**

- 各现场 access_log 的实际行数、日增量、磁盘余量；切换期间（单次等锁超时配置为 5 秒）应用写入的排队情况。

## 决策

新增迁移 `V20261123170000__access_log_monthly_partitions.sql`。安装触发器需要短时表锁，超时则回滚整个迁移。迁移不复制或改写现有数据。

1. 影子表 `app.access_log_partitioned`：列与 `access_log` 一致，`PARTITION BY RANGE (access_time)` 按月分区，外加默认分区 `access_log_default`（`access_time` 为空或月份尚未建分区）。分区命名 `access_log_yYYYYmMM`。
2. `AFTER INSERT OR DELETE` 触发器把旧表的新增与删除同步到影子表。复制只锁定当前批次的源行，避免把已清理记录写回。
3. 状态表 `access_log_partition_state`（单行）：`COPYING → READY → CUTOVER`，可 `rollback` 回 `READY`，或在切换前 `ABORTED`。
4. 函数：
   - `access_log_partition_copy_batch(limit)`：按 id 升序复制一批旧数据，一次调用一个事务；以 `NOT EXISTS` 跳过触发器已写入的行，可中断、可重复执行；游标追上首次批次时的 `max(id)` 后进入 `READY`。
   - `access_log_ensure_partition` / `access_log_maintain_partitions(n)`：预建当前及之后 n 个月的分区；默认分区里已有某月数据时先迁出再 `ATTACH`。
   - `access_log_drop_expired_partitions(cutoff)`：切换完成且旧表退役后，才 `DETACH` + `DROP` 上界不晚于 cutoff 的整月分区。
   - `access_log_partition_cutover()`：咨询锁 → `ACCESS EXCLUSIVE` 锁两张表（`lock_timeout=5s`）→ 补齐尾部 → 删除触发器 → `access_log` 改名为 `access_log_legacy`、影子表改名为 `access_log` → 序列推进到两表最大 id 之后，并开启从分区表到旧表的新增和删除同步。
   - `access_log_partition_rollback()`：禁用新表到旧表的同步，换回表名，恢复旧表到分区表的同步与旧表序列。
   - `access_log_partition_abort()`：切换前删除触发器与影子表。
   - `access_log_partition_status()`：阶段、复制进度、分区数、默认分区是否有数据。
5. 应用侧 `AccessLogPartitionService`：
   - 维护任务每小时（`app.access-log-partition.maintenance-cron`，默认每小时 15 分）及启动时执行：预建分区；旧表退役且访问日志保留开启时，按保留天数删除整月分区。过渡和回退期间继续由 `LogRetentionCleaner` 分批删除。
   - 复制任务默认关闭（`APP_ACCESS_LOG_PARTITION_COPY_ENABLED=false`），开启后每分钟一轮、每轮最多 20 批 × 5000 行。
   - 运维接口 `/api/v1/operations/access-log-partition/*`：`GET status`（`system:read`），`POST copy|maintain|cutover|rollback|abort`（`system:manage`）；切换、回退、放弃写入 `SECURITY_AUDIT`。
6. 读写 SQL 不改：切换后表名仍为 `access_log`，PostgreSQL 在表改名后重新解析已缓存的预备语句。

## 必须保持的不变量

- 任何阶段，应用写入的每条访问日志都进入当前名为 `access_log` 的表；切换前的影子表只是副本。
- 复制可以在任意批次中断，恢复后不重复、不遗漏（以 id 去重，以游标续跑）。
- 切换、回退、放弃只能由具备 `system:manage` 的人员手动触发。安全审计日志记录操作、结果与 `requestId`，通过该 ID 关联同一请求的访问审计和操作者。
- 不修改已发布的 Flyway 迁移；放弃后重新启用必须通过新的前向迁移。
- 分区删除只作用于整月分区，且仅在访问日志保留开启、保留天数大于 0 时执行；默认分区永不被整体删除。

## 方案边界

### 包含

- access_log 的分区结构、双写、复制、切换、回退、放弃、分区维护与保留期整月删除。
- 数据库浏览对分区表的行数估算（按分区求和）。

### 不包含

- 删除 `access_log_legacy`：观察期（建议 ≥ 30 天）结束后由后续迁移删除。
- 审计日志可靠性（同步写审计、丢弃计数，路线图 §2.3）。
- 其他大表（`frontend_response_metric`、`mr_external_archive_access_log`）分区。
- 前端页面；状态与操作暂通过接口与 `mrrctl`/psql 使用。

## 替代方案

### 方案 A：停机窗口内 `CREATE TABLE … PARTITION BY` + 一次性 `INSERT … SELECT`

- 优点：步骤少，无双写阶段。
- 缺点：千万行单事务复制耗时长、WAL 与磁盘峰值大，失败需整体重来；停机窗口不可控。
- 未采用原因：医院内网停机窗口有限，且无法中断续跑。

### 方案 B：pg_partman / pg_cron 扩展

- 优点：成熟的分区自动维护。
- 缺点：Windows Server 现场需要额外安装与升级扩展，增加部署依赖。
- 未采用原因：与“Windows 原生部署、少依赖”的约束冲突；维护逻辑只有几十行 PL/pgSQL。

### 方案 C：只调大清理批次 / 改为按时间范围 DELETE

- 优点：改动最小。
- 缺点：仍有死元组与膨胀，VACUUM 压力不变；只是推迟问题。
- 未采用原因：不解决根因。

## 影响

### 正面影响

- 保留期清理从“每晚删 10 万行”变为“每月删一个分区”，耗时与表大小无关，不产生死元组。
- 按时间范围的审计查询可做分区裁剪；单个分区的索引更小。

### 负面影响与技术债

- 过渡期双写使每条日志多一次插入（同一事务内）；影子表异常时会导致访问日志写入失败，需监控并可 `abort`。
- 复制期间旧表与影子表共存。两者索引结构不同，磁盘占用应以实测为准。
- 分区表不能直接约束 `request_id` 全局唯一。旧表退役前，同步写入旧表继续执行其唯一约束。旧表退役的前向迁移须明确后续唯一性策略。
- 旧索引名（`idx_access_log_*`、`uk_access_log_request_id`）随旧表改名留在 `access_log_legacy` 上。后续迁移在新 `access_log` 上建索引必须使用新名称，**不能**写 `CREATE INDEX IF NOT EXISTS idx_access_log_…`（会因同名索引存在而静默跳过）。
- `pg_stat_user_tables` 会逐个列出分区；数据库监控页的表大小排行会出现多个 `access_log_y…` 行。

### 安全、隐私与审计

- 双写与复制不改变字段内容，脱敏规则不变；`request_body` 等敏感列在数据库浏览中仍整列不返回。
- 切换后的新增和普通删除都同步到旧表，已清理的数据回退后也不会重新出现。
- 整月删除只在旧表退役且保留期开启后生效。默认分区继续按既有条件分批清理。

### 数据库、性能与容量

- 复制默认 5000 行/批、20 批/轮、1 轮/分钟（约 10 万行/分钟），每批一个短事务；可按现场 IO 调整。
- 建分区需要父表的短时排他锁，函数设置 `lock_timeout=3s`，拿不到锁报错并在下一轮重试，避免排在长查询后堵塞日志写入。
- 切换和回退在拿锁前配置 `lock_timeout=5s`，限制单次等待。补尾部、改名及安装触发器持有 `ACCESS EXCLUSIVE` 锁的实际时间仍需压测。

### Chrome 86、Windows 与部署兼容性

- 无前端变更；无新增 PostgreSQL 扩展；PostgreSQL 16 原生声明式分区。

## 迁移方案

1. 部署包含本迁移的版本：Flyway 只建对象（秒级），触发器立即开始双写。
2. 按现场副本实测影子分区及索引大小、复制产生的 WAL 和磁盘峰值，给三者预留空间。分批复制可暂停，空间不足时先关闭复制，再决定是否放弃迁移。
3. 设置 `APP_ACCESS_LOG_PARTITION_COPY_ENABLED=true` 并重启（或在低峰用 `POST /copy` 手动推进）；观察 `GET /status` 的 `copyLag` 与指标 `mrr.access_log.partition.copy.lag`。
4. `phase=READY` 后在低峰调用 `POST /cutover`（维护模式会拦截该请求，执行前关闭维护模式）。
5. 抽查访问日志、影像审计、响应分析、运维中心、查询诊断页面；观察至少 30 天。
6. 关闭复制开关；后续版本新增迁移先移除分区表到旧表的同步触发器，再退役 `access_log_legacy` 与迁移状态、专用函数，保留维护函数。

## 回滚与前向修复

- 切换前：`POST /abort` 删除触发器与影子表，旧表不受影响；或直接关闭复制开关并保持现状。应用回退到旧版本时触发器仍在工作（旧应用只写 `access_log`），无兼容问题。
- 切换后：`POST /rollback` 换回持续同步的旧表，状态回到 `READY`，可择期再次切换。旧表退役前暂停整月删除。
- 已切换且需要回退应用版本时，先执行 rollback 再回退应用。
- 已发布迁移不得回写，结构问题使用新的前向迁移。

## 验证

- 单元测试：`AccessLogPartitionServiceTest`（复制开关、批次上限与提前结束、指标、保留期删除条件、错误映射）。
- PostgreSQL 集成测试：`AccessLogPartitionPostgresqlIT`（迁移到上一版本后写入历史数据，再执行本迁移，覆盖双写、分批复制去重、默认分区迁出、切换、整月删除、回退、放弃、Flyway 重跑、分区表行数估算）。
- E2E 或手工流程：在测试库按“迁移方案”完整演练一次，含复制过程中重启应用、切换后回退再切换。
- 本地 PostgreSQL 16.16 合成日志 25 万行：50 批复制用时 3.51 秒，切换约 44 毫秒，回退约 10 毫秒，前后行数一致。旧表约 83 MB，分区合计约 137 MB，均含索引；未测高峰并发和 WAL 峰值，医院启用前仍需在对应数据规模复验。
- 生产或灰度验证：先在一个现场开启复制并观察一周，再切换。

## 后续事项

- [ ] 后续迁移：删除 `access_log_legacy` 与迁移专用函数。
- [ ] 运维中心增加分区状态卡片（当前仅接口）。
- [ ] `mrrctl.ps1 doctor` 检查默认分区是否有数据、未来分区是否已预建。
