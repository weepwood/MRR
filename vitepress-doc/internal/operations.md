# 运维与监控

## 监控组成

MRR 的原生监控体系包括：

- Spring Boot Actuator：应用、JVM、HTTP 和 HikariCP 指标。
- postgres_exporter：PostgreSQL 连接、事务、锁、死锁和容量。
- windows_exporter 或 node_exporter：主机和磁盘。
- Prometheus：指标采集和规则计算。
- Alertmanager：告警路由。
- Grafana：应用、PostgreSQL 和数据质量看板。

这些组件可作为操作系统服务运行，不要求 Docker。

## Actuator

默认地址：

```text
http://127.0.0.1:18046/actuator/health
http://127.0.0.1:18046/actuator/prometheus
```

关键配置：

```properties
management.server.port=18046
management.server.address=127.0.0.1
management.endpoints.web.exposure.include=health,info,prometheus
management.endpoint.health.show-details=never
```

生产环境不应通过公网直接访问管理端点。

## PostgreSQL 监控

V0 基线声明了监控相关扩展，但生产部署仍应由 PostgreSQL 管理员确认扩展可用：

```conf
shared_preload_libraries = 'pg_stat_statements'
track_io_timing = on
compute_query_id = on
pg_stat_statements.max = 10000
pg_stat_statements.track = all
```

重启 PostgreSQL 后创建独立监控角色并执行：

```bash
psql -U postgres -d imgapi -f monitoring/postgresql/enable-monitoring.sql
```

postgres_exporter 默认监听 `127.0.0.1:9187`。

## Prometheus

配置文件：

```text
monitoring/prometheus/prometheus.yml
```

默认目标：

| 目标 | 地址 |
|------|------|
| MRR 后端 | `127.0.0.1:18046` |
| PostgreSQL exporter | `127.0.0.1:9187` |
| Windows exporter | `127.0.0.1:9182` |
| Alertmanager | `127.0.0.1:9093` |

Linux 使用 node_exporter 时，将主机目标改为 `127.0.0.1:9100`。

验证：

```bash
promtool check config monitoring/prometheus/prometheus.yml
```

## Alertmanager

配置文件：

```text
monitoring/alertmanager/alertmanager.yml
```

仓库默认配置不保存邮件、企业微信或 Webhook 凭证。通知渠道必须在部署环境单独配置。

```bash
amtool check-config monitoring/alertmanager/alertmanager.yml
```

## Grafana

仓库提供三个预置看板：

- `mrr-application-overview`
- `mrr-postgresql-overview`
- `mrr-data-quality`

前端入口可通过以下变量调整：

```dotenv
VITE_GRAFANA_URL=http://127.0.0.1:3000
VITE_GRAFANA_DASHBOARD_UID=mrr-application-overview
```

完整 provisioning 说明见 `monitoring/README.md`。

## 数据质量检查

数据质量检查只允许手动触发：

```http
POST /api/v1/system/data-quality/run
```

当前检查范围包括：

- 病案号或上架号缺失、非八位数字。
- 页数无效。
- 文件名或目录缺失。
- 已迁移但 OSS URL 为空。
- 迁移成功但未校验。
- 疑似重复扫描页。
- 统计记录无法关联扫描记录。
- 档案装箱状态异常。

结果保存到检查批次、检查汇总和受样本限制的异常明细。配置：

```properties
app.data-quality.sample-limit=200
app.data-quality.retention-days=90
```

发现异常后应建立修复记录，不能只删除检查结果。

## 服务状态页

公开页面：

```text
/status
```

展示当前状态、持续时间、最近 90 天可用率、每日状态和异常区间。

```properties
app.status.enabled=true
app.status.check-interval-ms=60000
app.status.heartbeat-timeout-ms=120000
app.status.request-timeout-ms=3000
app.status.frontend-health-url=
app.status.retention-days=365
app.status.zone-id=Asia/Shanghai
```

状态页与业务应用同部署，服务完全停止时页面也不可访问。需要外部即时告警时必须增加独立探针。

## 日志运维

默认日志文件：`img-api.log`。

```properties
logging.logback.rollingpolicy.max-file-size=10MB
logging.logback.rollingpolicy.max-history=30
```

访问日志数据库保留策略：

```properties
app.log-retention.enabled=true
app.log-retention.cron=0 30 2 * * ?
app.log-retention.retention-days=1095
app.log-retention.batch-size=5000
app.log-retention.max-batches-per-run=20
```

定期检查日志目录空间、访问日志增长、清理任务和敏感信息泄露。

## 预置告警

- 后端或 PostgreSQL 不可用。
- 数据库连接使用率超过 80% 或 90%。
- PostgreSQL 死锁。
- HikariCP 等待、接近耗尽或获取超时。
- 主机磁盘空间不足。
- 最近一次手动数据质量检查出现严重异常。
- 数据质量检查运行超过 30 分钟。

## 巡检周期

### 每日

- 查看应用和数据库可用状态。
- 检查新增错误日志和高耗时请求。
- 检查磁盘空间和连接池。

### 每周

- 检查慢查询、锁等待和 Prometheus Target。
- 检查 Grafana 数据连续性。
- 抽查图片访问与数据库记录是否一致。

### 每月

- 执行一次手动数据质量检查。
- 验证数据库备份可读取。
- 检查日志保留和清理效果。
- 复核监控账号与服务账号权限。

## 事件记录

故障处理至少记录开始和恢复时间、影响范围、版本、日志与指标证据、临时措施、根因、永久修复及验证。禁止在未确认根因时删除审计数据或随意修改 Flyway 历史。