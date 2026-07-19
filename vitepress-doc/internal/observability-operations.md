# 单服务器可观测性与运维

MRR 当前采用一台 Windows Server，生产运行组件只有 PostgreSQL、Spring Boot 后端和 Nginx。本文说明在不部署额外监控平台的情况下如何定位问题、备份数据和完成恢复。

## 1. 请求编号与错误码

业务响应保留数值 `code`，并增加：

```json
{
  "code": 500,
  "errorCode": "MRR-SYSTEM-9000",
  "message": "服务器内部错误，请联系管理员",
  "requestId": "a5a13d5ddfe24c21945ac321fb56b832",
  "traceId": "a5a13d5ddfe24c21945ac321fb56b832"
}
```

单服务器模式不启用分布式追踪，`traceId` 与 `requestId` 使用同一个关联值。用户反馈问题时只需要提供错误码、请求编号和发生时间，不应提供病案号、身份证号、Token 或完整敏感 URL。

排查顺序：

1. 在 `C:\MRR\logs\backend\mrr-error.log` 搜索请求编号；
2. 在 `img-api.log` 查看同一请求的上下文；
3. 在 Nginx access log 查看网关耗时和 HTTP 状态；
4. 必要时再查询 `access_log` 审计表。

## 2. 日志

生产日志保存在本机：

```text
C:\MRR\logs\backend\img-api.log
C:\MRR\logs\backend\mrr-error.log
C:\MRR\logs\backend\gc.log
C:\MRR\logs\nginx\
C:\MRR\logs\service\
```

日志为 JSON 或固定格式滚动文件，不需要 Loki、Elasticsearch、Fluent Bit 等采集组件。

禁止把患者姓名、身份证号、病案号、图片完整路径、签名 URL、密码、JWT 和 HMAC 密钥写入日志标签或错误响应。

## 3. 健康检查

管理端口只监听本机：

```text
http://127.0.0.1:18046/actuator/health/liveness
http://127.0.0.1:18046/actuator/health/readiness
http://127.0.0.1:18046/actuator/metrics
```

- Liveness 只判断 Java 进程是否正常；
- Readiness 检查数据库与可靠审计队列；
- Metrics 用于管理员本机诊断，不对外开放。

部署和回滚脚本以 readiness 结果作为是否切换版本的依据。

## 4. 可靠审计

关键病案访问与管理操作优先同步写入 PostgreSQL。数据库短时不可用时，事件强制刷入：

```text
C:\MRR\state\audit\audit-events.jsonl
```

数据库恢复后自动幂等重放。该目录仅允许 Administrators 和 SYSTEM 访问。

检查要点：

- `/actuator/health/readiness` 是否为 `UP`；
- 审计队列文件是否持续增长；
- 日志是否出现数据库与本地队列同时写入失败；
- 磁盘是否已满或 ACL 是否被修改。

## 5. 统一管理入口

双击：

```text
C:\MRR\MRR-Manager.cmd
```

可以完成：

- 服务状态、启停和重启；
- 部署、自动健康检查和回滚；
- 数据库与配置备份；
- 最近备份验证；
- 错误日志查看；
- 诊断包导出；
- 按需 JFR；
- 手工恢复演练。

远程网页中不直接暴露执行备份、重启或 JFR 的接口，避免管理接口成为新的高权限攻击面。

## 6. 备份

安装脚本创建 Windows 计划任务：

```text
MRR-Daily-Backup
```

每天 02:00 执行：

```text
C:\MRR\ops\backup\backup-database.ps1
```

脚本读取现有 Spring 数据库配置，不需要额外的备份数据库角色或 `pgpass.conf`。

备份包含：

- PostgreSQL custom-format dump；
- 普通配置、secrets 和 Nginx 配置；
- 当前版本 Manifest；
- SHA-256 与 JSON 清单。

目录：

```text
C:\MRR\backups\postgresql\daily
C:\MRR\backups\postgresql\weekly
C:\MRR\backups\postgresql\monthly
```

默认保留每日 14 天、每周 8 周、每月 12 个月。

建议配置第二备份位置：

```properties
app.backup.secondary-path=\\nas\mrr-backup
```

备份与数据库位于同一物理磁盘时，不能应对磁盘损坏。

## 7. 验证与恢复演练

日常验证不需要管理员数据库账号：

```powershell
C:\MRR\ops\backup\verify-backup.ps1
```

验证内容：

- 清单存在；
- 数据库 dump SHA-256；
- `pg_restore --list`；
- 配置 ZIP SHA-256 和可读性。

完整恢复演练按需执行：

```powershell
C:\MRR\ops\backup\restore-drill.ps1
```

脚本会交互请求 PostgreSQL 管理员凭据，创建隔离临时数据库，执行恢复与核心表检查，最后生成 JSON 报告并默认删除临时数据库。

## 8. 性能诊断

JFR 默认不持续录制，避免长期磁盘写入和认知负担。发生 CPU、内存或慢接口问题时，通过管理器录制 5 分钟，或执行：

```powershell
C:\MRR\ops\diagnostics\profile.ps1 start -DurationMinutes 5
```

诊断文件保存在：

```text
C:\MRR\logs\diagnostics
```

基础 GC 与 Safepoint 日志仍持续滚动；OOM 时自动生成 Heap Dump 并退出，由 WinSW 按策略重启。

## 9. 诊断包

执行：

```powershell
C:\MRR\ops\diagnostics\export-diagnostics.ps1
```

诊断包包含服务状态、端口、磁盘、健康结果、脱敏配置和有限日志尾部，不包含 secrets 文件。诊断包仍可能包含内部 IP、用户名和错误上下文，应按敏感运维资料管理。

## 10. 发布策略

单服务器模式使用维护窗口和目录联接切换：

1. 校验发布包；
2. 开启维护模式；
3. 停止后端；
4. 切换 `current/previous`；
5. 启动并检查 readiness；
6. 成功后恢复访问；
7. 失败时自动切回旧版本。

不运行 Blue/Green 双实例，不部署服务网格，也不在生产服务器执行 k6 压测。

## 11. 可选高级监控

仓库 `monitoring/` 目录仅供未来多服务器部署使用。默认安装不会复制或启动 Prometheus、Grafana、Alertmanager、exporter 和外部探针。
