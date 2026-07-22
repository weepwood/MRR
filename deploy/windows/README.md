# MRR Windows 原生部署运维

本目录提供面向单台 Windows Server 的 MRR 部署方案。生产服务器只需要预装 PostgreSQL 和 JDK；Nginx 与 WinSW 已固定版本并包含在 MRR Windows 离线包中，不需要安装 Node.js、pnpm、Maven，也不执行 `git pull`。

## 组成

- **MRR-Backend**：Spring Boot JAR，由 WinSW 注册为 Windows 服务；
- **MRR-Gateway**：Nginx，由 WinSW 注册为 Windows 服务；
- **mrrctl.ps1**：状态、启停、维护、部署、版本和回滚入口；
- **MRR 一键管理中心**：双击运行的 Windows 图形化管理入口，复用受控脚本执行操作；
- **不可变发布目录**：`releases/current/previous`；
- **外置配置**：普通配置和敏感配置与发布包分离；
- **运行识别**：发布 manifest 与 `/actuator/info` 双重核对产品版本和 Git Commit。

## 服务器准备

建议使用：

- Windows Server 2019 或更高版本；
- PowerShell 5.1 或 PowerShell 7；
- JDK 21；
- PostgreSQL 16；
Nginx for Windows 与 WinSW 由发布工作流下载、记录版本和 SHA256，并随离线 ZIP 交付，无需在服务器上单独准备。

## 首次安装

以管理员身份运行 PowerShell：

```powershell
Set-ExecutionPolicy -Scope Process Bypass

.\deploy\windows\install.ps1 `
  -Root C:\MRR `
  -JavaHome 'C:\Program Files\Java\jdk-21'
```

安装脚本会创建目录、复制配置模板、保护敏感配置目录、安装后端和网关服务并校验 Nginx。首次安装不会自动启动业务服务。

## 一键管理中心

安装完成后，直接双击：

```text
C:\MRR\ops\MRR-管理中心.cmd
```

若解压或文件系统不支持中文文件名，也可以运行 `C:\MRR\ops\MRR-Manager.cmd`。两个入口指向同一个管理程序。

程序会自动请求管理员权限，并集中展示产品版本、后端服务、Nginx 网关、健康状态、维护模式和磁盘空间。可执行：

- 一键启动、停止和重启全部服务；
- 开启维护模式与恢复正常访问；
- 选择受管理 ZIP 执行部署；
- 查看版本列表和执行系统诊断；
- 检查或平滑重载 Nginx；
- 打开系统首页、日志、配置和发布包目录。

所有服务、维护和部署操作仍由 `mrrctl.ps1` 或 `nginxctl.ps1` 执行，管理中心不绕过发布基线、健康检查、构建身份或 SHA-256 校验。

## Windows 脚本编码约定

为兼容 Windows Server 2019 自带的 Windows PowerShell 5.1：

- `deploy/windows/**/*.ps1` 必须使用 **UTF-8 with BOM**，否则 PowerShell 5.1 会按系统 ANSI 代码页解释中文字符串和文件名；
- `deploy/windows/*.cmd` 必须使用 **UTF-8 without BOM**，避免 BOM 被 `cmd.exe` 当作首条命令的一部分；
- CMD 入口必须先执行 `chcp 65001`，再输出或解析中文内容；
- `mrr-manager.ps1 -SelfTest` 用于在 Windows PowerShell 5.1 下无界面验证程序集、中文文本和控制脚本路径。

该约定由 Python 防回归测试和 `windows-latest` 门禁共同检查。

## 配置

普通配置：

```text
C:\MRR\config\application-prod.properties
```

敏感配置：

```text
C:\MRR\secrets\application-secrets.properties
```

至少配置：

- PostgreSQL 地址、账号和密码；
- JWT 与 AES 密钥；
- 外部系统 HMAC Client 和 Secret；
- 图片目录、图片服务地址和凭据；
- OSS 凭据（使用 OSS 时）。

不要把服务器上的敏感配置复制回 Git 仓库。

发布包中的 `manifest.json` 会声明 `configuration.schemaVersion`。升级时若该值变化，必须先比较新版配置模板并补充配置。

## 开发代理与生产请求

开发环境由 Vite 提供 `/proxy`：

```text
浏览器 http://localhost:9200/proxy/api/...
    → Vite
    → http://localhost:18045/api/...
```

生产环境不运行 Vite，也不提供 `/proxy`。Nginx 同源转发：

```text
浏览器 http://服务器地址/api/...
    → Nginx :80
    → Spring Boot 127.0.0.1:18045
```

生产前端必须使用 `.env.production` 构建：

```properties
VITE_APP_API_BASEURL=/
VITE_APP_DEMO_MODE=false
VITE_BUILD_MOCK=false
```

如果生产环境请求仍包含 `/proxy/api/...`，说明发布包使用了错误的前端构建产物。

## 唯一产品版本

MRR 产品版本只由仓库根目录 `VERSION` 决定，内容是不带 `v` 前缀的 SemVer：

```text
0.4.0
```

对应关系：

```text
VERSION            0.4.0
Git Tag            v0.4.0
GitHub Release     MRR v0.4.0
Windows ZIP        MRR-v0.4.0.zip
发布目录           v0.4.0-<short-commit>
```

PR 验证包会追加短 Commit，例如：

```text
MRR-v0.4.0-abcdef12.zip
```

## 受管理发布包

从本基线开始，`mrrctl.ps1` 只把包含以下文件的 ZIP 视为受管理发布包：

```text
backend/mrr-backend.jar
frontend/index.html
docs/user/
docs/internal/
deploy/windows/
runtime/nginx/
runtime/winsw/
runtime/versions.json
runtime/SHA256SUMS
VERSION
release-baseline.json
manifest.json
SHA256SUMS
release-notes.md
```

基线建立前生成的旧格式 ZIP 缺少版本清单和兼容性声明，**不能直接作为 `mrrctl rollback` 的目标**。需要回到旧版本时，应先按数据库备份恢复和人工部署流程处理，不能绕过发布基线冒险切换旧 JAR。

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
    "reason": "当前版本不允许仅切回旧应用的原因"
  },
  "configuration": {
    "schemaVersion": 1
  }
}
```

## 发布前校验

在构建机仓库根目录执行：

```bash
python scripts/release_baseline.py validate
```

该命令检查：

- `VERSION` 是否为 SemVer；
- Maven `revision` 是否与 `VERSION` 一致；
- Flyway 正式迁移命名和版本是否有效；
- manifest 数据库最低、最高迁移是否存在；
- 最高兼容迁移是否为当前最新正式迁移；
- 回滚和配置结构声明是否完整。

GitHub Actions 还会检查：

- Tag 是否严格等于 `v<VERSION>`；
- 后端 JAR 中 `build.version` 和 Git SHA；
- 前端产物中的产品版本和 Git SHA；
- 两个文档站点的产品版本；
- ZIP 内 manifest 和 SHA256 清单。

## 运维命令

```powershell
$ctl = 'C:\MRR\ops\mrrctl.ps1'

# 状态与诊断
& $ctl status
& $ctl doctor

# 服务控制
& $ctl start all
& $ctl stop backend
& $ctl restart all

# 维护模式
& $ctl maintenance on -Message '系统升级中，请稍后再试。'
& $ctl maintenance off

# 独立 Nginx 控制器
$nginx = 'C:\MRR\ops\nginxctl.ps1'
& $nginx status
& $nginx start
& $nginx test
& $nginx reload
& $nginx pause -Message '系统升级中，请稍后再试。'
& $nginx resume
& $nginx restart
& $nginx stop

# 也可使用 CMD 入口
C:\MRR\ops\nginx-control.cmd status

# 版本信息
& $ctl version
& $ctl versions

# 部署当前受管理发布包
& $ctl deploy C:\MRR\packages\MRR-v0.4.0.zip

# 只回滚到 previous 指向的另一份受管理发布包
& $ctl rollback previous

# 仅在数据库已经人工恢复或确认兼容时强制执行受管理包回滚
& $ctl rollback previous -Force

# 日志
& $ctl logs backend -Tail 300
& $ctl logs backend-service -Tail 300
& $ctl logs gateway -Tail 300
```

`-Force` 只跳过当前 manifest 的回滚许可判断，不会让旧格式 ZIP 变成受管理发布包。

## 部署过程

`mrrctl deploy` 执行：

1. 解压发布包；
2. 校验必需文件、manifest 结构、`VERSION` 一致性和 SHA256；
3. 创建不可变发布目录；
4. 开启维护模式；
5. 停止后端；
6. 切换 `current`，保留 `previous`；
7. 启动后端并等待 `/actuator/health`；
8. 读取 `/actuator/info`，核对运行版本和 Git Commit；
9. 重新加载 Nginx 并关闭维护模式；
10. 清理超出保留数量且未被 `current/previous` 引用的旧目录。

默认保留最近 5 个版本，可用 `-KeepReleases` 调整。

## 回滚规则

`applicationRollback.allowed` 是是否可以只切换应用文件的最终判断：

- `true`：仍需确认目标受管理旧版本在数据库兼容范围内；
- `false`：普通回滚被拒绝，必须先恢复数据库或完成专项兼容处理；
- `-Force`：仅用于已经人工完成数据库恢复或明确确认兼容的受管理发布包。

`database.backwardCompatibleWithPreviousApplication=false` 表示升级后的数据库尚未通过上一应用版本兼容演练，不应直接切回旧 JAR。

如果新版本部署后健康检查失败：

- manifest 允许应用回滚时，脚本可恢复原受管理应用版本；
- manifest 禁止应用回滚时，脚本保持维护模式，不会冒险启动可能不兼容的旧应用。

Flyway 不自动降级。应用回滚从来不等于数据库回滚。

## 运行身份核对

后端管理端口仅监听本机：

```text
http://127.0.0.1:18046/actuator/health
http://127.0.0.1:18046/actuator/info
```

`/actuator/info` 至少应包含：

```json
{
  "build": {
    "version": "0.4.0"
  },
  "git": {
    "commit": {
      "id": "abcdef1234567890abcdef1234567890abcdef12"
    }
  }
}
```

该版本和 Commit 必须与当前发布目录中的 `manifest.json` 完全一致。`mrrctl deploy` 和 `rollback` 会自动执行此检查。

## 维护模式

MRR 不使用系统工具挂起 Java 或 Nginx 进程。`nginxctl.ps1 pause` 表示开启维护模式：Nginx 保持运行并返回 503 维护页，后端可完成已经进入的请求，本机 Actuator 仍保持可访问；`resume` 恢复正常流量。

## 目录结构

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
├─ backups
├─ shared
├─ state
└─ ops
```

## 常见故障

### 开发登录返回 403 Invalid CORS request

完全停止并重新启动 Vite，确认请求地址是：

```text
http://localhost:9200/proxy/api/v1/auth/login
```

该问题发生在开发代理层，不等于账号无权限。

### 生产环境仍请求 `/proxy`

使用 `.env.production` 删除旧 `dist` 后重新构建，并清理浏览器缓存。

### API 返回 502

检查后端是否监听：

```text
127.0.0.1:18045
```

### 后端服务存在但健康检查失败

```powershell
Get-Service MRR-Backend
Get-Content C:\MRR\logs\backend\img-api.log -Tail 300
C:\MRR\ops\mrrctl.ps1 doctor
```

重点检查数据库连接、Flyway、密钥、图片目录和端口占用。

### 发布失败且 manifest 禁止自动恢复旧应用

保持维护模式，不要反复切换版本。先判断数据库迁移是否已经执行，按发布前备份或专项兼容方案处理后，再决定是否对受管理包使用 `-Force`。

更多版本治理规则见 `vitepress-doc/internal/release.md`。
