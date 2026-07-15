# 部署

## 部署原则

MRR 正式环境采用原生进程部署，不依赖 Docker：

```text
Nginx
├── Vue 管理端静态文件
├── VitePress 用户手册
├── VitePress 内部文档
├── Springdoc 反向代理
└── Spring Boot API 反向代理

Spring Boot 业务进程
PostgreSQL
图片文件服务
可选：Prometheus / Grafana / Alertmanager / exporters
```

仓库 Dockerfile 与 Compose 仅用于开发、测试或演示。

## 构建产物

### 前端

```bash
cd frontend-fantastic-admin
corepack pnpm@10.33.0 install --frozen-lockfile
pnpm build
```

产物：`frontend-fantastic-admin/dist/`。

### 后端

```bash
cd backend-repo
mvn clean package
```

产物：`backend-repo/target/imgapi-*.jar`。

### 文档

```bash
cd vitepress-doc
npm install
npm run docs:build
```

产物：

```text
vitepress-doc/.vitepress/dist-user/
vitepress-doc/.vitepress/dist-internal/
```

## 推荐目录

```text
/opt/mrr/                     # Linux
C:\MRR\                      # Windows
├── app/
│   └── imgapi.jar
├── frontend/
├── docs-user/
├── docs-internal/
├── config/
├── logs/
└── monitoring/
```

应用、静态文件、配置和日志分目录管理，升级时避免覆盖密钥与运行数据。

## 后端环境变量

生产至少配置：

```properties
SERVER_PORT=18045
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/imgapi?currentSchema=app
SPRING_DATASOURCE_USERNAME=mrr_app
SPRING_DATASOURCE_PASSWORD=<strong-password>
JWT_SECRET_KEY=<strong-signing-key>
AES_SECRET_KEY=<strong-aes-key>
IMAGE_BASE_PATH=<image-root>
IMAGE_URL=<image-service-url>
```

按需配置 OSS 和状态页前端探测。密钥由系统服务或安全配置文件注入，不写入仓库脚本。

## 数据库初始化

新数据库只使用：

```text
backend-repo/src/main/resources/db/migration/V0__baseline_schema.sql
```

关键配置：

```properties
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=false
```

部署前确认：

- `app` Schema 创建权限。
- V0 中声明的 PostgreSQL 扩展已由管理员创建，或应用账号具备相应权限。
- `migration-legacy` 不在 Flyway locations 中。
- 旧数据库已有独立迁移方案，不能直接启动 V0。

## 启动后端

Linux systemd 示例：

```ini
[Unit]
Description=MRR Backend
After=network.target postgresql.service

[Service]
User=mrr
WorkingDirectory=/opt/mrr/app
EnvironmentFile=/etc/mrr/mrr.env
ExecStart=/usr/bin/java -jar /opt/mrr/app/imgapi.jar
Restart=on-failure
RestartSec=5
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
```

Windows 可使用 WinSW、NSSM 或其他服务管理器运行：

```text
java.exe -jar C:\MRR\app\imgapi.jar
```

服务账户只授予应用、日志和图片所需权限。

## Nginx 路由

| 路径 | 目标 |
|------|------|
| `/` | Vue SPA |
| `/api/` | Spring Boot `18045` |
| `/docs/` | 用户手册，带文档鉴权 |
| `/docs/internal/` | 内部文档，带内部权限鉴权 |
| `/api-docs/` | Swagger UI，带内部权限鉴权 |
| `/v3/api-docs` | 后端 OpenAPI，带内部权限鉴权 |

Vue 路由回退：

```nginx
location / {
    try_files $uri $uri/ /index.html;
}
```

文档鉴权必须覆盖 HTML、JS、CSS、搜索索引和 OpenAPI 文件。

## 图片服务 CORS

```nginx
add_header Access-Control-Allow-Origin "https://mrr.example.internal" always;
add_header Access-Control-Allow-Methods "GET, HEAD, OPTIONS" always;
add_header Access-Control-Allow-Headers "Content-Type, Range" always;
```

正式环境使用明确来源。前端 PDF 请求使用 `credentials: omit`，通常不需要 `Access-Control-Allow-Credentials`。

## 文档路径

用户手册 Base：`/docs/`。

内部文档 Base：`/docs/internal/`。

部署目录与 Nginx location 必须一致，否则静态资源会 404。

## Springdoc 与 Actuator

应用内部 Springdoc：

```text
/swagger-ui.html
/v3/api-docs
/v3/api-docs.yaml
```

对外统一为 `/api-docs/` 并执行权限校验。

Actuator 默认监听 `127.0.0.1:18046`，不要映射公网。跨机采集使用防火墙白名单、VPN 或受控代理。

## 发布顺序

1. 备份数据库与配置。
2. 停止写入或进入维护窗口。
3. 对新数据库执行 V0 初始化，或执行已审查的旧库迁移方案。
4. 部署后端并验证健康检查。
5. 部署前端和文档。
6. 验证图片加载、PDF 和文档鉴权。
7. 恢复业务访问。
8. 观察日志、连接池和错误率。

## 部署验证

- 管理端可以登录。
- `/status` 正常。
- 记录、患者和统计可以读取数据。
- `/archive` 可以按病案号与上架号查询。
- 身份证查询后 URL 不保留明文。
- 图片服务允许 PDF 导出。
- `/docs/`、`/docs/internal/` 和 `/api-docs/` 权限正确。
- `127.0.0.1:18046/actuator/health` 正常。
- Flyway 只显示 V0 或后续新迁移，不加载 legacy 目录。