# MRR 数据库监控与数据质量

本目录提供 MRR 的原生监控配置，不要求项目使用 Docker。业务后端、PostgreSQL、Prometheus、Grafana、Alertmanager 和 exporter 均可按现有服务器方式直接安装，并注册为 Windows 服务或 Linux systemd 服务。

## 1. 组成

- Spring Boot Actuator：应用、JVM、HikariCP 和数据质量聚合指标
- postgres_exporter：PostgreSQL 连接、事务、死锁和容量指标
- Prometheus：指标采集与告警规则
- Grafana：MRR 应用、PostgreSQL、数据质量三个预置看板
- Alertmanager：接收告警，通知渠道由部署环境配置
- windows_exporter 或 node_exporter：可选的主机磁盘指标

数据质量检查只允许管理员在“系统与数据库监控”页面点击“立即检查”触发，不配置后台定时任务。

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
psql -U postgres -d imgapi -f monitoring/postgresql/enable-monitoring.sql
```

Flyway 只负责创建数据质量结果表，不再尝试执行需要管理员权限的扩展安装，因此不会因为应用数据库账号权限不足而阻塞启动。

## 4. postgres_exporter

设置只读监控连接并启动 exporter：

### Windows PowerShell

```powershell
$env:DATA_SOURCE_NAME = "postgresql://mrr_monitor:监控密码@127.0.0.1:5432/imgapi?sslmode=disable"
.\postgres_exporter.exe --web.listen-address=127.0.0.1:9187
```

### Linux

```bash
export DATA_SOURCE_NAME='postgresql://mrr_monitor:监控密码@127.0.0.1:5432/imgapi?sslmode=disable'
./postgres_exporter --web.listen-address=127.0.0.1:9187
```

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

前端“Grafana 看板”按钮默认打开当前主机的 `3000` 端口，也可以通过前端环境变量设置：

```dotenv
VITE_GRAFANA_URL=http://监控服务器地址:3000
```

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

- 后端或 PostgreSQL 不可用
- PostgreSQL 连接使用率超过 80% / 90%
- PostgreSQL 死锁
- HikariCP 连接等待、接近耗尽或获取超时
- Windows 或 Linux 主机磁盘空间不足
- 最近一次手动数据质量检查发现严重异常
- 手动数据质量检查运行超过 30 分钟

## 10. 配置验证

安装 Prometheus 和 Alertmanager 后可执行：

```bash
promtool check config monitoring/prometheus/prometheus.yml
amtool check-config monitoring/alertmanager/alertmanager.yml
```

Grafana 看板文件可使用任意 JSON 校验工具检查。仓库 CI 也会自动校验这些配置文件，但运行时部署不依赖 Docker。
