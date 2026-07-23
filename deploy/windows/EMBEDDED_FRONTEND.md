# JAR 内嵌前端部署

MRR 的正式 Windows 发布包会同时提供两份前端：

- `backend/mrr-backend.jar` 中的 `BOOT-INF/classes/static/`：默认生产入口；
- `frontend/`：第一阶段保留的紧急回退副本。

Nginx 仍负责 80 端口、维护模式、入口日志、文档和 API 代理，但主页面默认代理给 Spring Boot，由同一个 JAR 返回 Vue 页面和静态资源。

## 新安装

执行 `install.ps1` 后会创建：

```text
C:\MRR\config\nginx\frontend-mode.inc
```

默认内容为内嵌模式。启动服务后可检查：

```powershell
C:\MRR\ops\mrrctl.ps1 status
C:\MRR\ops\mrrctl.ps1 doctor
```

`status` 中的 `FrontendMode` 应为 `embedded`，`doctor` 会验证当前 JAR 是否包含 `index.html` 和 Vite assets。

## 已有服务器迁移

先按原流程部署包含该功能的新发布包。即使旧 Nginx 仍读取 `current/frontend`，系统也可以继续工作。

随后以管理员身份执行一次：

```powershell
C:\MRR\current\deploy\windows\migrate-embedded-frontend.ps1 -Root C:\MRR
```

迁移脚本会：

1. 备份现有 Nginx 配置；
2. 写入支持双模式切换的新配置；
3. 默认启用 JAR 内嵌前端；
4. 校验 Nginx 配置；
5. 更新 `ops` 目录中的管理脚本；
6. Nginx 正在运行时执行平滑重载；
7. 任一步骤失败时恢复原配置。

迁移不会修改数据库配置、JWT/HMAC 密钥、OSS 凭据或图片目录。

## 模式切换

默认使用 JAR 内嵌前端：

```powershell
C:\MRR\ops\mrrctl.ps1 frontend embedded
```

临时切回发布包中的外置前端：

```powershell
C:\MRR\ops\mrrctl.ps1 frontend external
```

一键管理中心也提供“使用 JAR 内嵌前端”和“使用外置前端回退”按钮。

切换到内嵌模式前，脚本会检查：

- `BOOT-INF/classes/static/index.html` 存在且非空；
- `BOOT-INF/classes/static/assets/` 至少包含一个资源文件。

切换到外置模式前，脚本会检查 `current/frontend/index.html`。每次切换都会先执行 `nginx -t`，验证失败会自动恢复原模式。

## 缓存策略

- `index.html` 和 Vue 浏览器路由：`Cache-Control: no-cache`；
- `/assets/` 和 `/browser_upgrade/`：一年不可变缓存；
- API 不套用前端静态资源缓存策略；
- Nginx 继续动态 gzip Spring Boot 返回的静态文件。

## 保留 Nginx 的原因

前端进入 JAR 不代表删除 Nginx。Nginx 仍承担：

- 80 端口统一入口；
- 维护模式；
- 访问与审计日志；
- 文档站点；
- API 和 Swagger 路由；
- 静态资源缓存和压缩；
- 隔离仅监听 `127.0.0.1:18045` 的 Spring Boot。

病案图片、OSS 文件、配置和密钥不会写入 JAR。
