# Windows Server 原生部署

MRR 在内网 Windows Server 中采用 **WinSW + PowerShell + Nginx + 不可变版本目录** 部署，不依赖 Docker，也不在生产服务器上执行源码构建。

完整脚本和模板位于：

```text
deploy/windows/
```

## 部署组成

| 组件 | 运行方式 | 用途 |
|---|---|---|
| MRR Backend | `MRR-Backend` Windows 服务 | Spring Boot API |
| MRR Gateway | `MRR-Gateway` Windows 服务 | 前端、文档、反向代理和维护页 |
| PostgreSQL | 官方 Windows 服务 | 业务数据库 |
| Prometheus / Alertmanager | 可注册为 Windows 服务 | 指标与告警 |
| Grafana | 官方 Windows 服务 | 运行趋势和看板 |

后端管理端点只监听本机：

```text
http://127.0.0.1:18046/actuator/health
http://127.0.0.1:18046/actuator/prometheus
```

## 首次安装

准备 JDK 21、PostgreSQL、Windows 版 Nginx 和 WinSW 后，以管理员身份执行：

```powershell
.\deploy\windows\install.ps1 `
  -Root C:\MRR `
  -WinSWPath C:\Install\WinSW-x64.exe `
  -NginxPath C:\Install\nginx `
  -JavaHome 'C:\Program Files\Java\jdk-21'
```

脚本会创建目录、安装两个 Windows 服务、部署配置模板、保护敏感配置目录并执行 Nginx 语法校验。首次安装不会启动业务服务。

## 配置隔离

普通配置：

```text
C:\MRR\config\application-prod.properties
```

敏感配置：

```text
C:\MRR\secrets\application-secrets.properties
```

`secrets` 目录取消继承权限，只允许本机 Administrators 和 SYSTEM 读取。升级发布包不包含服务器配置，因此不会覆盖数据库密码、密钥、图片路径和 OSS 凭据。

## 服务管理

```powershell
$ctl = 'C:\MRR\ops\mrrctl.ps1'

& $ctl status
& $ctl doctor
& $ctl start all
& $ctl stop backend
& $ctl restart all
& $ctl logs backend -Tail 300
```

`status` 同时检查 Windows 服务、Actuator、前端健康文件、当前版本、维护模式和磁盘剩余空间。

## 维护模式

MRR 不通过挂起 JVM 实现“暂停”。挂起 Java 进程可能让数据库事务、连接池连接和文件任务停在中间状态。

使用：

```powershell
& $ctl maintenance on -Message '系统升级中，请稍后再试。'
& $ctl maintenance off
```

维护模式由 Nginx 返回 503 页面，后端仍可完成已进入的请求，本机 Actuator 与 Prometheus 保持可用。

## 版本管理

```text
C:\MRR\releases\v0.1.1-abcdef12
C:\MRR\releases\v0.1.2-12345678
C:\MRR\current  -> releases\v0.1.2-12345678
C:\MRR\previous -> releases\v0.1.1-abcdef12
```

发布目录是不可变的。后端和 Nginx 始终读取 `current`，部署脚本只切换目录联接，不覆盖正在运行的 JAR 或静态文件。

发布：

```powershell
& $ctl deploy C:\MRR\packages\MRR-v0.1.2.zip
```

回滚：

```powershell
& $ctl rollback previous
& $ctl rollback v0.1.1
```

当当前版本的 `manifest.json` 声明：

```json
{
  "databaseBackwardCompatible": false
}
```

普通回滚会被拒绝。必须先按数据库备份和迁移方案完成恢复，再显式使用 `-Force` 切换应用版本。

## 自动失败恢复

部署时按以下顺序执行：

1. 解压并校验发布包。
2. 开启维护模式。
3. 停止后端。
4. 切换 `current`。
5. 启动后端。
6. 等待 Actuator 返回 `UP`。
7. 重新加载 Nginx并关闭维护模式。

新版本健康检查失败时，脚本会重新指向原版本并再次执行健康检查。原版本也失败时，维护模式保持开启，避免继续对用户提供异常服务。

## 发布包

GitHub Actions 工作流 `.github/workflows/windows-release-package.yml` 构建：

```text
backend/mrr-backend.jar
frontend/
docs/user/
docs/internal/
manifest.json
SHA256SUMS
release-notes.md
```

生产服务器只接收 ZIP，不安装 Maven、Node.js 和 pnpm，也不执行 `git pull`。

## 运维边界

应用版本回滚不等于数据库回滚。Flyway 不自动降级。涉及字段删除、类型修改、不可逆数据转换或旧版本无法识别的新约束时，必须使用发布前数据库备份或专用兼容迁移。

更多命令、目录结构和故障处理参见 `deploy/windows/README.md`。
