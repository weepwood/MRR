# MRR 数据库监控与数据质量

本目录提供可选的 Docker Compose 监控栈：

- Prometheus：采集 Spring Boot、PostgreSQL 和主机指标
- Grafana：预置 MRR 应用、PostgreSQL、数据质量三个看板
- postgres_exporter：使用独立 `mrr_monitor` 账号读取 PostgreSQL 统计信息
- node_exporter：采集 Linux 主机磁盘容量
- Alertmanager：接收 Prometheus 告警，通知渠道由部署方配置

## 1. 启动

```bash
cp .env.example .env
# 修改所有密码后启动业务和监控服务
docker compose -f docker-compose.yml -f docker-compose.monitoring.yml --profile monitoring up -d --build
```

默认仅绑定本机：

- Grafana: `http://127.0.0.1:3000`
- Prometheus: `http://127.0.0.1:9090`
- Alertmanager: `http://127.0.0.1:9093`

Actuator 使用容器内部 `18046` 端口，不映射到宿主机。前端通过受 `system:read` 权限保护的业务接口读取数据库摘要，不直接访问 Actuator。

## 2. 已有 PostgreSQL 数据卷

`docker-entrypoint-initdb.d` 只会在空数据目录初始化时执行。已有 `postgres_data` 卷时，需要手动创建或更新监控账号：

```bash
docker compose exec postgres psql -U postgres -d imgapi
```

然后执行：

```sql
CREATE ROLE mrr_monitor LOGIN PASSWORD '替换为独立强密码';
GRANT CONNECT ON DATABASE imgapi TO mrr_monitor;
GRANT pg_monitor TO mrr_monitor;
```

如果角色已经存在，使用：

```sql
ALTER ROLE mrr_monitor WITH LOGIN PASSWORD '替换为独立强密码';
GRANT pg_monitor TO mrr_monitor;
```

密码需要与 `.env` 的 `POSTGRES_MONITOR_PASSWORD` 一致。

## 3. 数据质量检查

默认每天 02:15 执行，也可以在“系统与数据库监控”页面手动触发。检查包括：

- 扫描记录病案号、上架号缺失
- 病案号或上架号不是八位数字
- 页数无效
- 文件名或目录缺失
- 已迁移但 OSS 地址为空
- 迁移成功但未校验
- 疑似重复扫描页
- 统计记录找不到扫描记录
- 档案装箱异常状态

每次运行保存：

- `mrr_data_quality_run`：批次和汇总
- `mrr_data_quality_check_result`：每项检查的准确数量
- `mrr_data_quality_issue`：受样本上限控制的异常明细

Prometheus 指标不使用病案号、上架号或患者字段作为标签，避免高基数和敏感信息泄露。

可通过环境变量调整：

```dotenv
APP_DATA_QUALITY_ENABLED=true
APP_DATA_QUALITY_CRON=0 15 2 * * ?
APP_DATA_QUALITY_SAMPLE_LIMIT=200
APP_DATA_QUALITY_RETENTION_DAYS=90
```

## 4. 告警

预置规则覆盖：

- 后端或 PostgreSQL 不可用
- PostgreSQL 连接使用率超过 80% / 90%
- 数据库死锁
- HikariCP 等待、接近耗尽或连接超时
- 主机磁盘可用空间低于 15% / 8%
- 严重数据质量异常
- 数据质量检查运行超过 30 分钟
- 超过 26 小时没有成功检查

Alertmanager 默认只保存和展示告警，不包含外部通知凭证。生产部署应在 `monitoring/alertmanager/alertmanager.yml` 增加医院允许使用的邮件、企业微信或内部 Webhook 接收器，并将凭证通过部署系统注入，不要提交到仓库。

## 5. 常用检查

```bash
# 查看所有抓取目标
docker compose -f docker-compose.yml -f docker-compose.monitoring.yml --profile monitoring ps

# 验证 Prometheus 配置
docker compose -f docker-compose.yml -f docker-compose.monitoring.yml --profile monitoring exec prometheus \
  promtool check config /etc/prometheus/prometheus.yml

# 验证告警规则
docker compose -f docker-compose.yml -f docker-compose.monitoring.yml --profile monitoring exec prometheus \
  promtool check rules /etc/prometheus/rules/mrr-alerts.yml

# 查看数据库监控账号是否存在
docker compose exec postgres psql -U postgres -d imgapi -c "\\du mrr_monitor"

# 检查 pg_stat_statements
docker compose exec postgres psql -U postgres -d imgapi -c \
  "SELECT extname FROM pg_extension WHERE extname = 'pg_stat_statements';"
```

## 6. 平台差异

`node_exporter` 的主机目录挂载面向 Linux 服务器。Windows 或 macOS Docker Desktop 开发环境如果无法挂载 `/proc`、`/sys` 和根目录，可暂时只启动其余监控服务，生产 Linux 服务器仍应保留主机磁盘监控。
