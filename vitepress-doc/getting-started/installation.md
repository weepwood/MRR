# 安装指南

本文说明如何在本地开发环境和常规生产环境中安装 MRR `v0.1.1`。

::: warning 部署原则
正式环境不依赖 Docker。仓库中的 Dockerfile 和 Docker Compose 只用于本地开发、测试或演示。生产环境建议使用 PostgreSQL、Spring Boot JAR、Nginx 静态站点和独立图片服务。
:::

## 环境要求

| 组件 | 要求 | 用途 |
|------|------|------|
| JDK | 21+ | 后端编译与运行 |
| Maven | 3.9+ | 后端构建 |
| PostgreSQL | 16+ | 业务数据库 |
| Node.js | `^20.19.0` 或 `>=22.12.0` | 前端与文档构建 |
| pnpm | 10.33.0 | 前端依赖管理 |
| Nginx | 当前稳定版 | 生产静态资源、代理与文档鉴权 |

图片通常不直接存入 PostgreSQL，需要单独评估图片目录或 OSS 容量。

## 获取代码

```bash
git clone https://github.com/weepwood/MRR.git
cd MRR
git checkout dev-no-login
```

## 准备 PostgreSQL

### 新数据库

以管理员账号创建数据库：

```sql
CREATE DATABASE imgapi;
```

默认连接：

```text
jdbc:postgresql://localhost:5432/imgapi?currentSchema=app
```

新数据库由以下唯一初始迁移创建：

```text
backend-repo/src/main/resources/db/migration/V0__baseline_schema.sql
```

关键配置：

```properties
spring.flyway.locations=classpath:db/migration
spring.flyway.schemas=app
spring.flyway.default-schema=app
spring.flyway.baseline-on-migrate=false
spring.flyway.validate-on-migrate=true
```

V0 会创建当前表、索引、视图、函数、注释和必要种子数据。部署前需要确认 PostgreSQL 管理员已经允许创建 `app` Schema，并处理 `pg_stat_statements`、`pg_trgm` 等扩展权限。

### 旧数据库

`db/migration-legacy` 保存 V0 重整前的历史增量迁移，只用于审计，不会被当前 Flyway 执行。

已经使用旧迁移链的数据库不能直接切换到 V0。禁止删除 `flyway_schema_history`、强制执行 `repair` 或启用自动基线。必须先制定结构比较、备份、数据搬迁、校验和回滚方案。

### 本地容器（可选）

```bash
docker compose up -d postgres
```

该命令仅用于开发环境。

## 配置后端

复制模板：

```bash
# Linux / macOS
cp backend-repo/src/main/resources/application-local.template.properties \
  backend-repo/src/main/resources/application-local.properties

# Windows PowerShell
Copy-Item backend-repo/src/main/resources/application-local.template.properties `
  backend-repo/src/main/resources/application-local.properties
```

至少设置：

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

同时通过环境变量设置 JWT：

```powershell
$env:JWT_SECRET_KEY = '本地开发签名密钥'
$env:AES_SECRET_KEY = '本地开发 AES 密钥'
```

`application-local.properties` 包含敏感信息，不应提交到 Git。

## 启动后端

```bash
cd backend-repo
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

或：

```bash
mvn clean package
java -jar target/imgapi-*.jar --spring.profiles.active=local
```

默认地址：

| 地址 | 用途 |
|------|------|
| `http://127.0.0.1:18045` | 业务 API |
| `http://127.0.0.1:18046/actuator/health` | 本机健康检查 |
| `http://127.0.0.1:18045/swagger-ui.html` | 后端直连 Springdoc |

正式环境优先使用受保护的 `/api-docs/`，不要公开直连 Springdoc 和 Actuator。

## 启动前端

```bash
cd frontend-fantastic-admin
corepack pnpm@10.33.0 install --frozen-lockfile
pnpm dev
```

默认访问：

```text
http://localhost:9000
```

开发配置关键项：

```dotenv
VITE_APP_API_BASEURL = http://localhost:18045
VITE_APP_DEMO_MODE = true
VITE_OPEN_PROXY = true
```

联调真实登录和权限时，将 `VITE_APP_DEMO_MODE` 设为 `false`。

生产构建：

```bash
pnpm lint:tsc
pnpm test:run
pnpm build
```

## 启动与构建文档

```bash
cd vitepress-doc
npm install

npm run docs:dev:user
npm run docs:dev:internal
npm run docs:build
```

构建产物：

```text
vitepress-doc/.vitepress/dist-user
vitepress-doc/.vitepress/dist-internal
```

两个站点必须独立构建，避免用户搜索索引包含内部资料。

Windows 上若请求端口被系统排除或占用，`run-docs.mjs` 会自动向后探测可用端口并打印实际地址：

```bash
npm run docs:dev:internal -- --port 5310
```

## 图片服务与 PDF 导出

影像档案袋的 PDF 由浏览器直接读取图片生成。跨域时图片服务需要允许管理端来源：

```nginx
add_header Access-Control-Allow-Origin "https://mrr.example.com" always;
add_header Access-Control-Allow-Methods "GET, HEAD, OPTIONS" always;
add_header Access-Control-Allow-Headers "Content-Type, Range" always;
```

前端请求使用 `credentials: omit`，通常不需要 `Access-Control-Allow-Credentials`。不要通过关闭浏览器安全策略解决跨域问题。

## 生产部署结构

```text
Browser
  │
  ▼
Nginx :443
  ├── /                Vue 管理端
  ├── /docs/           用户手册
  ├── /docs/internal/  内部文档
  ├── /api-docs/       Springdoc
  └── /api/            Spring Boot API
                         ├── PostgreSQL 16
                         ├── 图片服务或 OSS
                         └── 127.0.0.1:18046 Actuator
```

部署步骤：

1. 新库从 V0 初始化；旧库使用单独迁移方案。
2. 使用环境变量提供数据库密码、JWT、AES 和 OSS 凭据。
3. 构建并以系统服务运行后端 JAR。
4. 构建前端、用户文档和内部文档。
5. 使用 Nginx 托管静态资源并代理 API。
6. 配置文档 `auth_request`、HTTPS、安全响应头和图片 CORS。
7. 按 `monitoring/README.md` 部署原生监控。

## 初次验证

- [ ] 新数据库只执行 V0 及 V0 之后的正式增量迁移
- [ ] `http://127.0.0.1:18046/actuator/health` 返回 `UP`
- [ ] 前端能够加载真实业务数据
- [ ] `/archive?bah=...` 能读取图片
- [ ] 高位病案号必须与上架号成对查询
- [ ] 身份证查询后 URL 不保留明文
- [ ] 图片服务 CORS 允许 PDF 导出
- [ ] `/status` 能显示状态与运行区间
- [ ] 用户手册、内部文档和 `/api-docs/` 权限正确
- [ ] 默认密码和所有密钥已替换

更完整的生产说明见 [内部部署文档](/internal/deployment) 和 [故障排查](/internal/troubleshooting)。