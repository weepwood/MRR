# 故障排查

## 排查顺序

先判断问题属于哪一层：

1. 浏览器与前端静态资源。
2. Nginx 路由或权限子请求。
3. Spring Boot 业务进程。
4. PostgreSQL 与 Flyway。
5. 图片文件服务或 OSS。
6. Actuator、Prometheus、Grafana 等运维组件。

不要在未确认层级前同时修改多个配置。

## 后端无法启动

### 数据库连接失败

```powershell
Test-NetConnection 127.0.0.1 -Port 5432
```

核对 PostgreSQL 服务、数据库名 `imgapi`、Schema `app`、账号密码、`pg_hba.conf` 和环境变量覆盖。

### 新数据库 V0 初始化失败

检查：

- `spring.flyway.locations=classpath:db/migration`。
- `spring.flyway.baseline-on-migrate=false`。
- 当前目录中只有 V0 基线及 V0 之后正式新增的迁移。
- 应用账号能创建 `app` Schema、表、索引、函数和视图。
- `pg_stat_statements`、`pg_trgm` 等扩展已由管理员创建，或应用账号具备创建权限。

不要把 `migration-legacy` 加入 Flyway locations。

### 旧数据库启动出现迁移冲突

已经运行旧增量迁移链的数据库不能直接切换到 V0。禁止：

- 删除 `flyway_schema_history`。
- 随意执行 `flyway repair`。
- 开启 `baseline-on-migrate` 强行接管。
- 把 V0 插入旧迁移链。

应停止升级，恢复发布前状态，并制定结构对比、备份、数据搬迁或兼容迁移方案。

### 缺少密钥

```powershell
$env:JWT_SECRET_KEY
$env:AES_SECRET_KEY
```

生产密钥应由服务配置或密钥管理系统注入，不长期暴露在命令历史中。

## 前端无法启动

### 依赖冲突

```bash
corepack pnpm@10.33.0 install --frozen-lockfile
```

前端项目使用 pnpm，不使用 `npm install`，也不使用 `--legacy-peer-deps` 掩盖依赖问题。

### 页面空白

检查浏览器首个错误、JS/CSS 404、Nginx SPA 回退、部署 Base、API 地址和运行模式。

### 页面切换残影或 Padding 消失

检查是否重新加入离场页面叠层、档案袋是否错误进入 `KeepAlive`、离开时是否清理 `body.archive-immersive`、全局监听和定时器。

### 弹窗打开页面抖动

项目通过 `scrollbar-gutter: stable` 预留空间，并覆盖 Element Plus 临时 Body 宽度：

```css
body.el-popup-parent--hidden {
  width: auto !important;
}
```

不要逐页面关闭滚动锁定。

## VitePress 无法启动

### `listen EACCES`

Windows 常见原因是端口位于系统排除范围：

```powershell
netsh interface ipv4 show excludedportrange protocol=tcp
netsh interface ipv6 show excludedportrange protocol=tcp
```

当前 `run-docs.mjs` 会从请求端口开始探测，遇到 `EACCES` 或 `EADDRINUSE` 自动选择后续可用端口，并打印实际地址：

```bash
npm run docs:dev:internal -- --port 5310
```

普通进程占用可检查：

```powershell
Get-NetTCPConnection -LocalPort 5310 -ErrorAction SilentlyContinue
```

### Shiki 语言回退警告

`env` 或 `gitignore` 高亮回退不会导致启动失败。代码块使用 `dotenv`、`text` 或 `properties`。

### 文档资源 404

核对用户手册 Base `/docs/`、内部文档 Base `/docs/internal/`，以及 Nginx location 与部署目录是否一致。

## API 请求失败

### 401

检查 JWT 是否存在且未过期、后端 JWT 密钥是否变化、前端是否重复登出。文档 Cookie 与业务 JWT 用途不同，不能混用。

### 403

检查角色权限、路由 `meta.auth` 与后端接口权限。内部文档和 Swagger 需要 `system:read` 或管理员。

### 同一错误出现两条提示

页面已经提示业务错误时，请求层不应重复提示。检查是否绕过统一错误去重，或同时使用多个消息组件。

## 影像档案袋查询异常

### 高位病案号查错患者

病案号大于或等于 `10000000` 时必须同时提供上架号，后端使用 `BAH AND SJH`，不能使用 OR。

### 前导零丢失

病案号与上架号必须使用字符串保存和传输，不能转换为数字。

### 身份证仍留在 URL

查询完成后应使用 `router.replace` 将明文替换为不透明令牌。检查 POST 返回令牌和路由替换是否执行。

### 刷新后仍显示旧图片

检查图片刷新接口是否返回并保存新的版本参数，前端图片 URL 是否携带该版本，避免浏览器复用旧缓存。

## 图片与 PDF

图片无法显示时依次检查数据库文件名与目录、后端 URL、浏览器直接访问、MIME、Nginx 映射和 OSS 状态。

PDF 出现 CORS 错误时检查图片服务：

```nginx
add_header Access-Control-Allow-Origin "http://localhost:9000" always;
add_header Access-Control-Allow-Methods "GET, HEAD, OPTIONS" always;
add_header Access-Control-Allow-Headers "Content-Type, Range" always;
```

还需检查图片状态、空文件、浏览器解码、Canvas 尺寸和选中图片是否属于同一档案袋。

## 监控没有数据

### Prometheus Target DOWN

检查 Actuator `127.0.0.1:18046`、网络边界、postgres_exporter 连接、监控角色和 PostgreSQL 扩展。

### Grafana 无看板

检查 Datasource/Dashboard provisioning、`MRR_GRAFANA_DASHBOARD_PATH`、Prometheus 地址和 Dashboard UID。

### 数据质量按钮不可用

检查 `system:read`、是否已有任务运行、数据质量表是否由 V0 创建；后台 Cron 应保持禁用。

## 保留证据

缺陷或事故记录应包含 Commit SHA、精确时间与时区、首个错误和完整堆栈、脱敏请求信息、request ID、Flyway 版本、浏览器/Node/Java/PostgreSQL 版本和复现步骤。不要只提供最后一行错误。