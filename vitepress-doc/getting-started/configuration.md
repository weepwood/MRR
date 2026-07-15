# 配置说明

本文以当前 `application.properties`、本地配置模板和前端环境文件为准。不要把历史文档中的 JPA、Redis、SMTP 或界面占位配置当作已实现能力。

## 配置来源

### 后端

| 文件或来源 | 用途 |
| --- | --- |
| `backend-repo/src/main/resources/application.properties` | 默认配置和环境变量映射 |
| `application-local.template.properties` | 本地开发模板 |
| `application-local.properties` | 本地私有配置，不提交 Git |
| 操作系统环境变量 | 生产环境密码、密钥、地址和开关 |

启动本地 profile：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### 前端

| 文件 | 用途 |
| --- | --- |
| `.env.development` | 开发服务器 |
| `.env.production` | 生产构建 |
| `.env.local` | 当前机器覆盖值，不提交敏感信息 |

### VitePress

`MRR_DOCS_MODE=user` 构建用户手册，`MRR_DOCS_MODE=internal` 构建内部文档。正常情况下通过 `vitepress-doc/scripts/run-docs.mjs` 和 package scripts 设置，无需手工修改。

## 后端运行参数

### 服务端口

```properties
server.port=${SERVER_PORT:18045}
management.server.port=${MANAGEMENT_SERVER_PORT:18046}
management.server.address=${MANAGEMENT_SERVER_ADDRESS:127.0.0.1}
```

- `18045`：业务 API、Springdoc 资源。
- `18046`：Actuator、Health、Prometheus，默认只监听本机。

### 数据库

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

生产环境必须设置 `SPRING_DATASOURCE_PASSWORD`。连接池大小应结合 PostgreSQL `max_connections`、应用实例数量和实际并发调整。

### Flyway

```properties
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.schemas=app
spring.flyway.default-schema=app
spring.flyway.baseline-on-migrate=true
spring.flyway.validate-on-migrate=true
```

已经执行的版本化迁移不可直接修改。需要调整表结构或默认值时，新增更高版本迁移。

## 安全密钥

```properties
aes.secret.key=${AES_SECRET_KEY:}
```

- `AES_SECRET_KEY` 用于身份证查询令牌等加密场景，真实环境必须提供强随机密钥。
- JWT 签名密钥应通过部署环境提供，当前项目约定使用 `JWT_SECRET_KEY`，不要写入仓库。
- 密钥变更可能导致现有登录 Token 或身份证查询令牌失效，应安排维护窗口。

PowerShell 示例：

```powershell
$env:SPRING_DATASOURCE_PASSWORD = 'replace-me'
$env:AES_SECRET_KEY = 'replace-with-a-strong-secret-at-least-32-bytes'
$env:JWT_SECRET_KEY = 'replace-with-a-strong-jwt-signing-secret'
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

默认配置中的示例 IP 和 Windows 路径不能直接用于生产。部署时必须全部替换为实际图片目录或图片服务地址。

### 浏览器 PDF 的 CORS

影像档案袋通过浏览器直接读取图片并生成 PDF。图片服务必须返回允许管理端来源的 CORS 头：

```nginx
add_header Access-Control-Allow-Origin "https://mrr.example.com" always;
add_header Access-Control-Allow-Methods "GET, HEAD, OPTIONS" always;
add_header Access-Control-Allow-Headers "Content-Type, Range" always;
```

前端使用 `credentials: omit`，无需开启凭据跨域。

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

`OSS_ACCESS_KEY_ID` 和 `OSS_ACCESS_KEY_SECRET` 必须由环境变量或密钥管理系统提供。迁移完成后还应核对迁移校验状态和数据质量结果。

## Springdoc 与文档访问

```properties
springdoc.api-docs.enabled=${SPRINGDOC_API_DOCS_ENABLED:true}
springdoc.api-docs.path=/v3/api-docs
springdoc.swagger-ui.enabled=${SPRINGDOC_SWAGGER_UI_ENABLED:true}
springdoc.swagger-ui.path=/swagger-ui.html
```

- 后端直连：`/swagger-ui.html`、`/v3/api-docs`。
- 正式同域入口：`/api-docs/`。
- 内部文档和 API 文档需要管理员或 `system:read` 权限。
- 文档访问会话通过短期 HttpOnly Cookie 和 Nginx `auth_request` 校验。

## Actuator 与 Prometheus

```properties
management.endpoints.web.exposure.include=${MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE:health,info,prometheus}
management.endpoint.health.show-details=${MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS:never}
management.prometheus.metrics.export.enabled=${MANAGEMENT_PROMETHEUS_METRICS_EXPORT_ENABLED:true}
management.metrics.tags.application=imgapi
spring.datasource.hikari.metrics.enabled=true
```

不要把管理端口直接暴露到公网。详细原生监控配置见 `monitoring/README.md`。

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

- 前端健康地址为空时，仅判断后端和数据库。
- 配置前端地址时应指向实际部署的 `/healthz.txt`。
- 状态历史按运行区间时长计算可用率。

## 数据质量

```properties
app.data-quality.enabled=false
app.data-quality.cron=-
app.data-quality.sample-limit=${APP_DATA_QUALITY_SAMPLE_LIMIT:200}
app.data-quality.retention-days=${APP_DATA_QUALITY_RETENTION_DAYS:90}
```

当前数据质量检查为手动模式。不要通过设置 Cron 启用后台自动执行；管理员应在系统监控页面点击“立即检查”。

## 访问日志保留

```properties
app.log-retention.enabled=${APP_LOG_RETENTION_ENABLED:true}
app.log-retention.cron=${APP_LOG_RETENTION_CRON:0 30 2 * * ?}
app.log-retention.retention-days=${APP_LOG_RETENTION_RETENTION_DAYS:1095}
app.log-retention.batch-size=${APP_LOG_RETENTION_BATCH_SIZE:5000}
app.log-retention.max-batches-per-run=${APP_LOG_RETENTION_MAX_BATCHES_PER_RUN:20}
```

清理任务按批次删除历史访问日志。调整保留期前应确认审计、合规和存储要求。

## 日志

```properties
logging.level.com.zjcxph.imgapi.mapper=${MAPPER_LOG_LEVEL:WARN}
logging.level.root=INFO
logging.level.com.zjcxph.imgapi=INFO
logging.file.name=${LOG_FILE_NAME:img-api.log}
logging.logback.rollingpolicy.max-file-size=10MB
logging.logback.rollingpolicy.max-history=30
```

生产环境不建议开启 Mapper DEBUG，以免产生大量日志或记录敏感查询参数。

## 前端环境变量

开发文件当前关键项：

```env
VITE_APP_SETTING = true
VITE_APP_TITLE = MRR-ADMIN
VITE_APP_API_BASEURL = http://localhost:18045
VITE_APP_DEMO_MODE = true
VITE_OPEN_PROXY = true
VITE_OPEN_DEVTOOLS = false
VITE_APP_DISABLE_DEVTOOL = false
```

| 变量 | 说明 |
| --- | --- |
| `VITE_APP_API_BASEURL` | Axios 业务 API 基础地址 |
| `VITE_APP_DEMO_MODE` | 跳过认证接口，仅用于前端展示 |
| `VITE_OPEN_PROXY` | 是否启用开发服务器代理 |
| `VITE_OPEN_DEVTOOLS` | 是否打开 Vue 开发工具 |
| `VITE_APP_DISABLE_DEVTOOL` | 是否启用防调试功能 |

生产构建必须关闭展示模式，并使用实际同域 API 地址或 Nginx 代理路径。

## 运行时系统设置

管理端“系统设置”保存到服务端 `mr_system_settings`，服务端不可用时回退浏览器本地配置。当前已接入的设置包括：

- 系统名称
- 档案袋影像栏模式
- 档案袋预览模式
- 缩略图宽度
- 首批渲染数量
- 自动适应预览区域
- 记住选中图片
- 水印开关与透明度
- 页面标题风格
- 全局圆角

数据库、密钥、OSS 凭据、备份和 SMTP 不属于运行时设置页面。

## 生产检查清单

- [ ] 所有默认账号密码已替换
- [ ] `SPRING_DATASOURCE_PASSWORD`、`AES_SECRET_KEY`、`JWT_SECRET_KEY` 已设置
- [ ] Actuator 仅本机或监控网络可访问
- [ ] Springdoc 仅通过受保护同域入口访问
- [ ] 图片路径、图片服务地址和 CORS 正确
- [ ] OSS 凭据未提交仓库
- [ ] 数据质量保持手动触发
- [ ] 日志保留期符合审计要求
- [ ] `/status` 时区和前端健康地址符合部署环境
- [ ] 前端生产构建关闭展示模式