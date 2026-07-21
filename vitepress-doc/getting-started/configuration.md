# 配置说明

本文以当前 `application.properties`、本地模板和前端环境文件为准。历史文档中的 JPA、Redis、SMTP 或未接入的设置项不属于当前运行配置。

## 配置来源

### 后端

| 来源 | 用途 |
|------|------|
| `backend-repo/src/main/resources/application.properties` | 默认配置和环境变量映射 |
| `application-local.template.properties` | 本地开发模板 |
| `application-local.properties` | 本地私有配置，不提交 Git |
| 操作系统环境变量 | 生产密码、密钥、地址和开关 |

本地启动：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### 前端

| 文件 | 用途 |
|------|------|
| `.env.development` | 开发服务器 |
| `.env.production` | 生产构建 |
| `.env.local` | 当前机器覆盖值 |

### VitePress

`MRR_DOCS_MODE=user` 构建用户手册，`MRR_DOCS_MODE=internal` 构建内部文档。通常由 `scripts/run-docs.mjs` 设置。

## 服务端口

```properties
server.port=${SERVER_PORT:18045}
management.server.port=${MANAGEMENT_SERVER_PORT:18046}
management.server.address=${MANAGEMENT_SERVER_ADDRESS:127.0.0.1}
```

- `18045`：业务 API 和 Springdoc 资源。
- `18046`：Actuator、Health 和 Prometheus，默认只监听本机。

## 数据库与连接池

```properties
spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/imgapi?currentSchema=app}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME:postgres}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}
spring.datasource.hikari.maximum-pool-size=${SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE:20}
spring.datasource.hikari.minimum-idle=${SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE:5}
spring.datasource.hikari.connection-timeout=${SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT:30000}
spring.datasource.hikari.idle-timeout=${SPRING_DATASOURCE_HIKARI_IDLE_TIMEOUT:300000}
spring.datasource.hikari.max-lifetime=${SPRING_DATASOURCE_HIKARI_MAX_LIFETIME:1800000}
```

生产环境必须设置数据库密码。连接池大小需要结合 PostgreSQL `max_connections`、实例数量和并发量调整。

## Flyway V0 基线

```properties
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.schemas=app
spring.flyway.default-schema=app
spring.flyway.baseline-on-migrate=false
spring.flyway.validate-on-migrate=true
```

当前正式迁移入口：

```text
backend-repo/src/main/resources/db/migration/V0__baseline_schema.sql
```

规则：

- 新数据库只从 V0 初始化。
- `db/migration-legacy` 不进入当前迁移位置。
- 已使用旧迁移链的数据库不能直接加载 V0。
- V0 部署后需要结构变化时，新增 V1、V2 等增量迁移。
- 不删除旧数据库的 Flyway 历史，也不通过 `repair` 掩盖迁移链差异。

## 安全密钥

```properties
aes.secret.key=${AES_SECRET_KEY:}
```

JWT 通过 `JWT_SECRET_KEY` 提供。真实环境必须使用强随机且互不相同的密钥。密钥变化会使现有登录 Token 或身份证查询令牌失效。

```powershell
$env:SPRING_DATASOURCE_PASSWORD = 'replace-me'
$env:AES_SECRET_KEY = 'replace-with-a-strong-aes-secret'
$env:JWT_SECRET_KEY = 'replace-with-a-strong-jwt-secret'
```

## 图片服务

```properties
image.url=${IMAGE_URL:http://localhost:8005/ba-img}
image.username=${IMAGE_USERNAME:br_admin}
image.password=${IMAGE_PASSWORD:}
image.basePath=${IMAGE_BASE_PATH:C:/path/to/images}
image.server-url-default=${IMAGE_SERVER_URL_DEFAULT:http://127.0.0.1:8005/ba-img-00}
image.server-url-ba01=${IMAGE_SERVER_URL_BA01:http://127.0.0.1:8005/ba-img-01}
image.server-url-ba02=${IMAGE_SERVER_URL_BA02:http://127.0.0.1:8005/ba-img-02}
image.server-url-ba03=${IMAGE_SERVER_URL_BA03:http://127.0.0.1:8005/ba-img-03}
```

默认路径和地址仅为开发示例，生产环境必须替换。

### 浏览器 PDF 的 CORS

```nginx
add_header Access-Control-Allow-Origin "https://mrr.example.com" always;
add_header Access-Control-Allow-Methods "GET, HEAD, OPTIONS" always;
add_header Access-Control-Allow-Headers "Content-Type, Range" always;
```

前端图片请求使用 `credentials: omit`。

## OSS

```properties
oss.endpoint=${OSS_ENDPOINT:oss-cn-hangzhou.aliyuncs.com}
oss.bucket=${OSS_BUCKET:mrr-medical-records}
oss.access-key-id=${OSS_ACCESS_KEY_ID:}
oss.access-key-secret=${OSS_ACCESS_KEY_SECRET:}
oss.region=${OSS_REGION:cn-hangzhou}
oss.base-url=${OSS_BASE_URL:https://mrr-medical-records.oss-cn-hangzhou.aliyuncs.com}
oss.url-expire-seconds=${OSS_URL_EXPIRE_SECONDS:3600}
```

OSS 密钥必须由环境变量或密钥管理系统提供。迁移后需要核对校验状态和数据质量结果。

## Springdoc 与文档访问

```properties
springdoc.api-docs.enabled=${SPRINGDOC_API_DOCS_ENABLED:true}
springdoc.api-docs.path=/v3/api-docs
springdoc.swagger-ui.enabled=${SPRINGDOC_SWAGGER_UI_ENABLED:true}
springdoc.swagger-ui.path=/swagger-ui.html
```

- 后端直连：`/swagger-ui.html`、`/v3/api-docs`。
- 正式入口：`/api-docs/`。
- 内部文档和 API 文档需要管理员或 `system:read`。
- 文档访问使用短期 HttpOnly Cookie 与 Nginx `auth_request`。

## Actuator 与 Prometheus

```properties
management.endpoints.web.exposure.include=${MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE:health,info,prometheus}
management.endpoint.health.show-details=${MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS:never}
management.prometheus.metrics.export.enabled=${MANAGEMENT_PROMETHEUS_METRICS_EXPORT_ENABLED:true}
management.metrics.tags.application=imgapi
spring.datasource.hikari.metrics.enabled=true
```

不要把管理端口直接暴露公网。

## 服务状态

```properties
app.status.enabled=${APP_STATUS_ENABLED:true}
app.status.check-interval-ms=${APP_STATUS_CHECK_INTERVAL_MS:60000}
app.status.initial-delay-ms=${APP_STATUS_INITIAL_DELAY_MS:60000}
app.status.heartbeat-timeout-ms=${APP_STATUS_HEARTBEAT_TIMEOUT_MS:120000}
app.status.request-timeout-ms=${APP_STATUS_REQUEST_TIMEOUT_MS:3000}
app.status.frontend-health-url=${APP_STATUS_FRONTEND_HEALTH_URL:}
app.status.retention-days=${APP_STATUS_RETENTION_DAYS:365}
app.status.zone-id=${APP_STATUS_ZONE_ID:Asia/Shanghai}
```

前端健康地址为空时，只判断后端和数据库；配置时应指向实际 `/healthz.txt`。

## 数据质量

```properties
app.data-quality.enabled=false
app.data-quality.cron=-
app.data-quality.sample-limit=${APP_DATA_QUALITY_SAMPLE_LIMIT:200}
app.data-quality.retention-days=${APP_DATA_QUALITY_RETENTION_DAYS:90}
```

数据质量检查保持管理员手动触发，不通过 Cron 自动运行。

## 访问日志与应用日志

```properties
app.log-retention.enabled=${APP_LOG_RETENTION_ENABLED:true}
app.log-retention.cron=${APP_LOG_RETENTION_CRON:0 30 2 * * ?}
app.log-retention.retention-days=${APP_LOG_RETENTION_RETENTION_DAYS:1095}
app.log-retention.batch-size=${APP_LOG_RETENTION_BATCH_SIZE:5000}
app.log-retention.max-batches-per-run=${APP_LOG_RETENTION_MAX_BATCHES_PER_RUN:20}

logging.level.com.zjcxph.imgapi.mapper=${MAPPER_LOG_LEVEL:WARN}
logging.file.name=${LOG_FILE_NAME:img-api.log}
logging.logback.rollingpolicy.max-file-size=10MB
logging.logback.rollingpolicy.max-history=30
```

生产环境不建议开启 Mapper DEBUG。

## 前端环境变量

```dotenv
VITE_APP_SETTING = true
VITE_APP_TITLE = MRR-ADMIN
VITE_APP_API_BASEURL = http://localhost:18045
VITE_APP_DEMO_MODE = true
VITE_OPEN_PROXY = true
VITE_OPEN_DEVTOOLS = false
VITE_APP_DISABLE_DEVTOOL = false
```

生产构建必须关闭展示模式并使用实际 API 地址或 Nginx 代理路径。

## 运行时系统设置

管理端系统设置优先保存到 `mr_system_settings`，服务端不可用时回退浏览器本地配置。当前已接入：

- 系统名称。
- 档案袋影像栏和预览模式。
- 缩略图宽度、首批渲染数量和自动适应。
- 记住选中图片。
- 水印开关和透明度。
- 页面标题风格和全局圆角。
- 科室配色。
- 档案袋局部显示项和本地浏览偏好。

数据库、密钥、OSS 凭据、备份和 SMTP 不属于运行时设置页面。

## 生产检查

- [ ] 新数据库使用 V0；旧数据库有独立迁移方案
- [ ] 所有默认账号密码已替换
- [ ] 数据库、AES 和 JWT 密钥已设置
- [ ] Actuator 只允许本机或监控网络访问
- [ ] Springdoc 只通过受保护入口访问
- [ ] 图片路径、服务地址和 CORS 正确
- [ ] OSS 凭据未提交仓库
- [ ] 数据质量保持手动触发
- [ ] 日志保留期符合审计要求
- [ ] `/status` 时区和前端健康地址符合环境
- [ ] 前端生产构建关闭展示模式