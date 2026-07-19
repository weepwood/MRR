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
| 内置运维能力 | Actuator、结构化日志、状态历史和数据库诊断 | 单服务器日常运行与排错 |
| 扩展监控 | 可选 Prometheus / Grafana | 仅在确有需要时额外部署 |

后端管理端点只监听本机：

```text
http://127.0.0.1:18046/actuator/health
http://127.0.0.1:18046/actuator/info
http://127.0.0.1:18046/actuator/prometheus
```

其中 `/actuator/info` 用于核对运行中的产品版本、构建时间和 Git Commit。

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

配置结构版本记录在发布包 `manifest.json` 的 `configuration.schemaVersion`。当结构版本变化时，升级前必须比较配置模板并补充新配置。

## 服务与维护模式

```powershell
$ctl = 'C:\MRR\ops\mrrctl.ps1'

& $ctl status
& $ctl doctor
& $ctl start all
& $ctl stop backend
& $ctl restart all
& $ctl logs backend -Tail 300

& $ctl maintenance on -Message '系统升级中，请稍后再试。'
& $ctl maintenance off
```

MRR 不通过挂起 JVM 实现“暂停”。维护模式由 Nginx 返回 503 页面，后端仍可完成已进入的请求，本机 Actuator 保持可用。

## 唯一版本与发布目录

产品版本由仓库根目录 `VERSION` 决定。正式标签必须为 `v<VERSION>`，例如 `VERSION=0.4.0` 对应：

```text
Git Tag:       v0.4.0
Windows ZIP:   MRR-v0.4.0.zip
发布目录:      v0.4.0-abcdef12
```

发布目录是不可变的：

```text
C:\MRR\releases\v0.4.0-abcdef12
C:\MRR\current  -> releases\v0.4.0-abcdef12
C:\MRR\previous -> releases\另一份受管理发布包
```

后端和 Nginx 始终读取 `current`，部署脚本只切换目录联接，不覆盖正在运行的 JAR 或静态文件。

## 受管理发布包

GitHub Actions 构建的 v1 发布包包含：

```text
backend/mrr-backend.jar
frontend/
docs/user/
docs/internal/
deploy/windows/
VERSION
release-baseline.json
manifest.json
SHA256SUMS
release-notes.md
```

`manifest.json` 示例：

```json
{
  "manifestSchemaVersion": 1,
  "productVersion": "0.4.0",
  "gitCommit": "abcdef1234567890abcdef1234567890abcdef12",
  "buildTime": "2026-07-19T12:30:00Z",
  "database": {
    "minimumCompatibleMigration": "20260715113552",
    "maximumCompatibleMigration": "20260719174500",
    "backwardCompatibleWithPreviousApplication": false
  },
  "applicationRollback": {
    "allowed": false,
    "reason": "回滚限制说明"
  },
  "configuration": {
    "schemaVersion": 1
  }
}
```

基线建立前生成的旧格式 ZIP 缺少 `VERSION`、v1 manifest 和兼容性声明，不能作为 `mrrctl rollback` 的普通目标。需要回到基线前版本时，必须使用数据库备份恢复与人工部署流程。

## 发布与回滚

部署受管理包：

```powershell
& $ctl deploy C:\MRR\packages\MRR-v0.4.0.zip
```

回滚到 `previous` 指向的另一份受管理包：

```powershell
& $ctl rollback previous
```

当当前 manifest 禁止应用回滚时，普通回滚会被拒绝。只有已经完成人工数据库恢复或明确确认兼容后，才可执行：

```powershell
& $ctl rollback previous -Force
```

`-Force` 只跳过回滚许可判断，不会把旧格式 ZIP 转换为受管理发布包。

## 自动失败处置

部署时按以下顺序执行：

1. 解压并校验发布包、manifest 和 SHA256；
2. 确认数据库兼容范围和配置结构版本；
3. 开启维护模式；
4. 停止后端；
5. 切换 `current`；
6. 启动后端；
7. 等待 Actuator 返回 `UP`；
8. 核对 `/actuator/info` 的版本和 Commit；
9. 重新加载 Nginx 并关闭维护模式。

新版本健康检查失败时：

- manifest 明确允许应用回滚，脚本才可恢复原受管理应用版本；
- manifest 禁止应用回滚，系统保持维护模式，等待数据库恢复或专项兼容处置。

## 回滚边界

应用版本回滚不等于数据库回滚。Flyway 不自动降级。

- `applicationRollback.allowed=true`：仍需确认目标受管理旧版本处于兼容矩阵内；
- `applicationRollback.allowed=false`：不得直接切换旧 JAR/前端；
- `database.backwardCompatibleWithPreviousApplication=false`：上一应用版本使用升级后数据库尚未完成兼容验证；
- 涉及字段删除、类型修改、不可逆数据转换或旧版本无法识别的新约束时，必须使用发布前数据库备份或专用兼容迁移。

更多命令、目录结构和故障处理参见 `deploy/windows/README.md`，版本与兼容规则以 [发布流程与版本基线](./release.md) 为准。
