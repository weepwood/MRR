# 安装指南

本文说明如何在本地开发环境和常规生产环境中部署 MRR。

::: warning 部署原则
正式环境不依赖 Docker。仓库中的 Dockerfile 和 Docker Compose 只用于本地开发、测试或演示。生产环境建议使用 PostgreSQL 服务、Spring Boot JAR、Nginx 静态站点和独立图片服务。
:::

## 环境要求

| 组件 | 最低要求 | 说明 |
| --- | --- | --- |
| JDK | 21 | 后端编译与运行 |
| Maven | 3.9+ | 后端构建 |
| PostgreSQL | 16+ | 业务数据库 |
| Node.js | `^20.19.0` 或 `>=22.12.0` | 前端构建 |
| pnpm | 10.33.0 | 前端依赖管理 |
| npm | Node.js 自带 | VitePress 文档构建 |
| Nginx | 建议使用当前稳定版 | 生产静态资源、反向代理和文档访问控制 |

硬件容量取决于病案数量、图片存储方式和并发量。图片通常不直接保存在 PostgreSQL 中，必须单独评估图片目录或 OSS 容量。

## 获取代码

```bash
git clone https://github.com/weepwood/MRR.git
cd MRR
git checkout dev-no-login
```

## 准备 PostgreSQL

### 创建数据库

以 PostgreSQL 管理员登录：

```sql
CREATE DATABASE imgapi;
```

应用默认连接：

```text
jdbc:postgresql://localhost:5432/imgapi?currentSchema=app
```

后端启动后由 Flyway 创建和迁移 `app` schema。生产环境建议创建独立应用账号，并授予数据库连接、schema 使用和迁移所需权限，而不是长期使用 `postgres` 超级用户。

### 本地容器方式（可选）

只需要快速启动开发数据库时：

```bash
docker compose up -d postgres
```

不要把该命令视为正式部署方案。

## 配置后端

复制本地模板：

```bash
# Linux / macOS
cp backend-repo/src/main/resources/application-local.template.properties \
  backend-repo/src/main/resources/application-local.properties

# Windows PowerShell
Copy-Item backend-repo/src/main/resources/application-local.template.properties `
  backend-repo/src/main/resources/application-local.properties
```

至少修改：

```properties
server.port=18045
spring.datasource.url=jdbc:postgresql://localhost:5432/imgapi?currentSchema=app
spring.datasource.username=postgres
spring.datasource.password=你的数据库密码

aes.secret.key=至少-32-字节的随机密钥

image.basePath=C:\path\to\your\images
image.url=http://localhost:8005/ba-img
image.username=change-me
image.password=change-me
```

`application-local.properties` 包含敏感信息，已被设计为本地文件，不应提交到 Git。

## 启动后端

```bash
cd backend-repo
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

或先构建再运行：

```bash
mvn clean package
java -jar target/imgapi-0.0.8-SNAPSHOT.jar --spring.profiles.active=local
```

默认端口：

| 端口 | 用途 |
| --- | --- |
| `18045` | 业务 API 和 Springdoc 资源 |
| `18046` | 仅本机监听的 Actuator、Health 和 Prometheus |

验证：

```bash
curl http://127.0.0.1:18046/actuator/health
```

后端直连的 Springdoc 默认路径为：

```text
http://localhost:18045/swagger-ui.html
http://localhost:18045/v3/api-docs
```

正式同域部署优先使用受权限保护的 `/api-docs/`，不要把直连 Springdoc 暴露到公网。

## 配置并启动前端

```bash
cd frontend-fantastic-admin
corepack enable
corepack pnpm@10.33.0 install --frozen-lockfile
pnpm dev
```

开发环境默认地址：

```text
http://localhost:9000
```

`frontend-fantastic-admin/.env.development` 的关键项：

```env
VITE_APP_API_BASEURL = http://localhost:18045
VITE_APP_DEMO_MODE = true
VITE_OPEN_PROXY = true
```

- 只浏览前端界面时可以保留展示模式。
- 联调认证、权限和真实业务数据时，将 `VITE_APP_DEMO_MODE` 改为 `false`。
- API 地址改变时同步修改 `VITE_APP_API_BASEURL`。

生产构建：

```bash
pnpm lint:tsc
pnpm test:run
pnpm build
```

产物位于 `frontend-fantastic-admin/dist/`。

## 构建文档

```bash
cd vitepress-doc
npm install

# 用户手册开发服务器
npm run docs:dev:user

# 内部文档开发服务器
npm run docs:dev:internal

# 构建两个站点
npm run docs:build
```

构建产物：

```text
vitepress-doc/.vitepress/dist-user
vitepress-doc/.vitepress/dist-internal
```

用户手册与内部文档必须分别构建，避免用户手册的本地搜索索引包含内部资料。

## 图片服务与 PDF 导出

影像档案袋的 PDF 由浏览器直接读取图片生成。图片服务与前端不同源时，需要允许管理端来源：

```nginx
location /ba-img-01/ {
    root /path/to/image/root;

    if ($request_method = OPTIONS) {
        add_header Access-Control-Allow-Origin "https://mrr.example.com" always;
        add_header Access-Control-Allow-Methods "GET, HEAD, OPTIONS" always;
        add_header Access-Control-Allow-Headers "Content-Type, Range" always;
        return 204;
    }

    add_header Access-Control-Allow-Origin "https://mrr.example.com" always;
    add_header Access-Control-Allow-Methods "GET, HEAD, OPTIONS" always;
    add_header Access-Control-Allow-Headers "Content-Type, Range" always;
}
```

前端图片请求使用 `credentials: omit`，不需要 `Access-Control-Allow-Credentials`。不要通过关闭浏览器安全策略解决跨域问题。

## 生产部署结构

推荐结构：

```text
Browser
  │
  ▼
Nginx :443
  ├── /                Vue 管理端
  ├── /docs/           VitePress 用户手册
  ├── /docs/internal/  VitePress 内部文档
  ├── /api-docs/       Springdoc Swagger UI
  └── /api/            Spring Boot 业务接口
                         │
                         ├── PostgreSQL 16
                         ├── 本地图片服务或 OSS
                         └── 127.0.0.1:18046 Actuator
```

部署步骤：

1. 安装并配置 PostgreSQL 16。
2. 使用环境变量提供数据库密码、AES 密钥、JWT 密钥和 OSS 凭据。
3. 构建并以系统服务方式运行后端 JAR。
4. 构建前端、用户文档和内部文档。
5. 使用 Nginx 托管静态资源并反向代理业务 API。
6. 配置文档 `auth_request`、HTTPS、安全响应头和图片 CORS。
7. 按 `monitoring/README.md` 部署原生监控组件。

## 服务状态配置

后端默认记录自身与数据库的运行历史：

```properties
app.status.enabled=true
app.status.check-interval-ms=60000
app.status.heartbeat-timeout-ms=120000
app.status.retention-days=365
app.status.zone-id=Asia/Shanghai
```

需要把前端静态服务纳入状态判断时：

```properties
app.status.frontend-health-url=http://127.0.0.1:9000/healthz.txt
```

保持为空时只判断后端和数据库，避免开发端口不一致造成误报。

## 初次验证清单

- [ ] `http://127.0.0.1:18046/actuator/health` 返回 `UP`
- [ ] 前端能够打开并加载真实业务数据
- [ ] Flyway 迁移完成且没有 checksum mismatch
- [ ] `/archive?bah=...` 能读取图片
- [ ] 图片服务 CORS 允许 PDF 导出
- [ ] `/status` 能显示当前状态和运行区间
- [ ] 管理员能够打开 `/monitoring`
- [ ] 用户手册、内部文档和 `/api-docs/` 权限符合预期
- [ ] 默认账号密码和所有密钥已替换

## 常见问题

### Flyway 校验和不一致

不要直接修改已经在数据库执行过的迁移，也不要首先执行 `flyway repair`。应恢复原迁移内容，并通过新的版本化迁移实现后续调整。

### 前端能打开但业务数据为空

检查后端是否启动、`VITE_APP_API_BASEURL` 是否正确，以及是否仍处于仅跳过认证的展示模式。

### 状态页在故障时打不开

`/status` 与主后端同进程，不是独立监控服务。后端恢复后会根据最后心跳补录停机区间。

### GitHub Actions 直接失败

仓库工作流可能因账户额度不足在执行步骤前失败。应结合本地 `mvn test`、`pnpm build` 和 `npm run docs:build` 判断变更是否有效。