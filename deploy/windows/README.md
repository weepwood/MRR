# MRR Windows 原生部署运维

本目录提供不依赖 Docker 的 Windows Server 部署方案。核心组件为：

- WinSW：将 Spring Boot JAR 和 Nginx 注册为 Windows 服务。
- `mrrctl.ps1`：统一执行状态检查、启停、维护模式、部署和回滚。
- `releases/current/previous`：使用目录联接管理不可变版本。
- 外置配置：普通配置与敏感配置分离，升级时不覆盖服务器配置。
- Actuator、Prometheus、Grafana：继续使用仓库现有监控能力。

## 1. 服务器准备

建议准备：

- Windows Server 2019 或更高版本。
- PowerShell 5.1 或 PowerShell 7。
- JDK 21。
- PostgreSQL 16。
- Windows 版 Nginx 解压目录。
- WinSW 可执行文件。

生产服务器不需要安装 Node.js、pnpm、Maven，也不执行 `git pull`。构建由 GitHub Actions 或独立构建机完成，服务器只接收发布 ZIP。

## 2. 首次安装

以管理员身份打开 PowerShell：

```powershell
Set-ExecutionPolicy -Scope Process Bypass

.\deploy\windows\install.ps1 `
  -Root C:\MRR `
  -WinSWPath C:\Install\WinSW-x64.exe `
  -NginxPath C:\Install\nginx-1.xx.x `
  -JavaHome 'C:\Program Files\Java\jdk-21'
```

安装脚本会：

1. 创建 `C:\MRR` 目录结构。
2. 写入配置模板，但默认不覆盖已有配置。
3. 设置 `secrets` 目录 ACL，仅允许 Administrators 和 SYSTEM。
4. 安装 `MRR-Backend` 和 `MRR-Gateway` Windows 服务。
5. 校验 Nginx 配置。

安装脚本不会启动业务服务。先完成配置，再部署第一个版本。

## 3. 配置

普通配置：

```text
C:\MRR\config\application-prod.properties
```

敏感配置：

```text
C:\MRR\secrets\application-secrets.properties
```

至少修改：

- PostgreSQL 地址、账号和密码。
- JWT 和 AES 密钥。
- 图片目录与图片服务地址。
- 图片服务账号和密码。
- OSS 凭据（使用 OSS 时）。

不要把服务器上的敏感配置复制回 Git 仓库。

## 4. 发布包结构

```text
MRR-v0.1.2.zip
├─ backend
│  └─ mrr-backend.jar
├─ frontend
│  └─ index.html
├─ docs
│  ├─ user
│  └─ internal
├─ manifest.json
├─ SHA256SUMS
└─ release-notes.md
```

`manifest.json` 至少包含：

```json
{
  "productVersion": "v0.1.2",
  "gitCommit": "abcdef1234567890",
  "buildTime": "2026-07-15T15:30:00+09:00",
  "databaseSchemaVersion": "V1",
  "databaseBackwardCompatible": true
}
```

`databaseBackwardCompatible=false` 时，普通回滚会被阻止，避免旧 JAR 直接读取不兼容数据库。

## 5. 运维命令

```powershell
$ctl = 'C:\MRR\ops\mrrctl.ps1'

# 状态
& $ctl status
& $ctl doctor

# 服务控制
& $ctl start all
& $ctl stop backend
& $ctl restart all

# 维护模式；不会挂起 JVM
& $ctl maintenance on -Message '系统升级中，预计很快恢复。'
& $ctl maintenance off

# 版本
& $ctl version
& $ctl versions

# 部署和回滚
& $ctl deploy C:\MRR\packages\MRR-v0.1.2.zip
& $ctl rollback previous
& $ctl rollback v0.1.1

# 数据库已完成人工恢复后，强制应用回滚
& $ctl rollback v0.1.1 -Force

# 日志
& $ctl logs backend -Tail 300
& $ctl logs backend-service -Tail 300
& $ctl logs gateway -Tail 300
```

## 6. 部署行为

`deploy` 执行：

1. 解压到临时目录。
2. 校验 `manifest.json` 和必要文件。
3. 将版本移动到 `releases/<版本>-<commit>`。
4. 开启维护模式。
5. 优雅停止后端服务。
6. 更新 `previous` 和 `current` 目录联接。
7. 启动后端并检查 `127.0.0.1:18046/actuator/health`。
8. 健康检查通过后重新加载 Nginx 并退出维护模式。
9. 健康检查失败时自动恢复原版本。

默认保留最近 5 个版本，可以通过 `-KeepReleases` 调整。`current` 和 `previous` 指向的版本不会被清理。

## 7. 暂停语义

不要使用系统工具挂起 Java 进程。MRR 将“暂停服务”实现为维护模式：

- 新的页面和业务 API 请求返回 503 维护页。
- 后端进程继续完成已经进入的请求。
- 本机 Actuator 和 Prometheus 端点保持可用。
- `/status` 页面壳和静态资源保持可访问。

具体状态接口路径如果发生变化，应同步更新 `templates/nginx.conf` 中绕过维护模式的 status API location。

## 8. 目录结构

```text
C:\MRR
├─ config
├─ secrets
├─ releases
├─ current       -> releases\vX.Y.Z-commit
├─ previous      -> releases\vX.Y.Z-commit
├─ packages
├─ staging
├─ logs
├─ runtime\nginx
├─ monitoring-data
├─ backups
├─ shared
├─ state
└─ ops
```

## 9. 故障处理

### 后端服务运行但健康检查失败

```powershell
Get-Service MRR-Backend
Get-Content C:\MRR\logs\backend\img-api.log -Tail 300
C:\MRR\ops\mrrctl.ps1 doctor
```

重点检查数据库连接、Flyway、密钥、图片目录和端口占用。

### Nginx 无法启动

```powershell
C:\MRR\runtime\nginx\nginx.exe `
  -p C:\MRR\runtime\nginx `
  -c C:\MRR\config\nginx\nginx.conf `
  -t
```

### 自动回滚也失败

保持维护模式，不要反复切换版本。检查数据库是否已经执行不兼容迁移，并按发布前备份恢复数据库。
