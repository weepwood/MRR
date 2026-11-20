# MRR 数据库监控与数据质量

本目录提供 MRR 的原生监控配置，不要求项目使用 Docker。业务后端、PostgreSQL、Prometheus、Grafana、Alertmanager 和 exporter 均可按现有服务器方式直接安装，并注册为 Windows 服务或 Linux systemd 服务。

## 1. 组成

- Spring Boot Actuator：应用、JVM、HikariCP 和数据质量聚合指标
- postgres_exporter：PostgreSQL 连接、事务、死锁和容量指标
- 自定义聚合查询（`postgresql/queries.yaml`）：语句级延迟、慢语句数、膨胀、死元组、长事务
- Prometheus：指标采集与告警规则
- Grafana：MRR 应用、PostgreSQL、查询性能、数据质量四个预置看板
- Alertmanager：接收告警，通知渠道由部署环境配置
- windows_exporter 或 node_exporter：可选的主机磁盘指标

指标口径、告警阈值依据、诊断顺序和调优流程见 `docs/engineering/database-monitoring-and-tuning.md`。本文件只讲部署与启动。

注意：Actuator 的统一标签是 `management.metrics.tags.application=mrr`，所有规则与看板必须以 `application="mrr"` 过滤。历史上曾误写成 `imgapi`，导致 Hikari 告警与部分面板静默失效。

数据库相关告警同样不写死库名：连接、死锁、缓存命中、临时文件四条规则用 `datname!~"template[01]|postgres"` 与 `datname!=""` 只排除系统库和后台进程，现场库名与示例不同也无需改规则文件。同一实例若共存多个业务库，这些指标为合并口径。

数据质量检查只允许管理员在“系统与数据库监控”页面点击“立即检查”触发，不配置后台定时任务。

> **暂不部署 Prometheus / Grafana？** 用 `scripts/db_monitor/` 的计划任务采集器即可获得历史趋势、离线看板与事件日志告警，零新增服务。见 `docs/engineering/database-monitoring-without-prometheus.md`。本目录的配置保留不动，后续切换时指标口径与阈值一致。

## 2. 后端监控端点

后端默认提供仅本机可访问的管理端口：

```text
http://127.0.0.1:18046/actuator/health
http://127.0.0.1:18046/actuator/prometheus
```

相关配置位于 `backend-repo/src/main/resources/application.properties`：

```properties
management.server.port=18046
management.server.address=127.0.0.1
management.endpoints.web.exposure.include=health,info,prometheus
```

Prometheus 与后端不在同一台服务器时，需要通过防火墙白名单或反向代理开放监控端点，不建议直接暴露到公网。

## 3. PostgreSQL 原生配置

编辑 PostgreSQL 的 `postgresql.conf`：

```conf
shared_preload_libraries = 'pg_stat_statements'
track_io_timing = on
compute_query_id = on
pg_stat_statements.max = 10000
pg_stat_statements.track = all
```

修改后重启 PostgreSQL，然后使用管理员账号创建独立监控用户：

```sql
CREATE ROLE mrr_monitor LOGIN PASSWORD '替换为独立强密码';
```

如果角色已存在：

```sql
ALTER ROLE mrr_monitor WITH LOGIN PASSWORD '替换为独立强密码';
```

随后执行：

```bash
# 库名换成现场实际库名：脚本按“当前连接的库”授权，不写死库名
psql -U postgres -d imgapi -f monitoring/postgresql/enable-monitoring.sql
```

Flyway 只负责创建数据质量结果表，不再尝试执行需要管理员权限的扩展安装，因此不会因为应用数据库账号权限不足而阻塞启动。

## 4. postgres_exporter

设置只读监控连接并启动 exporter：

### Windows PowerShell

```powershell
$env:DATA_SOURCE_NAME = "postgresql://mrr_monitor:监控密码@127.0.0.1:5432/imgapi?sslmode=disable"
.\postgres_exporter.exe --web.listen-address=127.0.0.1:9187 --extend.query-path="C:\MRR\monitoring\postgresql\queries.yaml"
```

### Linux

```bash
export DATA_SOURCE_NAME='postgresql://mrr_monitor:监控密码@127.0.0.1:5432/imgapi?sslmode=disable'
./postgres_exporter --web.listen-address=127.0.0.1:9187 --extend.query-path=/opt/mrr/monitoring/postgresql/queries.yaml
```

自定义查询必须用 `--extend.query-path`。**不要**用 `--config.file`：0.15 起这个开关指向的是多实例认证配置（`auth_modules`），把 `queries.yaml` 传进去会报 `field mrr_pg_stat_statements not found in type config.AuthConfig`（0.21.1 实测）。`--extend.query-path` 虽被标记 DEPRECATED，目前仍是唯一入口。加载成功后 `curl 127.0.0.1:9187/metrics` 应能看到 `mrr_pg_stat_statements_*`、`mrr_pg_bloat_*`、`mrr_pg_maintenance_*`、`mrr_pg_transactions_*`。

`queries.yaml` 只做单行聚合，刻意不导出 `queryid` / `query` 文本：语句明细由 `postgresql/diagnostics/` 的 SQL 报告承担，避免 Prometheus 基数爆炸。若指标缺失，`MrrQueryMetricsMissing` 会在 20 分钟后报警，而不是让查询级告警静默失效。

生产环境应通过操作系统服务管理器注入密码，不要把连接密码写入仓库中的配置文件。

## 5. Prometheus

配置文件：`monitoring/prometheus/prometheus.yml`。

默认抓取地址：

- MRR 后端：`127.0.0.1:18046`
- postgres_exporter：`127.0.0.1:9187`
- windows_exporter：`127.0.0.1:9182`
- Alertmanager：`127.0.0.1:9093`

Windows 启动示例：

```powershell
.\prometheus.exe `
  --config.file="C:\MRR\monitoring\prometheus\prometheus.yml" `
  --storage.tsdb.path="C:\MRR\monitoring-data\prometheus" `
  --storage.tsdb.retention.time=30d `
  --web.listen-address=127.0.0.1:9090
```

Linux 启动示例：

```bash
./prometheus \
  --config.file=/opt/mrr/monitoring/prometheus/prometheus.yml \
  --storage.tsdb.path=/var/lib/prometheus \
  --storage.tsdb.retention.time=30d \
  --web.listen-address=127.0.0.1:9090
```

Linux 使用 node_exporter 时，将 `prometheus.yml` 中 `mrr-host` 的目标改为 `127.0.0.1:9100`。告警规则同时兼容 windows_exporter 与 node_exporter 的磁盘指标。

## 6. Alertmanager

配置文件：`monitoring/alertmanager/alertmanager.yml`。

```powershell
.\alertmanager.exe `
  --config.file="C:\MRR\monitoring\alertmanager\alertmanager.yml" `
  --storage.path="C:\MRR\monitoring-data\alertmanager" `
  --web.listen-address=127.0.0.1:9093
```

默认配置只在本地展示告警，不包含邮件、企业微信或内部 Webhook 凭证。通知渠道应在服务器上单独配置。

## 7. Grafana

安装原生 Grafana 后：

1. 将 `monitoring/grafana/provisioning/datasources/prometheus.yml` 放入 Grafana 的 datasource provisioning 目录。
2. 将 `monitoring/grafana/provisioning/dashboards/dashboards.yml` 放入 dashboard provisioning 目录。
3. 设置环境变量 `MRR_GRAFANA_DASHBOARD_PATH`，值为本仓库 `monitoring/grafana/dashboards` 的绝对路径。
4. 重启 Grafana 服务。

Prometheus datasource 默认指向 `http://127.0.0.1:9090`。Grafana 不在同一服务器时，应修改 datasource 地址。

前端“Grafana 看板”按钮默认直达 `mrr-application-overview` 看板，而不是 Grafana 空首页。默认地址为当前主机的 `3000` 端口，也可以通过前端环境变量设置：

```dotenv
VITE_GRAFANA_URL=http://监控服务器地址:3000
VITE_GRAFANA_DASHBOARD_UID=mrr-application-overview
```

如果点击后无法打开页面或页面没有内容，请先确认 Grafana 服务已启动并监听 `3000` 端口，再确认已完成上面的 provisioning 配置。也可以将 `VITE_GRAFANA_DASHBOARD_UID` 改为 `mrr-data-quality`、`mrr-postgresql-overview` 或 `mrr-postgresql-query-performance`，分别打开对应看板。

### 7.1 本地一键演示（Windows 单机，无需 Docker）

`monitoring/local/` 用于在单机把整条链路跑通：

| 文件 | 作用 |
| --- | --- |
| `prometheus.local.yml` | 采集目标全部指向本机回环，规则复用 `../prometheus/rules/mrr-alerts.yml` |
| `start-local-stack.ps1` | 依次拉起 postgres_exporter(9187) → Prometheus(9090) → Grafana(3001)；`-Stop` 按 PID 文件停止 |

```powershell
$env:MRR_MONITOR_DSN = 'postgresql://mrr_monitor:密码@127.0.0.1:5432/imgapi?sslmode=disable'
.\monitoring\local\start-local-stack.ps1
```

启动后：

- `http://127.0.0.1:9090/targets`：`mrr-backend`、`mrr-postgresql`、`prometheus` 三个 target 全 UP
- `http://127.0.0.1:3001/d/mrr-postgresql-overview`：数据库概览（带库名下拉）
- `http://127.0.0.1:3001/d/mrr-application-overview`：应用概览

为什么默认 `3001`：Grafana 官方 Windows 安装包会把 `Grafana` 注册成服务并占用 `3000`。演示实例用环境变量覆盖 `GF_PATHS_*`、`GF_SERVER_HTTP_PORT`，数据写在 `.workbuddy/` 下，不修改安装目录的 `conf/*.ini`，因此可以与 3000 那台服务并存。要把预置看板装进 3000 的服务实例，按第 7 节复制 provisioning 目录并重启服务即可。

本机实测版本：Prometheus 3.16.0、Grafana 13.3.1、postgres_exporter 0.21.1、PostgreSQL 18.7、JDK 21。

`postgresql-overview` 看板带 `datname` 变量（`label_values(pg_database_size_bytes, datname)`），首次打开默认选字母序第一个库，切换即可看其他库。此前表达式硬编码 `datname="imgapi"`，在库名不同的环境会整页 No data。

数据库未预加载 `pg_stat_statements` 时：查询性能看板的 9 个语句级面板显示 No data，`MrrQueryMetricsMissing`、`MrrPgExporterScrapeError` 会在 20 分钟 / 5 分钟后进入 pending / firing。这是自检规则在报「采集链路缺一段」，不是看板画错。开启方式见第 3 节。

## 8. 手动数据质量检查

检查仅由管理页面或受权限保护的接口手动触发：

```http
POST /api/v1/system/data-quality/run
```

当前检查包括：

- 扫描记录病案号、上架号缺失
- 病案号或上架号不是八位数字
- 页数无效
- 文件名或目录缺失
- 已迁移但 OSS 地址为空
- 迁移成功但未校验
- 疑似重复扫描页
- 统计记录找不到扫描记录
- 档案装箱状态异常

每次执行保存：

- `mrr_data_quality_run`：检查批次和汇总
- `mrr_data_quality_check_result`：每项检查的准确数量
- `mrr_data_quality_issue`：受样本上限控制的异常明细

Prometheus 只保存检查代码、级别和数量，不把病案号、上架号或患者信息作为标签。

可调整手动检查的保存范围：

```properties
app.data-quality.sample-limit=200
app.data-quality.retention-days=90
```

## 9. 预置告警

可用性与连接：

- 后端或 PostgreSQL 不可用
- PostgreSQL 连接使用率超过 80% / 90%
- PostgreSQL 死锁
- HikariCP 连接等待、接近耗尽或获取超时

查询级与健康度（依赖 `queries.yaml`）：

- 语句平均执行耗时超过 500ms / 2s
- 平均耗时超过 1 秒的语句超过 10 条（周度体检信号）
- 缓存命中率低于 99% / 95%
- 10 分钟内临时文件写入超过 200MB
- 事务运行超过 5 分钟、空闲事务超过 10 分钟
- 单表死元组占比超过 20%、表膨胀超过 1GiB 且比例超过 30%

监控自检（防止规则静默失效）：

- 查询级指标未采集、事务画像指标未采集
- postgres_exporter 采集部分失败

容量与其他：

- Windows 或 Linux 主机磁盘空间不足（15% / 8%）
- 最近一次手动数据质量检查发现严重异常
- 手动数据质量检查运行超过 30 分钟

## 10. 查询诊断与基线快照

`postgresql/diagnostics/` 提供六份只读诊断 SQL，用 `mrr_monitor` 账号执行即可：

| 文件 | 回答的问题 |
| --- | --- |
| `01-slow-queries.sql` | 哪类语句最耗时、有没有 N+1、是否重复规划、谁在写临时文件 |
| `02-index-usage.sql` | 哪些索引从未使用、哪些表全表扫描多、索引是否比表还大 |
| `03-bloat-and-vacuum.sql` | 死元组占比、膨胀估算、autovacuum 是否滞后 |
| `04-locks-and-transactions.sql` | 锁等待链、长事务、idle in transaction、未决两阶段事务 |
| `05-capacity-io.sql` | 库大小、缓存命中、IO 分布、WAL 与归档 |
| `06-connections.sql` | 连接水位、按应用/用户分布、最老会话 |

一键采集全部证据（调优前后各跑一次，用于对比）：

```powershell
$env:MRR_DB_DSN = "postgresql://mrr_monitor:监控密码@127.0.0.1:5432/imgapi"
python scripts/db_performance_snapshot.py --psql "C:\PostgreSQL\bin\psql.exe" --label before-index-v3
```

输出在 `.workbuddy/db-snapshots/<时间戳>-<标签>/`，含 `snapshot.json`、`summary.md` 与六份诊断原文，`--compare-to` 可直接与旧快照对比。报告内不写入口令，但可能含查询字面量，只允许内网留存。

诊断口径、判定顺序和调优流程见 `docs/engineering/database-monitoring-and-tuning.md`。

## 11. 配置验证

安装 Prometheus 和 Alertmanager 后可执行：

```bash
promtool check config monitoring/prometheus/prometheus.yml
amtool check-config monitoring/alertmanager/alertmanager.yml
```

Grafana 看板文件可使用任意 JSON 校验工具检查。仓库 CI 也会自动校验这些配置文件，但运行时部署不依赖 Docker。
