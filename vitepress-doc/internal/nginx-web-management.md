# MRR Nginx Web 运维管理（第一阶段）

> 风险等级：P1。此功能仅针对 MRR Windows 离线包自带的 `MRR-Gateway`，不是 Nginx 图片源浏览器，也不是 Nginx UI 的替代品。

## 功能与边界

在管理后台「运维 → Nginx 管理」（`/nginx-management`）查看网关状态、版本、受管配置文件元数据，并在受控条件下执行 `nginx -t`；拥有日志权限的用户可以查看固定文件的少量脱敏日志。

不支持 Web 端配置编辑、证书写入、Nginx 启停、重启、重载与任意命令。此阶段不新增数据库和独立代理进程。生产中需要启停或重载，仍由本机管理员使用 `C:\MRR\ops\MRR-管理中心.cmd` / `nginxctl.ps1`。

## 启用与关闭

生产默认 **关闭**。由服务器管理员在非 Git 仓库的外置 `C:\MRR\config\application-prod.properties` 中增加：

```properties
app.operations.nginx.enabled=true
app.operations.nginx.root=C:/MRR
```

也可以设置环境变量 `APP_OPERATIONS_NGINX_ENABLED=true`、`APP_OPERATIONS_NGINX_ROOT=C:/MRR`；外置配置属性优先级可能高于环境变量，须按实际 Spring 配置加载顺序核实。变更配置后需重启 **MRR-Backend**（不是 Nginx）。紧急关闭时设为 `false` 并重启后端。

必须使用受管包的 `runtime/nginx/nginx.exe`、`config/nginx/nginx.conf`，以及 WinSW 服务 `MRR-Gateway`；本模块不会扫描系统或发现其他 Nginx 实例。

Spring Boot 服务账号至少需要读取这些文件、读取 `logs/nginx/access.log` 与 `error.log`。配置检查可能尝试读取证书等包含文件、日志目录，并需要相应权限；失败时不要将 **MRR-Backend** 改为系统管理员，而应修正精确的文件 ACL 或在测试机复核配置。如果安全策略不允许服务账号执行 `nginx -t`，保持此功能关闭。

## API 与权限

| 接口 | 方法 | 权限 | 用途 |
| --- | --- | --- | --- |
| `/api/v1/nginx/management/status` | GET | `system:read` | 状态、版本、安装检测 |
| `/api/v1/nginx/management/config/files` | GET | `system:manage` | 受管配置文件名、大小、修改时间（不包含正文） |
| `/api/v1/nginx/management/config/test` | POST | `system:manage` | 固定二进制、固定参数执行 `nginx -t`；不写入配置，不重载 |
| `/api/v1/nginx/management/logs` | GET | `system:read` **且** `log:read` | `type=access|error&lines=1..200` |

全部使用已有 JWT、AuthorizationInterceptor、全局请求审计及统一请求客户端。前端权限判断仅控制展示，实际授权在后端完成。无 token 返回 401、权限不足返回 403；禁用状态禁止服务端磁盘和子进程操作。

## 安全限制

- 根目录来自本机环境/应用配置，**不得**接受来自 HTTP 的可执行程序路径、脚本、配置文件名和命令参数；
- 一次 `nginx -t` 最长运行 10 秒，同一时刻只允许一次，进程输出上限 4 KB；原始输出不返回浏览器，避免暴露服务器路径；
- 仅能查看 `access.log` 和 `error.log`，每次读取不超过 128 KB 或 200 行，使用真实路径检查阻止链接逃逸；GET 不能遍历文件系统；
- 对返回日志进行基本脱敏（URL 查询参数、认证头、档案入口路径标识）以及单行限长；这不是对任意自定义日志格式的完备脱敏，**禁止在 Nginx access/error log 中写入病案数据或密钥**；
- 页面只使用 Vue 文本插值展示日志，避免将日志内容当作 HTML 运行；
- `nginx -t` 成功不能替代站点健康检查，不会进行 reload；
- 此功能受 MRR 原有维护模式及 Nginx 代理路径控制；后端被攻击时不可依赖页面提供带外恢复。

## 手动验收

1. **默认关闭**：登录管理员可看见管理页中的禁用状态，配置检测/日志读取直接请求应被拒绝。
2. **权限矩阵**：未登录 401；只有 `record:read` 访问 status 403；只有 `system:read` 不能调用 config/test；缺少 `log:read` 不能读取日志。
3. **Windows 测试机启用**：核对 `MRR-Gateway`、`nginx.exe`、`nginx.conf`；status 识别版本、serviceState；不改变服务运行状态。
4. **配置检查**：对有效配置执行 test，返回 valid=true；模拟缺失文件、无权限和检查超时时均不重载 Nginx。
5. **日志**：access/error 读取行数不超过限制；查询 token 和档案入口标识不出现在返回内容；尝试 `../../secrets` 返回失败。
6. **禁用回滚**：将开关设为 false 并重启后端，确认所有受控操作不再执行，MRR 原业务路由和 Nginx 进程仍可用。
7. **兼容性**：`backend-repo/mvnw.cmd test`、`pnpm lint:tsc`、`pnpm build`；Windows Server + Chrome 86 检查。

此版本不改动已有 Nginx 模板、Windows 运维脚本、WinSW 配置、发布规则或数据库结构。

## 下一阶段（需独立 ADR / PR）

编辑 Nginx 配置、自动发布、重载和回滚会涉及更高权限控制，应设计最小权限的本地特权执行入口、备份/候选校验/锁、健康检查与带外恢复机制。禁止直接将 Spring Boot 服务提升为 Windows Administrator。
