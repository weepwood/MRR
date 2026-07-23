# 配置参考

本文以 `backend-repo/src/main/resources/application.properties` 为事实来源，整理正式部署常用配置。生产环境应通过 Windows 环境变量、服务包装器参数或外部配置文件覆盖默认值，不要修改打包后的 JAR。

## 配置优先级

Spring Boot 常见优先级从高到低为：

1. 命令行参数；
2. Java 系统属性；
3. 操作系统环境变量；
4. 外部 `application-*.properties`；
5. JAR 内置 `application.properties`。

同一参数不要同时在多个位置维护，避免排障时无法确定实际值。

## 必须设置的敏感配置

| 环境变量 | 用途 | 建议 |
| --- | --- | --- |
| `SPRING_DATASOURCE_PASSWORD` | PostgreSQL 密码 | 使用专用数据库账号，禁止默认空密码 |
| `JWT_SECRET_KEY` | JWT 签名 | 使用高强度随机值，轮换会使旧 Token 失效 |
| `AES_SECRET_KEY` | AES-GCM 加密 | 使用满足实现要求的随机密钥 |
| 外部调阅 HMAC 密钥 | HIS/EMR Ticket 签名 | 每个接入方独立管理，禁止写入前端 |
| `OSS_ACCESS_KEY_ID` | OSS AccessKey ID | 使用最小权限账号 |
| `OSS_ACCESS_KEY_SECRET` | OSS AccessKey Secret | 不写入仓库、日志或页面设置 |

实际 HMAC 配置名称以外部调阅模块和当前配置类为准；升级前应核对运行环境已有配置。

## 服务端口

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `SERVER_PORT` | `18045` | 业务 API |
| `MANAGEMENT_SERVER_PORT` | `18046` | Actuator 管理端口 |
| `MANAGEMENT_SERVER_ADDRESS` | `127.0.0.1` | 默认仅本机监听 |

不要把 `18046` 直接暴露给普通内网用户或外网。Nginx 只代理业务所需的受保护入口。

## PostgreSQL

| 配置 | 默认/示例 | 说明 |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/imgapi?currentSchema=app` | 数据库地址和业务 Schema |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | 建议改为专用账号 |
| `SPRING_DATASOURCE_PASSWORD` | 无默认值 | 必填 |
| `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` | `20` | 最大连接数 |
| `SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE` | `5` | 最小空闲连接 |
| `SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT` | `30000` | 获取连接超时，毫秒 |
| `SPRING_DATASOURCE_HIKARI_IDLE_TIMEOUT` | `300000` | 空闲回收，毫秒 |
| `SPRING_DATASOURCE_HIKARI_MAX_LIFETIME` | `1800000` | 连接最长生命周期，毫秒 |

连接池大小应结合 PostgreSQL `max_connections`、应用实例数和慢查询情况配置。不要仅为解决超时无限增大连接池。

## Flyway

当前迁移目录：

```text
backend-repo/src/main/resources/db/migration
backend-repo/src/main/resources/db/callback
```

关键配置：

| 配置 | 当前值 | 说明 |
| --- | --- | --- |
| `spring.flyway.enabled` | `true` | 启动时执行迁移 |
| `spring.flyway.schemas` | `app` | 业务 Schema |
| `spring.flyway.out-of-order` | `true` | 允许后补日期迁移 |
| `spring.flyway.validate-on-migrate` | `true` | 启动前校验 |
| `spring.flyway.validate-migration-naming` | `true` | 校验文件命名 |

迁移统一采用：

```text
VyyyyMMddHHmmss__description.sql
```

已执行迁移禁止修改内容或重命名。数千万行更新不应放入普通 Flyway 启动事务，应使用可恢复批处理脚本。

## 图片基础配置

| 环境变量 | 说明 |
| --- | --- |
| `IMAGE_URL` | 兼容图片基础地址 |
| `IMAGE_USERNAME` / `IMAGE_PASSWORD` | 受保护图片服务凭据 |
| `IMAGE_BASE_PATH` | 后端可直接读取的本地/NAS 根目录 |
| `IMAGE_SERVER_URL_DEFAULT` | 默认 Nginx 图片节点 |
| `IMAGE_SERVER_URL_BA01` | BA01 节点 |
| `IMAGE_SERVER_URL_BA02` | BA02 节点 |
| `IMAGE_SERVER_URL_BA03` | BA03 节点 |

Nginx URL 应指向受控只读图片根路径，建议以 `/` 结尾。不要配置可访问系统任意目录的 autoindex。

## 多来源图片解析

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `ARCHIVE_IMAGE_SOURCE_PREFER_OSS` | `true` | 是否优先 OSS |
| `ARCHIVE_IMAGE_SOURCE_ACQUIRE_TIMEOUT` | `PT30S` | 单张图片获取总超时 |
| `ARCHIVE_IMAGE_SOURCE_LOCAL_MAX_CONCURRENCY` | `16` | 本地读取并发 |
| `ARCHIVE_IMAGE_SOURCE_NGINX_MAX_CONCURRENCY` | `8` | Nginx 读取并发 |
| `ARCHIVE_IMAGE_SOURCE_NGINX_CONNECT_TIMEOUT` | `PT5S` | Nginx 连接超时 |
| `ARCHIVE_IMAGE_SOURCE_NGINX_READ_TIMEOUT` | `PT60S` | Nginx 读取超时 |
| `ARCHIVE_IMAGE_SOURCE_NAS_MAX_CONCURRENCY` | `8` | NAS 读取并发 |
| `ARCHIVE_IMAGE_SOURCE_HTTP_MAX_CONCURRENCY` | `8` | HTTP 读取并发 |
| `ARCHIVE_IMAGE_SOURCE_OSS_MAX_CONCURRENCY` | `8` | OSS 读取并发 |

NAS/HTTP 节点使用 Map 风格环境变量，例如：

```text
ARCHIVE_IMAGE_SOURCE_NAS_NODES_ARCHIVE01_ROOT=\\archive01\medical-images
ARCHIVE_IMAGE_SOURCE_HTTP_NODES_BA01_BASE_URL=http://10.0.0.11/images/
```

节点名称应稳定，不要在不同环境中用同一名称指向不同业务目录。

## ZIP/PDF 导出

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `ARCHIVE_EXPORT_TEMP_DIRECTORY` | 系统临时目录下 `mrr-archive-exports` | 导出临时文件目录 |
| `ARCHIVE_EXPORT_MAX_TOTAL_BYTES` | `21474836480` | 临时文件总配额，20 GiB |
| `ARCHIVE_EXPORT_MAX_FILE_BYTES` | `10737418240` | 单文件上限，10 GiB |
| `ARCHIVE_EXPORT_RETENTION` | `PT24H` | 成功文件保留时间 |
| `ARCHIVE_EXPORT_CLEANUP_INTERVAL` | `PT15M` | 清理间隔 |
| `ARCHIVE_EXPORT_WORKER_CORE_SIZE` | `1` | 核心线程数 |
| `ARCHIVE_EXPORT_WORKER_MAX_SIZE` | `2` | 最大线程数 |
| `ARCHIVE_EXPORT_WORKER_QUEUE_CAPACITY` | `20` | 排队容量 |
| `ARCHIVE_EXPORT_ASYNC_ITEM_THRESHOLD` | `500` | 图片数异步阈值 |
| `ARCHIVE_EXPORT_ASYNC_ESTIMATED_BYTES_THRESHOLD` | `1073741824` | 预估 1 GiB 异步阈值 |
| `ARCHIVE_EXPORT_ASYNC_SOURCE_COUNT_THRESHOLD` | `2` | 多来源异步阈值 |
| `ARCHIVE_EXPORT_FALLBACK_BYTES_PER_IMAGE` | `5242880` | 无大小信息时每图估算 5 MiB |

生产建议：

- 将临时目录放在独立磁盘；
- 监控剩余空间、任务队列和清理失败；
- 不要把临时目录放在 Nginx 可直接目录浏览的位置；
- 杀毒软件实时扫描可能显著拖慢大 ZIP/PDF；
- 配额应小于磁盘可用空间，并预留日志、数据库和发布空间。

## OSS

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `OSS_ENDPOINT` | `oss-cn-hangzhou.aliyuncs.com` | S3 兼容 endpoint |
| `OSS_BUCKET` | `mrr-medical-records` | Bucket |
| `OSS_ACCESS_KEY_ID` | 无 | 必填（使用 OSS 时） |
| `OSS_ACCESS_KEY_SECRET` | 无 | 必填（使用 OSS 时） |
| `OSS_REGION` | `cn-hangzhou` | Region |
| `OSS_BASE_URL` | 默认示例地址 | 对象公开/签名基础地址 |
| `OSS_URL_EXPIRE_SECONDS` | `3600` | 签名 URL 有效期 |

AccessKey 只授予目标 Bucket 和必要操作。文件浏览、迁移和业务读取可能需要不同权限，应优先拆分账号。

## 状态与监控

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `APP_STATUS_ENABLED` | `true` | 内置状态历史 |
| `APP_STATUS_CHECK_INTERVAL_MS` | `60000` | 检查间隔 |
| `APP_STATUS_HEARTBEAT_TIMEOUT_MS` | `120000` | 心跳超时 |
| `APP_STATUS_REQUEST_TIMEOUT_MS` | `3000` | 请求超时 |
| `APP_STATUS_FRONTEND_HEALTH_URL` | 空 | 可选前端检查地址 |
| `APP_STATUS_RETENTION_DAYS` | `365` | 历史保留 |
| `APP_STATUS_ZONE_ID` | `Asia/Shanghai` | 状态统计时区 |

Actuator 默认暴露：

```text
health,info,prometheus
```

`/status` 与主后端同进程，不能替代独立的外部监控。

## 日志

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `LOG_FILE_NAME` | `img-api.log` | 主日志文件 |
| `MAPPER_LOG_LEVEL` | `WARN` | MyBatis Mapper 日志 |
| `APP_LOG_RETENTION_ENABLED` | `true` | 访问日志清理 |
| `APP_LOG_RETENTION_RETENTION_DAYS` | `1095` | 保留 3 年 |
| `APP_LOG_RETENTION_BATCH_SIZE` | `5000` | 单批删除数量 |
| `APP_LOG_RETENTION_MAX_BATCHES_PER_RUN` | `20` | 每次最大批数 |

生产环境不要长期启用 SQL DEBUG。日志不得记录完整身份证、密码、Token、AccessKey、HMAC 密钥和未脱敏患者信息。

## 缓存与通用异步线程池

当前 Caffeine 默认：

```text
initialCapacity=100,maximumSize=1000,expireAfterWrite=10m
```

通用异步线程池默认：

```text
core-size=5
max-size=10
queue-capacity=100
```

影像导出使用独立线程池，不应只调整通用线程池来解决导出排队。

## 配置变更流程

1. 记录当前版本和配置快照；
2. 在测试环境修改；
3. 校验路径、端口、网络和权限；
4. 重启后检查 `/actuator/health` 和 `/actuator/info`；
5. 抽样测试登录、查询、图片、ZIP、PDF、OSS/Nginx 浏览；
6. 观察日志和磁盘；
7. 更新内部文档和部署记录。

## 禁止事项

- 不要把密钥提交到 Git；
- 不要把生产密码写入用户可见的系统设置；
- 不要让 Actuator 监听所有网卡；
- 不要用 `IMAGE_BASE_PATH` 指向系统盘根目录；
- 不要开放任意 Nginx URL 或服务器文件路径；
- 不要为解决性能问题无上限增加线程和连接池；
- 不要修改已经执行的 Flyway 文件。