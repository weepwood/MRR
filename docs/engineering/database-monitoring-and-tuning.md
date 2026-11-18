# 数据库监控与调优体系

本文定义 MRR 在 PostgreSQL 16 上的监控分层、指标采集、告警阈值、瓶颈诊断、调优流程、职责分工与效果评估标准。它是 `monitoring/README.md`（部署手册）之上的**运行规范**：README 讲“怎么装”，本文讲“看什么、什么时候报警、出了问题按什么顺序查、改完怎么算成功”。

适用边界：

- 生产形态为医院内网 Windows 单机：Nginx + Spring Boot 4 单体 + PostgreSQL 16，不使用 Docker；
- 不新增独立监控平台。Prometheus / Grafana / Alertmanager / postgres_exporter 已在本仓库 `monitoring/` 内维护，本体系只做**扩展与补齐**；
- 不涉及 DICOM 诊断工作站，不改变 Flyway 迁移历史；
- 本文所有指标均不含患者姓名、病案号、身份证等标识；`pg_stat_statements` 的查询文本**永不进入指标 label**，只在受控诊断报告中人工查看。

## 1. 现状与缺口

已有能力（2026-11 核对 `monitoring/` 实测）：

| 能力 | 落地物 | 覆盖 |
| --- | --- | --- |
| 可用性 | `prometheus/rules/mrr-alerts.yml` | 后端 / PostgreSQL 存活 |
| 实例资源 | postgres_exporter 内建指标 + `postgresql-overview` 看板 | 连接数、死锁、缓存命中、库大小、事务速率、临时文件 |
| 连接池 | Actuator + Hikari 指标 | 活跃连接、等待线程、获取超时 |
| 主机容量 | windows_exporter / node_exporter | 磁盘水位 |
| 数据质量 | 手动检查接口 + `mrr-data-quality` 看板 | 业务数据异常计数 |

缺口（本体系要补的部分）：

1. **查询级不可见**：`pg_stat_statements` 扩展已安装，但没有任何指标、看板或告警消费它。慢 SQL 只能靠用户投诉发现。
2. **告警标签错配**：Actuator 统一标签是 `management.metrics.tags.application=mrr`，而规则与看板里写的是 `application="imgapi"`。这些表达式选中的是空向量，**Hikari 三条关键告警此前不会触发**，应用看板的面板也是空白。本次已统一改为 `application="mrr"`。
3. **无膨胀与索引治理**：死元组、表膨胀、未使用索引没有任何测量，`mr_scan`（三千万级）与按月分区的 `runtime_log_event_*` 长期只增不减。
4. **无慢 SQL 日志**：`log_min_duration_statement = -1`，出问题时没有事后证据链。
5. **无调优流程与基线**：没有“改前快照 → 改后验证”的固定动作，效果无法证明。
6. **告警只报不指路**：缺少 runbook 指向，值班人员看到告警后仍要从零开始查。

## 2. 分层模型

监控按“回答的问题”分四层，避免把所有指标堆在一个看板里：

| 层 | 回答的问题 | 典型指标 | 消费者 | 响应方式 |
| --- | --- | --- | --- | --- |
| L0 可用性 | 服务还在吗 | `up`、`pg_up`、`hikaricp_connections_timeout_total` | 值班 | 立即处置 |
| L1 实例资源 | 资源够不够 | 连接使用率、缓存命中率、临时文件、磁盘、长事务 | 值班 + DBA | 立即处置或当天 |
| L2 语句与计划 | 谁在拖慢 | 平均语句延迟、慢语句数、最慢语句均值、膨胀、死元组 | 研发 + DBA | 周度清单 |
| L3 业务数据质量 | 数据对不对 | 病案号/上架号缺失、重复页、OSS 未校验 | 数据治理 | 排期治理 |

配套原则：

- **L0/L1 进告警，L2 主要进看板与周度体检**。语句级指标波动大，做成即时告警会制造噪声；只有“延迟回归”这种整体性变化才升级为告警。
- **L3 保持现状**：手动触发、只统计计数，不引入定时任务，不把患者标识做成 label。
- 每层都要能从告警一步跳到诊断材料，否则等于没监控。

## 3. 指标与采集

### 3.1 采集链路

```text
PostgreSQL 16
  ├─ pg_stat_* 视图 ──postgres_exporter(9187, mrr_monitor 只读)──┐
  ├─ pg_stat_statements（扩展 + 预加载）──queries.yaml 聚合────────┤
Spring Boot 4                                                    ├─ Prometheus(9090) ─┬─ Alertmanager(9093) → 值班
  └─ Actuator /actuator/prometheus(18046, 仅本机) ───────────────┘                     └─ Grafana(3000) → 三个看板
windows_exporter(9182) ── 磁盘 / CPU ────────────────────────────────────────────────────┘
```

- 采集账号 `mrr_monitor`：`pg_monitor` 角色 + 只读，不授予写权限；口令由操作系统服务管理器注入，**不写入仓库**。
- 抓取间隔 15s（`prometheus/prometheus.yml`）；自定义重查询用 `cache_seconds` 单独降频，避免 15s 一次全表扫描 `pg_stats`。
- 保留期 30 天，够覆盖“发版后一周回归”和月度体检。

### 3.2 服务端必须开启的参数

`postgresql.conf`（`shared_preload_libraries` 需重启，其余 `SELECT pg_reload_conf()` 生效）：

| 参数 | 建议值 | 作用 | 生效方式 |
| --- | --- | --- | --- |
| `shared_preload_libraries` | `'pg_stat_statements'` | 语句统计的前提 | 重启 |
| `pg_stat_statements.max` | `10000` | 覆盖 MRR 的语句种类 | 重启 |
| `pg_stat_statements.track` | `all` | 包含嵌套语句（Mapper 嵌套调用） | 重启 |
| `compute_query_id` | `on` | 关联日志与统计 | 重载 |
| `track_io_timing` | `on` | 让 `pg_stat_io.read_time` 有值 | 重载 |
| `log_min_duration_statement` | `2000`（先 2s，观察日志量） | 慢 SQL 事后取证 | 重载 |
| `log_lock_waits` | `on` | 记录锁等待超过 `deadlock_timeout` 的事件 | 重载 |
| `log_temp_files` | `10240`（10MB） | 谁在写临时文件 | 重载 |
| `log_autovacuum_min_duration` | `10000` | autovacuum 行为留痕 | 重载 |
| `log_line_prefix` | `'%m [%p] %q%u@%d '` | 日志可与 pid / 库名对齐 | 重载 |

约束：`log_min_duration_statement` 会放大日志量。上线前先按“峰值 QPS × 超标比例 × 单条日志大小”估算日增量，并确认 `deploy/` 的日志轮转与磁盘水位告警有效（已有 15% / 8% 两级）。日志正文含参数时按现有隐私红线处理，不得外传。

### 3.3 指标清单

| 层 | 指标 | 来源 | 用途 |
| --- | --- | --- | --- |
| L0 | `up{job="mrr-postgresql"}`、`pg_up` | exporter | 存活 |
| L0 | `hikaricp_connections_timeout_total` | Actuator | 池获取超时（用户可见故障） |
| L1 | `pg_stat_activity_count` / `pg_settings_max_connections` | exporter | 连接水位 |
| L1 | `hikaricp_connections_pending` / `_active` / `_max` | Actuator | 池饥饿先于超时出现 |
| L1 | `pg_stat_database_blks_hit` / `_blks_read` | exporter | 缓存命中 |
| L1 | `pg_stat_database_temp_bytes` | exporter | 排序/哈希溢出 |
| L1 | `pg_stat_database_deadlocks` | exporter | 死锁 |
| L1 | `mrr_pg_transactions_longest_xact_seconds` | queries.yaml | 长事务 |
| L1 | `mrr_pg_transactions_longest_idle_xact_seconds` | queries.yaml | 空闲事务（膨胀根因） |
| L2 | `mrr_pg_stat_statements_exec_seconds_total` / `_calls_total` | queries.yaml | 由 `rate()` 导出平均延迟 |
| L2 | `mrr_pg_stat_statements_slow_200ms` / `_slow_1s` | queries.yaml | 病态语句数量 |
| L2 | `mrr_pg_stat_statements_worst_mean_seconds` | queries.yaml | 单条最差语句 |
| L2 | `mrr_pg_stat_statements_shared_hit_total` / `_shared_read_total` | queries.yaml | 语句级缓存命中 |
| L2 | `mrr_pg_stat_statements_temp_blocks_total` | queries.yaml | 临时块来源 |
| L2 | `mrr_pg_bloat_max_ratio` / `_max_bytes` / `_tables_over_20pct` | queries.yaml | 膨胀 |
| L2 | `mrr_pg_maintenance_dead_tuple_ratio` / `_tables_needing_vacuum` | queries.yaml | autovacuum 健康度 |
| 自检 | `absent(mrr_pg_stat_statements_calls_total)`、`pg_exporter_last_scrape_error` | 规则 | 采集盲区 |

**基数纪律（硬约束）**：`queryid`、`query` 文本、`relname`、`indexrelname` 一律不做 label。单条语句明细只走 `diagnostics/` 的 SQL 报告，不进 Prometheus。这条规则决定了“为什么查询级指标看起来这么少”——是有意为之，不是遗漏。

逐语句的“哪条 SQL 最慢”由人读报告回答：`monitoring/postgresql/diagnostics/01-slow-queries.sql`（累计耗时、单次均值、N+1 嫌疑、重复规划、临时文件五个视角）。

## 4. 告警与阈值策略

### 4.1 严重级别语义

只有两级，避免“info 没人看”：

| 级别 | 含义 | 响应时限 | 通知方式 |
| --- | --- | --- | --- |
| `critical` | 业务已受损或即将受损（连接耗尽、库不可用、命中率崩、死锁） | 15 分钟内确认 | 立即推送值班 |
| `warning` | 趋势异常，尚有缓冲（80% 连接、长事务、膨胀、慢语句累积） | 次工作日内处理 | 汇总推送 / 日报 |

规则里统一带 `runbook_url`，指向具体诊断文件或本文档，值班不需要现场猜。

### 4.2 阈值与依据

| 指标 | warning | critical | 持续时间 | 依据 |
| --- | --- | --- | --- | --- |
| 连接使用率 | > 80% | > 90% | 5m / 2m | 应用池上限 20，`max_connections` 100；80% 时已无余量给批量任务与管理会话 |
| Hikari 等待线程 | > 0 | — | 2m | 池饥饿先于超时出现，是用户超时的先行指标 |
| Hikari 活跃占比 | — | > 90% | 2m | 与池上限比，而非与数据库上限比 |
| Hikari 获取超时 | — | > 0（5m 内） | 立即 | 已产生用户可见失败 |
| 死锁 | — | > 0（5m 内） | 立即 | 任何死锁都是代码加锁顺序问题 |
| 平均语句延迟 | > 500ms | > 2s | 15m / 10m | 页面 SLO 3s，数据库预算约 1/3；2s 时页面必然超时 |
| 慢语句（>1s 均值） | > 10 条 | — | 1h | 基线体检信号，非事故信号 |
| 缓存命中率 | < 99% | < 95% | 15m / 5m | OLTP 经验目标；<95% 时随机读放大明显 |
| 临时文件 | > 200MB / 10m | — | 10m | 单次大导出约 200MB 量级，超出说明排序溢出常态化 |
| 长事务 | > 300s | — | 5m | 远超正常业务事务，且会阻塞 autovacuum |
| 空闲事务 | > 600s | — | 5m | 连接泄漏或未提交分支的典型特征 |
| 死元组占比 | > 20% 且 > 1 万条 | — | 30m | 超过 20% 后扫描成本与索引膨胀加速；1 万条下限用于过滤小表整表 UPDATE 造成的假告警 |
| 表膨胀 | > 30% 且 > 1GiB | — | 6h | 小表膨胀无治理价值，加体积门槛降噪 |
| 磁盘可用 | < 15% | < 8% | 10m / 5m | 预留 WAL、临时文件与备份空间 |

阈值维护规则：

- 初始值是工程判断，**上线 2 周后必须用真实数据校准一次**，记录在月度体检里；
- 调整阈值视为配置变更，需在 PR 里写明“为什么原阈值产生误报/漏报”，不接受“为了消警而调高”；
- 新规则上线前先以 `warning` 观察一周，再决定是否升 `critical`。

### 4.3 降噪与抑制

- **分组**：按 `alertname + severity` 聚合，同一根因只推一条。
- **抑制**：`MrrPostgreSQLDown` 生效时抑制所有依赖数据库指标的告警；主机磁盘 `critical` 抑制同级 `warning`。
- **静默**：仅限已登记维护窗口（迁移、VACUUM FULL、压测），静默必须写原因与到期时间，禁止长期静默。
- **盲区告警优先于业务告警**：`MrrQueryMetricsMissing` 触发时，先修采集，否则查询级告警全部是“假绿”。

## 5. 瓶颈分析与诊断

### 5.1 诊断入口

| 现象 | 第一动作 | 命令 |
| --- | --- | --- |
| 页面整体变慢、连接超时 | 看连接与池 | `diagnostics/06-connections.sql` |
| 某个操作卡住、保存无响应 | 看锁与长事务 | `diagnostics/04-locks-and-transactions.sql` |
| 报表/导出慢、磁盘 IO 高 | 看慢 SQL 与临时文件 | `diagnostics/01-slow-queries.sql` |
| 数据量没涨但越来越慢 | 看膨胀与统计信息 | `diagnostics/03-bloat-and-vacuum.sql` |
| 命中率低、读盘多 | 看容量与 IO 分布 | `diagnostics/05-capacity-io.sql` |
| 怀疑索引缺失或冗余 | 看索引使用 | `diagnostics/02-index-usage.sql` |
| 需要留证据 / 前后对比 | 生成基线快照 | `python scripts/db_performance_snapshot.py --label <标签>` |

一键取全量证据：

```powershell
$env:MRR_DB_DSN = "postgresql://mrr_monitor:***@127.0.0.1:5432/imgapi"
python scripts/db_performance_snapshot.py --psql "C:\PostgreSQL\bin\psql.exe" --label incident-20261119
```

输出写入 `.workbuddy/db-snapshots/<时间戳>-<标签>/`（已 gitignore），包含 `snapshot.json`、`summary.md` 与六份诊断原文。**DSN 口令不会落盘**，但报告正文可能含字面量，只允许内网留存。

### 5.2 瓶颈定位决策树

按“从外到内、从便宜到昂贵”的顺序，每层先排除再下钻：

```text
① 是数据库整体慢，还是某个功能慢？
   ├─ 整体：看 mrr_pg_stat_statements 平均延迟 + 连接使用率
   │    ├─ 连接使用率高 / Hikari pending > 0 → ②
   │    └─ 连接正常但延迟高 → ③
   └─ 单功能：直接 ④

② 连接层
   ├─ 有 idle in transaction → ④ 找持有者，先处理事务边界，不动参数
   ├─ 有长事务 → 同上
   ├─ 无异常会话但池满 → 并发度问题：限流 / 批任务错峰
   └─ 以上都不是 → 才考虑池与 max_connections（最后手段）

③ 等待类型分类（04 的第 4 段）
   ├─ Lock / LWLock → 回到 ②
   ├─ IO: DataFileRead → ⑤
   ├─ IO: BufFileWrite → 临时文件，回到 ④ 看排序
   └─ CPU（无等待）→ 语句本身成本，进入 ④

④ 单条语句
   1) 01 报告定位：累计耗时高 → 高频；单次均值高 → 语句本身重
   2) EXPLAIN (ANALYZE, BUFFERS)，先 SET statement_timeout = '5s'
   3) 判读三条：估计行数 vs 实际行数（偏差 >10 倍 → 统计信息/相关性）
                  扫描方式（mr_scan 上出现 Seq Scan 即为缺陷）
                  缓冲区（shared read 高 → 缓存不足或扫描范围过大）
   4) 对照 02 报告：是否缺索引、索引是否失效（表达式/类型不匹配）、是否有冗余索引拖慢写入

⑤ 实例资源
   ├─ 命中率 < 99% → shared_buffers 与扫描范围
   ├─ 临时文件高 → work_mem（会话级优先于全局）
   ├─ 膨胀高 → 03 报告 + 维护窗口
   └─ 磁盘 IO 饱和 → 先减少扫描量，再谈硬件
```

### 5.3 EXPLAIN 判读规范

- 生产环境只允许 `EXPLAIN (ANALYZE, BUFFERS)`，**且必须**先设置 `statement_timeout`，禁止对大表跑无边界 `ANALYZE`；
- 关注 `rows` 的 est vs actual：偏差大先 `ANALYZE` 该表，再考虑 `CREATE STATISTICS` 或改写；
- `Seq Scan` 出现在 `mr_scan`、`access_log`、`runtime_log_event_*` 上，一律视为待修问题；
- 计划里的 `Filter` 移除行数远大于返回行数，说明索引区分度不够或谓词不可索引；
- 不把“换了执行计划”当作结论，必须给出前后 `actual time` 与缓冲区对比。

## 6. 调优流程与规范

### 6.1 七个步骤

| 步骤 | 动作 | 产出 | 门禁 |
| --- | --- | --- | --- |
| S0 受理 | 记录触发源（告警/报障/体检/发版回归）、影响功能、时间窗 | 问题单 | 现象可复现或有指标证据 |
| S1 基线 | 跑 `db_performance_snapshot.py --label before-*` | `snapshot.json` + 诊断原文 | 基线必须来自变更前环境 |
| S2 定位 | 按 5.2 决策树下钻 | 瓶颈假设 + 支撑证据（计划/锁链/报告片段） | 没证据不动手 |
| S3 实验 | 最小改动验证假设；必要时在只读副本或影子库复现 | 实验结果 | 不改生产数据、不改迁移历史 |
| S4 实施 | 按变更分级走审批；迁移只能新增前向脚本 | 变更记录 + 回滚点 | P1 及以上需 ADR/变更评审 |
| S5 验证 | `--label after-*` 并与 before 对比；业务侧验证 | 前后对比表 | 主指标达标且护栏不回归 |
| S6 固化 | 更新索引/查询规范、阈值、基线归档、周报 | 规范更新 + 复盘 | 7 天观察期结束 |

### 6.2 变更分级

| 类型 | 示例 | 风险 | 审批 | 回滚 |
| --- | --- | --- | --- | --- |
| 只读诊断 | 诊断 SQL、`EXPLAIN` | P3 | 无 | 不适用 |
| 查询改写 | 分页、去掉 N+1、限定列 | P2 | 研发负责人 | 回滚代码 |
| 新增索引 | `CREATE INDEX CONCURRENTLY` | P2 | 研发负责人 + DBA | `DROP INDEX CONCURRENTLY` |
| 参数调整 | `work_mem`、单表 autovacuum 覆盖 | P2 | DBA + 运维负责人 | `ALTER ... RESET` |
| 删除索引 | 连续 30 天 `idx_scan = 0` 且非唯一/非约束 | P1 | 变更评审 | 重建脚本（需保留） |
| 结构变更 | 分区、类型调整、大表重写 | P1 | 变更评审 + ADR | 前向修复迁移 |
| 空间回收 | `VACUUM FULL` / `pg_repack` | P1 | 停机窗口 + 备份确认 | 备份还原 |

### 6.3 索引规范

- 生产新建索引一律 `CREATE INDEX CONCURRENTLY`，且**不能**放在事务块内（Flyway 迁移里需拆成独立语句并标注）；
- 上线前确认：`pg_stat_user_indexes` 中确实存在对应慢语句、`EXPLAIN` 能从 Seq Scan 变为 Index Scan；
- 复合索引遵循“等值在前、范围在后”，并核对 `mr_scan` 的查询条件顺序（`bah` / `sjh` / 上架号）；
- 表达式索引必须与 Mapper 中的谓词**逐字一致**，否则不会被使用（`V8` 的 trigram 索引就是这种约束）；
- 删除索引前必须同时满足：30 天窗口、`idx_scan = 0`、非唯一、非约束支撑、报告留档；三项缺一不可。

### 6.4 明确禁止

- 用调大 `max_connections` 解决连接问题（先查泄漏与长事务）；
- 在生产对大表执行无 `statement_timeout` 的 `EXPLAIN ANALYZE`；
- 为消除告警而直接调高阈值，且不记录原因；
- 删除“看起来没用”的索引而不做观察窗口；
- 业务高峰执行 `VACUUM FULL`、`REINDEX`、大表 `ALTER`；
- 把 `pg_stat_statements.query` 文本、含患者信息的日志贴进 Issue / PR / 截图；
- 修改已进入 main 的 Flyway 迁移（只能新增前向修复脚本）。

## 7. 职责划分

| 活动 | 数据库责任人(DBA) | 后端研发 | 值班运维 | 数据治理 | 变更审批 |
| --- | --- | --- | --- | --- | --- |
| 指标采集与看板维护 | **A/R** | C | C | I | I |
| 告警规则与阈值 | **A/R** | C | C | I | C |
| L0/L1 告警响应 | C | I | **A/R** | I | I |
| 慢 SQL 定位 | C | **A/R** | I | I | I |
| 查询改写与索引设计 | C | **R** | I | I | **A** |
| 参数与结构变更 | **R** | C | C | I | **A** |
| 膨胀与空间治理 | **A/R** | I | C | I | C |
| L3 数据质量治理 | C | C | I | **A/R** | I |
| 月度体检与复盘 | **A/R** | C | C | C | I |

`A` 负责、`R` 执行、`C` 需协商、`I` 需知会。单机部署下 DBA 与运维常由同一人兼任，但**审批与执行不得同一人**（P1 变更）。

## 8. 效果评估标准

### 8.1 体系健康度（每月）

| 指标 | 目标 | 计算方式 |
| --- | --- | --- |
| 关键指标覆盖率 | 100% | 表 3.3 中 L0/L1/L2 指标均有数据 |
| 采集盲区 | 0 | `MrrQueryMetricsMissing`、`MrrTransactionMetricsMissing` 未触发 |
| 告警有效率 | ≥ 70% | 产生有效处置动作的告警 / 总告警 |
| MTTD | ≤ 5 分钟 | 故障发生到告警触发 |
| MTTA | ≤ 15 分钟 | critical 告警确认时间 |
| MTTR | ≤ 2 小时 | 数据库类故障恢复时间 |
| 月度体检完成率 | 100% | 已出报告的月份 / 应出月份 |
| 过期静默 | 0 | 未按到期时间解除的静默 |

### 8.2 单次调优验收

必须同时满足，缺一项不算完成：

| 维度 | 标准 |
| --- | --- |
| 主指标 | 目标语句平均耗时下降 ≥ 30%，或达到约定目标值（如 < 200ms） |
| 整体护栏 | 全库平均语句延迟不回归超过 10%；缓存命中率不下降；临时文件不增加 |
| 写入代价 | 新增索引后，相关表写入延迟与 WAL 增长在可接受范围（对比 before 快照） |
| 稳定性 | 连续观察 7 天，无新增慢语句、无死锁新增 |
| 回滚 | 回滚脚本/命令已实际演练过一次 |
| 证据 | before/after 两份 `snapshot.json` 与对比表随变更记录归档 |

### 8.3 业务侧初始目标（待与业务确认）

| 场景 | 目标 |
| --- | --- |
| 病案查询（单条件） | P95 ≤ 1s |
| 影像档案袋列表 | P95 ≤ 1.5s |
| 统计报表首屏 | P95 ≤ 3s |
| 大批量导出 | 任务受理 ≤ 1s，进度可查，不阻塞其他查询 |

这几项目前是工程建议值，不是已确认的 SLO。首次基线采集后应与业务方对齐并写入验收标准；在确认前，只能作为回归对比的参考线。

## 9. 落地方式

| 模块 | 落地物 | 部署动作 | 验证方式 |
| --- | --- | --- | --- |
| 服务端参数 | `postgresql.conf` 增量（见 3.2） | 维护窗口重启 + 重载 | `SHOW pg_stat_statements.max`、`SHOW log_min_duration_statement` |
| 扩展与监控账号 | `monitoring/postgresql/enable-monitoring.sql` | 管理员执行 | `SELECT * FROM pg_stat_statements LIMIT 1` |
| 查询级采集 | `monitoring/postgresql/queries.yaml` | exporter 加 `--extend.query-path` | `curl 127.0.0.1:9187/metrics \| findstr mrr_pg_` |
| 告警规则 | `monitoring/prometheus/rules/mrr-alerts.yml` | 重载 Prometheus | `promtool check rules`、Alertmanager 看到规则 |
| 看板 | `monitoring/grafana/dashboards/postgresql-query-performance.json` | provisioning 自动加载 | 面板有曲线、无 “No data” |
| 诊断 | `monitoring/postgresql/diagnostics/*.sql` | 无需部署 | 用 `mrr_monitor` 实跑通过 |
| 基线 | `scripts/db_performance_snapshot.py` | 值班机可用 Python 3 | `--label selftest` 产出 `summary.md` |
| 流程 | 本文 + PR 模板 | 纳入 onboarding | 调优单均附带前后快照 |

### 9.1 分阶段推进

**阶段一（约 1 周）：补盲区**

1. 维护窗口开启 `pg_stat_statements` 预加载与 3.2 的日志参数；
2. exporter 加载 `queries.yaml`，确认 `/metrics` 出现 `mrr_pg_*`；
3. 导入更新后的规则，确认 `MrrQueryMetricsMissing` 从 firing 转 resolved；
4. 导入新看板，核对四张 stat 面板有值。

验收：新看板无空面板；`absent()` 系列告警不触发；应用看板 Hikari 面板恢复数据（标签已修正）。

**阶段二（2–3 周）：建立基线**

1. 每周一自动/手动跑一次快照，形成 4 周基线；
2. 首次全量诊断，输出《数据库基线体检报告》（膨胀 TOP、未使用索引、慢语句 TOP）；
3. 用真实数据校准一次阈值，记录调整理由；
4. 清理高价值项：明显缺索引的慢语句、长期未使用且占空间大的索引（走 P1 流程）。

验收：有一份可对比的基线；告警有效率 ≥ 70%；至少 1 项调优完成闭环。

**阶段三（持续）：规范运行**

1. 每次发版后 24 小时内看延迟趋势是否回归；
2. 月度体检 + 季度索引与膨胀治理；
3. 季度复盘：阈值、覆盖率、MTTR、未闭环项。

## 10. 验证记录（本次搭建）

已执行并留证：

| 项目 | 命令 | 结果 |
| --- | --- | --- |
| 诊断 SQL 可执行性 | `psql -f diagnostics/02..06-*.sql`（本地 PostgreSQL 18.7 / `mrr_test`） | 5 份全部 exit 0；`01` 因本地未预加载扩展无法执行，改用同列定义临时视图校验语法通过 |
| 基线脚本 | `python scripts/db_performance_snapshot.py --label selftest2` | 产出 `snapshot.json` + `summary.md`；`01` 失败被显式标注，未静默空表；报告内无明文口令 |
| 看板 JSON | `json.load` 解析四份看板 | 全部通过 |
| YAML | `yaml.safe_load` 解析规则与 `queries.yaml` | 全部通过 |

已知限制：

- 本地为 PostgreSQL 18.7，生产为 16。诊断 SQL 只用了 PG13+ 与 PG16 已有视图（`pg_stat_io` 需 16+），`pg_stat_statements` 列未使用 17+ 新增列；仍需在生产 16 上实跑一次确认。
- `pg_ls_waldir()` 需要 `pg_monitor`（PG15+ 已授予该角色），旧版本需单独授权。
- 阈值未经生产数据校准，阶段二后应更新本文表格。
- 本次未改动 Flyway、未新增数据库表、未引入新的基础设施组件。

## 11. 与既有文档的关系

- `monitoring/README.md`：安装、端口、启动命令、Grafana provisioning；
- 本文：指标口径、阈值、诊断顺序、调优流程与验收；
- `docs/engineering/mrr-data-operations-runbook.md`：数据操作类运维动作；
- `docs/engineering/data-quality-query-timeout.md`、`export-quota-scan.md`：具体慢查询场景的既有约定，诊断时一并参考。
