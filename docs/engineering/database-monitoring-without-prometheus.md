# 不装 Prometheus / Grafana 的数据库监控方案

本项目在 `monitoring/` 里已经备好了 Prometheus + Grafana 的完整配置，但医院内网单机部署时未必愿意再装两个服务（额外端口、额外进程、额外升级与安全评审）。本文给出**零新增服务**的替代路径，并说明它与正式方案的关系。

配套体系设计（指标口径、阈值依据、调优流程）见 `docs/engineering/database-monitoring-and-tuning.md`；Prometheus 部署形态见 `monitoring/README.md`。

> **2026-11-19 更新**：本机已装好 Prometheus 3.16 + Grafana 13.3，接入方式与实测结果见 `monitoring/README.md` 第 7.1 节和 `monitoring/local/`。因此本文的 A 路线（计划任务 + PowerShell）从「首选」降为**兜底**：只在确实不能新增两个常驻进程的环境使用。两条路线的指标口径与阈值完全一致，随时可以切换，历史数据不会因为换方案而失真（本文的 `history.jsonl` 保留，仅供离线追溯）。

## 1. 现状盘点

已有能力（不需要新增任何东西）：

| 能力 | 位置 | 局限 |
| --- | --- | --- |
| 应用内监控页 | `/monitoring`，读 `/api/v1/system/database-overview` | 只有“此刻”的快照，关掉页面就没了 |
| Actuator 指标 | `127.0.0.1:18046/actuator/prometheus` | 是文本端点，没有消费者就是死的 |
| 诊断 SQL | `monitoring/postgresql/diagnostics/*.sql` | 要人手动跑、手动比对 |
| 健康检查 | `/actuator/health` | 只回答“活着”，不回答“快不快” |

缺口：**没有历史、没有趋势、没有主动告警、没有慢 SQL 视角**。这四点恰好是“出事时能定位、出事前能发现”的关键。

## 2. 三条路线怎么选

| | A. 计划任务 + 采集脚本 | B. 接已有监控平台 | C. Prometheus + Grafana |
| --- | --- | --- | --- |
| 新增服务 | 0（用系统自带 PowerShell + 计划任务） | 取决于现有平台（Zabbix/Netdata 等） | 3–4 个（Prometheus、exporter、Alertmanager、Grafana） |
| 历史与趋势 | 有（JSONL，默认 30 天） | 有 | 有（30 天） |
| 告警 | 事件日志 + 企业微信机器人 | 平台自带 | Alertmanager 全功能 |
| 多实例/多库 | 不支持（单机单库按设计） | 支持 | 支持 |
| 交付成本 | 半天 | 取决于平台是否已存在 | 1–2 天 + 安全评审 |
| 适用 | **当前阶段** | 医院已有统一监控平台时优先 | 后续要长期做容量规划时 |

结论：现在选 A，把 C 的配置留在仓库里不删。A 与 C 的**指标口径和阈值完全一致**，将来切到 C 时历史对比仍然成立。

## 3. 方案 A 的组成

```text
Windows 计划任务（每 5 分钟，SYSTEM）
        │
        ├─ psql ──► PostgreSQL 统计视图（21 项基础指标）
        ├─ psql ──► pg_stat_statements（可选，7 项语句指标）
        ├─ HTTP ──► Actuator（Hikari 等待/活跃）
        └─ WMI  ──► 数据盘剩余空间
        │
        ├─ 阈值判定 ──► 事件日志（Application / MRR-DB-Monitor）
        │             └► 企业微信机器人（可选，走环境变量里的 Webhook）
        │
        └─ 落盘到 C:\MRR\monitoring-data\db-monitor\
              history.jsonl   逐次历史（趋势、前后对比）
              alerts.jsonl    状态迁移（只记变化，不刷屏）
              status.json     当前状态（给其他程序读）
              index.html      离线看板（单文件，双击即看）
              collect.log     采集日志
```

为什么用 psql 而不是调用应用接口：应用挂了恰恰是最需要看数据库的时候，采集链路必须和业务进程解耦；同时避免把登录 Token 塞进脚本。

## 4. 覆盖矩阵

| 层 | 指标 | 方案 A | 说明 |
| --- | --- | --- | --- |
| L0 可用性 | 数据库连通性 | ✅ | psql 失败即退出码 3，任务历史可见 |
| L0 | 应用存活 / Hikari 超时 | ⚠️ 部分 | 能取 Hikari 待定线程；应用整体存活建议直接用 `/actuator/health` 探活 |
| L1 连接 | 连接数、使用率、锁等待、空闲事务、最长事务 | ✅ | 6 项 |
| L1 容量 | 库大小、最大表、数据盘剩余 | ✅ | |
| L1 缓存/IO | 缓存命中率、临时文件增量 | ✅ | 临时文件用两次采集的增量，识别“最近在刷盘” |
| L1 争用 | 死锁增量 | ✅ | |
| L2 语句 | 平均延迟、慢语句条数、最慢语句、语句临时文件 | ✅（需预加载） | 依赖 `pg_stat_statements`，未开启时看板显式标注“未启用”，不会假装正常 |
| L2 维护 | 死元组占比、待清理表数、膨胀率、未使用索引数 | ✅ | 膨胀带 1GiB 体积门槛，避免小表误报 |
| L2 计划 | EXPLAIN、执行计划漂移 | ❌ | 仍需人工用 `diagnostics/` 的 SQL |
| L3 数据质量 | 病案号/上架号异常 | ➖ | 沿用应用内手动检查，不重复实现 |

明确不提供：多实例聚合、告警分组抑制、长期（>30 天）容量预测、交互式钻取。这些是 Prometheus + Grafana 的价值，不要指望脚本替代。

## 5. 部署步骤

### 5.1 准备监控账号

用管理员在数据库上执行（只需一次，`imgapi` 换成实际库名）：

```sql
CREATE ROLE mrr_monitor LOGIN PASSWORD '替换为独立强密码';
GRANT CONNECT ON DATABASE imgapi TO mrr_monitor;
GRANT pg_monitor TO mrr_monitor;
```

口令不要写进仓库。按推荐顺序有三种放置方式：

1. **推荐**：`%APPDATA%\postgresql\pgpass.conf`（SYSTEM 账户对应 `C:\Windows\System32\config\systemprofile\AppData\Roaming\postgresql\pgpass.conf`），DSN 里不写口令；
2. 或用 `install-task.ps1 -Dsn` 把带口令的 DSN 写入 `C:\ProgramData\MRR\db-monitor\db-dsn.txt`：脚本会断开目录与文件的 ACL 继承，只保留 SYSTEM 与 Administrators；
3. 机器级环境变量 `MRR_DB_DSN` 仍被采集器识别，但机器级变量存放在 HKLM，**本机任何登录用户都能读到**，只建议临时过渡，不要当作长期方案。

三处同时存在时，采集器按 `config.json` 的 `dsn` → `dsn_file` → 环境变量的顺序取第一个非空值。

### 5.2 试跑一次（不告警）

```powershell
cd C:\MRR\app\scripts\db_monitor
$env:MRR_DB_DSN = "postgresql://mrr_monitor@127.0.0.1:5432/imgapi"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\collect.ps1 -NoAlert
```

看到 `采集完成：overall=...` 后打开输出目录的 `index.html` 确认有数据。首次运行没有历史，趋势区会显示“数据不足”，第二次采集后出线。

退出码：`0` 正常 / `1` 预警 / `2` 严重 / `3` 采集失败。

### 5.3 注册计划任务

```powershell
# 管理员 PowerShell
.\install-task.ps1 -Dsn "postgresql://mrr_monitor@127.0.0.1:5432/imgapi" -EveryMinutes 5
```

脚本会创建事件日志源 `MRR-DB-Monitor`、写入连接串文件 `db-dsn.txt`（断开 ACL 继承，仅 SYSTEM 与 Administrators 可读）、注册任务 `MRR-DB-Monitor`（SYSTEM 账户，每 5 分钟，忽略重叠实例，超时 10 分钟）。若本机还残留早期的机器级 `MRR_DB_DSN`，脚本会提示清理，但不会自动删除。

验证：

```powershell
Start-ScheduledTask -TaskName MRR-DB-Monitor
Get-ScheduledTaskInfo -TaskName MRR-DB-Monitor   # 看 LastTaskResult 与 LastRunTime
```

`LastTaskResult` 就是采集器退出码，可以直接当作“最近一次健康状态”看。

卸载：`.\install-task.ps1 -Uninstall`（保留数据目录与看板）。

### 5.4 开启查询级指标（可选但建议）

只有预加载才能采集 `pg_stat_statements`，需重启 PostgreSQL：

```conf
shared_preload_libraries = 'pg_stat_statements'
pg_stat_statements.max = 10000
pg_stat_statements.track = all
track_io_timing = on
log_min_duration_statement = 2000
```

改完重启，再执行 `CREATE EXTENSION IF NOT EXISTS pg_stat_statements;`。看板顶部会从“未启用”变成“已启用”，语句类阈值开始生效。

## 6. 告警怎么收

| 通道 | 配置 | 适用 |
| --- | --- | --- |
| Windows 事件日志 | 默认开启，来源 `MRR-DB-Monitor`，事件 ID 9101 预警 / 9102 严重 / 9103 恢复 | 服务器本地排查、与其他系统日志统一采集 |
| 企业微信机器人 | 设环境变量 `MRR_ALERT_WEBHOOK` 为机器人地址 | 值班手机收通知 |
| 任务计划结果 | “上次运行结果”非 0 即异常 | 再套一层“任务失败就发邮件”的外部监控 |

查看事件：

```powershell
Get-EventLog -LogName Application -Source MRR-DB-Monitor -Newest 20 |
  Select-Object TimeGenerated, EntryType, EventID, Message
```

设计上**只在状态变化时告警**：同一指标持续异常不会每 5 分钟刷一条；`repeat_after_minutes`（默认 120）到点重新提醒一次；恢复时发一条恢复通知。实测见第 10 节。

## 7. 看板怎么用

- 路径：`<out_dir>\index.html`（默认 `C:\MRR\monitoring-data\db-monitor\index.html`），双击即可在浏览器打开，**不依赖网络、不加载任何外部资源**；
- 页面每 5 分钟自动刷新（`<meta refresh>`），刷新的是磁盘上的文件；
- 兼容 Chrome 86：只用了 grid / flex / 内联 SVG，没有 `:has()`、`color-mix()`、`dvh`；
- 如需远程看，可把该目录配成 Nginx 的一个只读 location（仅内网、仅 GET、加认证）。**但注意**：目录里还有 `history.jsonl`、`collect.log`，只暴露 `index.html` 一个文件，别整目录开放。

页面包含：整体状态徽标、8 张关键指标卡、当前告警、4 条 24 小时趋势、全部指标表、近 24 小时状态变化。指标表中的“状态”列与告警阈值一致，看到红/橙就知道该查什么。

## 8. 阈值与口径

阈值全部在 `scripts/db_monitor/config.json`，与 `database-monitoring-and-tuning.md` 第 4.2 节一一对应。三点需要理解：

1. **增量指标**：`deadlocks_delta`、`temp_bytes_delta`、`stmt_avg_latency_ms` 是“本次减上次”。第一次采集没有基线，这三项不参与判定；统计被重置（实例重启）时增量按 0 处理，不会误报。
2. **膨胀双门槛**：`bloat_max_ratio > 0.3` 且 `bloat_max_bytes > 1GiB` 才告警，避免几十 MB 的小表天天响。
3. **语句级缺失不静默**：`pg_stat_statements` 不可用时整体状态标记为“降级”，看板顶部与指标区都写明原因，不会出现“全是绿的但其实没采到”。

调整阈值属配置变更，改完在 PR 或运维记录里写明依据。

## 9. 与 Prometheus 方案的关系

- 指标口径、阈值、诊断 SQL 三者共用，切换时不产生口径漂移；
- 切到 Prometheus 后，`scripts/db_monitor` 不必删除：把间隔从 5 分钟放宽到 30 分钟，作为**独立于 Prometheus 的兜底证据链**（Prometheus 挂了它还在记）；
- 反向也成立：现在积累的 `history.jsonl` 可以直接作为“上线前基线”，与 Grafana 上线后的曲线对照。

## 10. 验证记录（2026-11-19 本地实测）

| 项目 | 操作 | 结果 |
| --- | --- | --- |
| 基础采集 | 对 `mrr_test` 实跑 | 21 项指标全部解析成功，退出码 1（数据盘可用 12.89% < 15%，属真实预警） |
| 查询级缺失处理 | 本地未预加载 `pg_stat_statements` | 显式告警“查询级指标不可用”，整体标记 `degraded`，不中断采集 |
| 历史与增量 | 连续采集 3 次 | `history.jsonl` 3 条，`deadlocks_delta`/`temp_bytes_delta` 正常产出 |
| 看板 | Chrome 无头渲染截图 | 卡片、趋势、指标表、告警区均正常；趋势对 <2% 的波动做居中处理，不放大成断崖 |
| 告警去重 | 构造 `connections_total` 越限，连跑两次 | 仅记录 1 条 `raise`，第二次不重复 |
| 恢复通知 | 阈值调回正常后运行 | 记录 1 条 `recover`，退出码回到 0 |
| PowerShell 兼容 | 用 5.1 自带解析器校验两个脚本 | 均无语法错误；脚本以 UTF-8 BOM 保存，避免中文乱码 |

## 11. 限制与注意事项

- 采集器以 SYSTEM 运行，**用户级环境变量读不到**，DSN 必须放连接串文件（`dsn_file`）、机器级环境变量或 `pgpass.conf`；
- 依赖 `psql` 客户端（随 PostgreSQL 安装），服务器若只有服务端无客户端需补装；
- 采集 SQL 使用 PG16 已有视图（`pg_stat_io` 等仅在诊断 SQL 中使用），跨大版本升级前需复核；
- 单实例单库设计，多库需复制一份配置与任务；
- HTML 是静态快照，**不能交互钻取**；要看具体慢 SQL 仍需跑 `diagnostics/01-slow-queries.sql`；
- 历史默认保留 30 天、超过 20MB 自动裁剪，长期容量规划请转 Prometheus 或定期归档 JSONL。
