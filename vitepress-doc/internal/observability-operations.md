# 可观测性与运维基线

本文说明 `feat/observability-operations` 引入的生产运维能力。当前生产模型仍是 Windows Server、Nginx、Spring Boot、PostgreSQL 原生部署，不要求 Docker。

## 1. 请求关联与错误码

所有业务响应保留兼容字段 `code`，同时增加：

```json
{
  "code": 500,
  "errorCode": "MRR-SYSTEM-9000",
  "message": "服务器内部错误，请联系管理员",
  "requestId": "a5a13d5ddfe24c21945ac321fb56b832",
  "traceId": "f08a6d1b20f04fd9b841a32d067b31ab"
}
```

Nginx 生成并覆盖公网请求的 `X-Request-Id`，后端验证格式后沿用。Nginx 访问日志同时记录网关和后端请求编号。用户反馈错误时应优先索取 `errorCode` 和 `requestId`，不得要求用户提供病案号、身份证号或完整 URL。

通用 500 响应不会返回 SQL、数据库地址、文件路径或堆栈。完整异常只保存在受限的结构化错误日志中。

## 2. 结构化日志

生产日志：

```text
C:\MRR\logs\backend\img-api.log
C:\MRR\logs\backend\mrr-error.log
C:\MRR\logs\backend\gc.log
C:\MRR\logs\backend\mrr-<pid>.jfr
```

JSON 日志字段包括 `service`、`version`、`requestId`、`traceId`、`spanId`、`errorCode`、`clientIp` 和日志级别。禁止记录患者姓名、身份证号、原始病案号、密码、令牌、HMAC 密钥、OSS 签名 URL 和完整请求体。

## 3. 轻量追踪

Spring Boot 默认对 10% 请求建立 Trace，但 OTLP 导出默认关闭。即使未部署 Collector，结构化日志仍可使用 `traceId/spanId` 关联请求。

部署 OpenTelemetry Collector 后配置：

```properties
management.tracing.export.otlp.enabled=true
management.opentelemetry.tracing.export.otlp.endpoint=http://127.0.0.1:4318/v1/traces
```

不得把患者信息、病案号、用户令牌或文件路径写入 Span 标签。

## 4. 健康检查

```text
http://127.0.0.1:18046/actuator/health/liveness
http://127.0.0.1:18046/actuator/health/readiness
http://127.0.0.1:18045/livez
http://127.0.0.1:18045/readyz
```

- Liveness 只判断 JVM 是否仍在运行，不因数据库故障重启 JVM。
- Readiness 检查数据库和可靠审计队列。
- `/healthz.txt` 与 `/api/v1/public/status/ping` 在维护模式下仍可访问。
- 外部探针必须运行在另一台服务器，避免业务服务器断电时探针同时消失。

外部探针示例：

```powershell
C:\MRR\ops\monitoring\probe-mrr.ps1 `
  -BaseUrl http://mrr-server `
  -MetricsDirectory C:\windows_exporter\textfile
```

建议通过 Windows 任务计划程序每分钟执行一次。

## 5. 可靠审计

病案查询、影像查看和下载、用户与角色变更、密码修改、OSS 写操作属于关键审计事件：

1. 请求线程优先写入 PostgreSQL。
2. PostgreSQL 写入失败时，事件强制刷入本地 JSONL 队列。
3. 后台 Worker 每 30 秒尝试重放。
4. `event_id` 唯一约束保证重放幂等。
5. 队列超过 10000 条时 readiness 进入 `OUT_OF_SERVICE` 并触发告警。

本地队列：

```text
C:\MRR\state\audit\audit-events.jsonl
```

安装脚本会移除继承权限，只允许 Administrators 和 SYSTEM 访问。必须在 `application-secrets.properties` 设置独立密钥：

```properties
app.audit.hmac-secret=<独立随机密钥>
```

不得复用 JWT、AES 或外部系统 HMAC 密钥。

## 6. 告警送达

仓库默认 Alertmanager 配置不保存通知凭据。使用部署脚本生成受 ACL 保护的 Webhook 配置：

```powershell
C:\MRR\ops\monitoring\configure-alertmanager.ps1 `
  -WebhookUrl https://internal-alert-gateway.example/mrr `
  -AmtoolPath C:\Monitoring\alertmanager\amtool.exe
```

关键告警包括：

- 后端、PostgreSQL、外部探针不可用。
- 5xx 错误率超过 5%。
- API P95 延迟超过 3 秒。
- HikariCP 连接等待或耗尽。
- 审计事件无法落库和落盘。
- 审计队列积压。
- JVM 堆内存和 GC 暂停异常。
- 磁盘不足、数据库增长异常。
- 备份失败、缺失或超过 30 小时未成功。

通知 Webhook 应由医院内部告警网关转发到企业微信、邮件或值班系统，不建议把公网通知凭据直接写入仓库。

## 7. 性能剖析

Windows 服务默认启用：

- 24 小时滚动 JFR，最大 1 GB。
- GC 与 Safepoint 滚动日志。
- OOM 自动生成 Heap Dump。
- `ExitOnOutOfMemoryError`，由 WinSW 按策略重启。

出现慢请求时，先用 Prometheus/Grafana 判断影响范围，再通过 `requestId/traceId` 定位日志，最后结合 JFR 和 `pg_stat_statements` 判断是 JVM、数据库还是图片服务瓶颈。

## 8. 容量指标

Prometheus 固定采集以下低基数指标：

```text
mrr_database_size_bytes
mrr_table_size_bytes{table="mr_scan|mr_statistics|mr_patient|access_log"}
mrr_table_estimated_rows{table="..."}
```

不得把病案号、用户 ID、请求 ID、文件路径或异常消息作为 Prometheus 标签。

## 9. 备份

先创建最小权限角色：

```powershell
psql -U postgres -d postgres `
  -v backup_password='<backup-password>' `
  -v restore_password='<restore-password>' `
  -f monitoring\postgresql\backup-roles.sql
```

将连接凭据保存在 ACL 保护的：

```text
C:\MRR\secrets\pgpass.conf
```

执行逻辑备份：

```powershell
C:\MRR\ops\backup\backup-database.ps1
```

脚本只有在以下步骤全部成功后才发布备份成功指标：

1. `pg_dump` 自定义格式备份完成。
2. `pg_restore --list` 能读取目录。
3. 生成 SHA-256 校验和与 JSON manifest。

## 10. 恢复演练

每月至少执行一次隔离恢复：

```powershell
C:\MRR\ops\backup\restore-drill.ps1 `
  -BackupFile C:\MRR\backups\postgresql\logical\imgapi-20260719-020000.dump
```

演练会创建临时数据库、完整恢复、检查核心表和关联数据、生成包含实际 RTO 的 JSON 报告，成功后默认删除临时数据库。

报告目录：

```text
C:\MRR\backups\restore-drills
```

`pg_restore --list` 只能证明备份目录可读，不能替代完整恢复演练。

## 11. 发布前验收

发布前至少验证：

```powershell
Invoke-WebRequest http://127.0.0.1:18046/actuator/health/liveness
Invoke-WebRequest http://127.0.0.1:18046/actuator/health/readiness
Invoke-WebRequest http://127.0.0.1/healthz.txt
```

同时确认：

- 随机错误响应包含 `errorCode` 和 `requestId`。
- 前端错误提示显示可检索的请求编号。
- Prometheus Targets 全部为 UP。
- Alertmanager 测试告警能够送达。
- 数据库临时不可用时关键审计进入本地队列，恢复后自动清空。
- 最近一次备份通过校验，最近一次恢复演练有 PASS 报告。

本分支不实现 Blue/Green 或流量灰度；该项属于下一阶段发布架构改造。现有不可变版本、健康检查和自动回滚机制继续保留。
