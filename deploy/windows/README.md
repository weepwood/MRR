# MRR Windows 单服务器部署

MRR 默认面向一台 Windows Server 部署，不依赖 Docker，也不要求安装 Prometheus、Grafana、Alertmanager、OpenTelemetry Collector 或外部探针。

## 运行组件

生产环境只运行：

1. PostgreSQL 16
2. MRR Backend（Spring Boot + WinSW）
3. MRR Gateway（Nginx + WinSW）

系统状态、错误定位、审计、备份和性能诊断均由 MRR 自身与 Windows 工具完成。

## 最简安装方式

从 GitHub Release 下载 `MRR-vX.Y.Z.zip` 后：

1. 将 ZIP 解压到临时目录；
2. 进入 `deploy\windows`；
3. 双击 `install.cmd`；
4. 编辑安装后生成的两个配置文件；
5. 双击 `C:\MRR\MRR-Manager.cmd`，选择“部署新版本 ZIP”。

离线发布包已经包含经过校验的：

- Eclipse Temurin JDK 21；
- nginx/Windows；
- WinSW。

因此生产服务器只需要预先安装 PostgreSQL 16，不需要安装 Java、Node.js、Maven、pnpm、Nginx、WinSW，也不需要在服务器执行 `git pull`。

## 手工运行时方式

需要使用服务器已有 Java、Nginx 或 WinSW 时，也可以执行：

```powershell
Set-ExecutionPolicy -Scope Process Bypass

.\install.ps1 `
  -Root C:\MRR `
  -WinSWPath C:\Install\WinSW-x64.exe `
  -NginxPath C:\Install\nginx `
  -JavaHome 'C:\Program Files\Java\jdk-21'
```

安装脚本会自动完成：

- 创建 `C:\MRR` 目录；
- 将内置 Java 和 Nginx 复制到持久运行目录；
- 写入配置模板；
- 保护 secrets、审计队列和备份目录 ACL；
- 注册后端和 Nginx Windows 服务；
- 创建每天 02:00 的数据库与配置备份任务；
- 安装统一管理入口 `C:\MRR\MRR-Manager.cmd`。

安装后先编辑：

```text
C:\MRR\config\application-prod.properties
C:\MRR\secrets\application-secrets.properties
```

至少配置：

- PostgreSQL 地址、用户名和密码；
- JWT 和 AES 密钥；
- `app.audit.hmac-secret`；
- 图片目录和图片服务地址；
- OSS 凭据（使用 OSS 时）。

## 日常管理

双击：

```text
C:\MRR\MRR-Manager.cmd
```

菜单支持：

- 查看状态；
- 启动、停止、重启；
- 部署新版本；
- 回滚上一版本；
- 立即备份；
- 验证最近备份；
- 查看错误日志；
- 导出诊断包；
- 按需录制 5 分钟 JFR；
- 手工执行完整恢复演练。

底层仍可直接使用：

```powershell
$ctl = 'C:\MRR\ops\mrrctl.ps1'
& $ctl status
& $ctl doctor
& $ctl deploy C:\MRR\packages\MRR-v0.4.0.zip
& $ctl rollback previous
```

## 内置运维页面

管理员登录 MRR 后，系统监控页面直接显示：

- 应用和数据库状态；
- 最近备份时间、大小和失败原因；
- 可靠审计队列积压；
- 服务器磁盘与图片磁盘；
- 应用日志和错误日志大小；
- JVM、数据库连接、锁等待和数据质量。

网页只提供只读状态。部署、回滚、立即备份、恢复演练和 JFR 等高权限操作只能在服务器上的 `MRR-Manager.cmd` 中执行。

## 发布与自动回滚

部署过程：

1. 校验 ZIP、Manifest 和 SHA-256；
2. 进入维护模式；
3. 停止后端；
4. 切换 `current` 和 `previous` 目录联接；
5. 启动新版本；
6. 检查本机 readiness；
7. 成功后退出维护模式；
8. 失败时自动恢复原版本。

单服务器模式不运行两套后端，也不实施蓝绿或按比例灰度发布。短暂维护窗口换取更低的部署复杂度。

## 内置健康检查

```text
http://127.0.0.1:18046/actuator/health/liveness
http://127.0.0.1:18046/actuator/health/readiness
http://127.0.0.1:18046/actuator/metrics
```

- Liveness：Java 进程是否存活；
- Readiness：数据库与可靠审计队列是否可用；
- Metrics：本机诊断时查看 JVM、HTTP 和连接池指标。

管理端口只监听 `127.0.0.1`，不要直接暴露到内网或公网。

## 日志与诊断

```text
C:\MRR\logs\
├─ backend\
│  ├─ img-api.log
│  ├─ mrr-error.log
│  └─ gc.log
├─ nginx\
├─ service\
└─ diagnostics\
```

系统不需要日志采集服务。发生问题时按请求编号搜索 JSON 日志，或使用管理器导出诊断包。

JFR 默认不持续运行。CPU、内存或接口耗时异常时，使用管理器录制 5 分钟，结果保存在：

```text
C:\MRR\logs\diagnostics
```

## 备份

每日 02:00 的计划任务执行：

```text
C:\MRR\ops\backup\backup-database.ps1
```

脚本直接读取现有 Spring 数据库配置，不需要创建额外备份账号或 `pgpass.conf`。

备份内容：

- PostgreSQL custom-format dump；
- 普通配置；
- secrets 配置；
- Nginx 配置；
- 当前版本 Manifest；
- SHA-256 和 JSON 清单。

默认保留：

- 每日 14 天；
- 每周 8 周；
- 每月 12 个月。

在 `application-prod.properties` 中可选配置第二备份位置：

```properties
app.backup.secondary-path=\\nas\mrr-backup
```

即使只有一台服务器，也建议把备份复制到 NAS、另一块物理磁盘或合规对象存储；备份与数据库放在同一块磁盘无法应对磁盘损坏。

## 目录结构

```text
C:\MRR
├─ MRR-Manager.cmd
├─ config
├─ secrets
├─ releases
├─ current
├─ previous
├─ packages
├─ logs
├─ backups
├─ state
├─ runtime
│  ├─ java
│  └─ nginx
└─ ops
   ├─ mrrctl.ps1
   ├─ mrr-manager.ps1
   ├─ backup
   ├─ diagnostics
   └─ services
```

## 可选高级监控

仓库根目录的 `monitoring/` 仅供未来扩容使用。默认安装不会复制或启动其中的 Prometheus、Grafana、Alertmanager 和 exporter 配置。
