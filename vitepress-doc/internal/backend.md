# 后端工程

## 技术基线

后端目录为 `backend-repo/`：

- Java 21
- Spring Boot 4.0.5
- Spring MVC、Validation、JDBC 与 Actuator
- MyBatis Spring Boot Starter 4.0.1
- PostgreSQL
- Flyway
- Springdoc OpenAPI 3.0.2
- Micrometer Prometheus Registry
- Caffeine Cache
- Maven 3.9+

Maven 坐标仍保留历史名称 `com.zjcxph:imgapi`，不能据此判断产品定位。

## 分层约定

```text
Controller
  ↓ 参数校验、权限、HTTP 映射
Service
  ↓ 业务规则、事务、外部存储协作
Mapper
  ↓ MyBatis SQL 与数据访问
PostgreSQL
```

新增业务应延续现有分层，不在 Controller 中直接拼接复杂 SQL，也不在 Mapper 中实现跨领域业务流程。

## 配置体系

默认配置：

```text
src/main/resources/application.properties
```

本地开发：

```text
application-local.template.properties  # 提交仓库
application-local.properties           # 本地私有，不提交
```

启动：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

生产环境通过环境变量覆盖数据库密码、JWT、AES、图片路径和 OSS 凭证。

## 服务端口

| 用途 | 默认地址 |
|------|----------|
| 业务 API | `0.0.0.0:18045` |
| Actuator | `127.0.0.1:18046` |

Actuator 与业务接口分离，默认只暴露 `health`、`info` 和 `prometheus`。

## 认证与权限

业务接口使用 JWT 与 RBAC。常见权限：

- `record:read`
- `record:manage`
- `statistics:read`
- `log:read`
- `user:manage`
- `role:read`
- `system:read`

Controller 或拦截器必须进行服务端校验。公开状态接口和登录接口属于明确例外，不应通过宽泛路径匹配放开其他接口。

## 病案标识规则

`bah` 和 `sjh` 在查询前规范为八位数字：

- 小于 `10000000` 的病案号可单独定位。
- 大于或等于 `10000000` 的病案号必须与上架号成对查询。
- 上架号可单独查询。
- 同时提供病案号和上架号时必须使用 `AND` 匹配。

旧单字段接口也必须遵守高位病案号限制。

## 身份证查询与 URL 令牌

1. 前端通过 POST 提交明文身份证号。
2. 后端查询患者全部病案。
3. 返回脱敏展示值和 URL 安全令牌。
4. 前端使用 `router.replace` 替换地址中的明文。
5. 刷新或前进后退时，通过令牌恢复查询。

令牌使用 AES-GCM 与随机 IV，并验证篡改。响应和日志中不返回完整身份证号。

## 文档访问会话

后台帮助中心打开文档前，后端签发短期文档访问 Cookie：

- HttpOnly。
- 默认有效期约 30 分钟。
- 用户手册要求登录。
- 内部文档和 Swagger 要求 `system:read` 或管理员。
- Nginx 使用 `auth_request` 校验页面与静态资源。
- 后端直连 Springdoc 路径也执行校验。

## 日志与审计

后端记录访问日志、用户、IP、URI、方法、状态、执行时间、审计动作与图片访问记录。不得在日志中记录 JWT、密码、OSS 密钥或完整身份证号。

## 数据质量

数据质量检查默认关闭后台调度：

```properties
app.data-quality.enabled=false
app.data-quality.cron=-
```

只能由管理员页面或受保护接口手动触发。Prometheus 指标只使用低基数标签，不能把病案号、上架号或患者字段作为标签。

## 可用性历史

后端周期更新状态心跳，状态改变时创建新的 `UP` 或 `DOWN` 区间。启动时根据最后心跳和超时阈值补录停机时间。

```properties
app.status.enabled=true
app.status.check-interval-ms=60000
app.status.heartbeat-timeout-ms=120000
app.status.frontend-health-url=
app.status.retention-days=365
app.status.zone-id=Asia/Shanghai
```

前端探测地址为空时，只判断后端与数据库。

## Flyway 规则

当前迁移链：

```text
src/main/resources/db/migration/V0__baseline_schema.sql
```

旧增量迁移已移动到：

```text
src/main/resources/db/migration-legacy/
```

规则：

- 新数据库只从 V0 基线初始化。
- `baseline-on-migrate=false`，不为已有库自动写入基线记录。
- 旧迁移目录不进入当前 `spring.flyway.locations`。
- 已部署旧迁移链的数据库不能直接切换 V0。
- V0 部署后需要结构变更时，新增 V1、V2 等增量迁移，不直接修改已部署基线。
- 应用账号不承担安装 PostgreSQL 扩展等管理员操作，部署前需确认扩展权限。

## 后端验证

```bash
mvn test
mvn package
```

数据库和权限变更还应验证：

- 新数据库可从 V0 完整初始化。
- 已部署数据库使用明确迁移方案升级。
- Mapper SQL 与 PostgreSQL 语法一致。
- 权限允许与拒绝路径。
- 身份证令牌往返、随机 IV 和篡改失败。