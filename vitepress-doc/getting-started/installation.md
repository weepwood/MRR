# 安装指南

本文说明如何在本地开发环境和 Windows/Linux 生产环境安装 MRR。

::: warning 部署原则
正式环境不依赖 Docker。仓库中的 Docker 配置只用于开发、测试或演示。生产环境建议使用 PostgreSQL 16、Spring Boot JAR、Nginx 静态站点和独立图片存储。
:::

## 环境要求

| 组件 | 要求 | 用途 |
| --- | --- | --- |
| JDK | 21+ | 后端编译与运行 |
| Maven | 3.9+ | 后端构建 |
| PostgreSQL | 16+ | 业务数据库 |
| Node.js | `^20.19.0` 或 `>=22.12.0` | 前端与文档构建 |
| pnpm | 10.33.0 | 前端依赖管理 |
| Nginx | 当前稳定版 | 静态资源、API 代理和文档鉴权 |

图片通常不存入 PostgreSQL，需要单独规划本地/NAS 或 OSS 容量。

## 获取代码

```bash
git clone https://github.com/weepwood/MRR.git
cd MRR
git checkout dev-no-login
```

## 准备 PostgreSQL

### 创建数据库

```sql
CREATE DATABASE imgapi;
```

默认连接：

```text
jdbc:postgresql://localhost:5432/imgapi?currentSchema=app
```

### Flyway 说明

当前配置：

```properties
spring.flyway.locations=classpath:db/migration,classpath:db/callback
spring.flyway.schemas=app
spring.flyway.default-schema=app
spring.flyway.out-of-order=true
spring.flyway.ignore-migration-patterns=*:missing,*:future
spring.flyway.baseline-on-migrate=false
spring.flyway.validate-on-migrate=true
spring.flyway.validate-migration-naming=true
```

当前仓库不使用单一 `V0__baseline_schema.sql` 作为唯一初始化入口。新数据库应执行 `db/migration` 下的完整迁移链。

现有数据库部署前必须：

1. 完成全量备份。
2. 查询并保存 `app.flyway_schema_history`。
3. 对比待发布迁移文件。
4. 在数据库副本验证迁移。
5. 不删除历史表、不修改已执行迁移、不用 `repair` 掩盖差异。

新迁移统一使用：

```text
VyyyyMMddHHmmss__description.sql
```

### 本地 PostgreSQL 容器（可选）

```bash
docker compose up -d postgres
```

仅用于开发环境。

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

至少配置：

```properties
server.port=18045
spring.datasource.url=jdbc:postgresql://localhost:5432/imgapi?currentSchema=app
spring.datasource.username=postgres
spring.datasource.password=你的数据库密码

image.basePath=C:\path\to\your\images
image.url=http://localhost:8005/ba-img
```

敏感值优先使用环境变量：

```powershell
$env:SPRING_DATASOURCE_PASSWORD = '你的数据库密码'
$env:JWT_SECRET_KEY = '足够长的随机 JWT 密钥'
$env:AES_SECRET_KEY = '至少 32 字节的随机 AES 密钥'
$env:OSS_ACCESS_KEY_ID = 'OSS AccessKey ID'
$env:OSS_ACCESS_KEY_SECRET = 'OSS AccessKey Secret'
```

`application-local.properties` 不应提交到 Git。

## 启动后端

```bash
cd backend-repo
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

或构建 JAR：

```bash
mvn clean package
java -jar target/imgapi-0.2.0.jar --spring.profiles.active=local
```

默认地址：

| 地址 | 用途 |
| --- | --- |
| `http://127.0.0.1:18045` | 业务 API |
| `http://127.0.0.1:18046/actuator/health` | 本机健康检查 |
| `http://127.0.0.1:18045/swagger-ui.html` | 后端直连 Springdoc |

正式环境应通过受保护的 `/api-docs/` 访问 API 文档，不要公开 Actuator。

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

生产构建：

```bash
pnpm lint:tsc
pnpm test:run
pnpm build
```

最低浏览器版本为 Edge 111、Chrome 109、Firefox 114 和 Safari 16.4。Chrome 109 以核心业务流程可用为兼容目标，部分较新的视觉效果会自动降级。

## 构建文档

```bash
cd vitepress-doc
npm install
npm run docs:changelog
npm run docs:build
```

开发模式：

```bash
npm run docs:dev:user
npm run docs:dev:internal
```

构建产物：

```text
vitepress-doc/.vitepress/dist-user
vitepress-doc/.vitepress/dist-internal
```

用户站点与内部站点独立构建，防止内部内容进入用户搜索索引。文档构建前会自动从 Git 提交刷新更新记录；无 `.git` 环境下保留仓库内快照。

## 编号与图片目录

病案号和上架号保留原始格式：

- `123` 保持 `123`。
- `00000123` 保持 `00000123`。
- 不自动补齐 8 位。

导入数据、数据库记录和图片文件夹必须一致。高位病案号 `>= 10000000` 查询时必须提供上架号。

## 图片来源

系统设置支持：

- `local`：默认，本地或 NAS 图片。
- `oss`：对已迁移记录生成 OSS 签名 URL，失败时回退本地。

服务端 ZIP 当前仍从本地存储读取。浏览器端 PDF 读取当前有效图片 URL，需要图片服务允许管理端来源的 CORS：

```nginx
add_header Access-Control-Allow-Origin "https://mrr.example.com" always;
add_header Access-Control-Allow-Methods "GET, HEAD, OPTIONS" always;
add_header Access-Control-Allow-Headers "Content-Type, Range" always;
```

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
  └── /api/            Spring Boot :18045
                         ├── PostgreSQL 16
                         ├── 本地/NAS 或 OSS
                         └── Actuator 127.0.0.1:18046
```

## 初次验证

- [ ] 当前迁移链在空数据库完整执行
- [ ] 现有数据库 Flyway 历史已核对
- [ ] Actuator health 返回 `UP`
- [ ] 前端能登录并加载真实数据
- [ ] 短编号和前导零编号均保持原始格式
- [ ] 高位病案号必须与上架号成对查询
- [ ] `mr_archive` 与业务表 `archive_id` 关联正常
- [ ] 本地图片可访问
- [ ] OSS 模式和本地回退正常
- [ ] 身份证查询后 URL 不保留明文
- [ ] 图片 CORS 支持 PDF 导出
- [ ] `/status` 能显示状态和运行区间
- [ ] 用户手册、内部文档和 `/api-docs/` 权限正确
- [ ] 默认密码和所有密钥已替换

更完整的上线操作见 [生产运行手册](/internal/runbook) 和 [数据导入与迁移](/internal/data-migration)。