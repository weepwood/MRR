# 后端工程

## 技术基线

后端目录为 `backend-repo/`：

- Java 21
- Spring Boot 4.0.5
- Spring MVC、Validation、JDBC 与 Actuator
- MyBatis Spring Boot Starter 4.0.1
- PostgreSQL 与 Flyway
- Springdoc OpenAPI 3.0.2
- Micrometer Prometheus Registry
- Caffeine Cache
- Maven 3.9+

Maven 坐标仍保留历史名称 `com.zjcxph:imgapi`，当前后端版本为 `0.2.0`。坐标和日志文件名不能作为产品边界的唯一依据。

## 分层约定

```text
Controller
  ↓ HTTP 映射、参数校验、权限
Service
  ↓ 业务规则、事务、外部存储协作
Mapper
  ↓ MyBatis SQL 与数据访问
PostgreSQL
```

新增业务应延续现有分层。Controller 不直接拼接复杂 SQL，Mapper 不承担跨领域流程，图片文件访问通过存储边界统一处理。

## 配置体系

默认配置：

```text
src/main/resources/application.properties
```

本地开发：

```text
application-local.template.properties  # 可提交模板
application-local.properties           # 本地私有配置，不提交
```

启动示例：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

生产环境通过环境变量提供数据库密码、JWT、AES、图片路径和 OSS 凭证。

## 服务端口

| 用途 | 默认地址 |
| --- | --- |
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

前端权限只控制菜单和路由，后端 Controller 或拦截器必须独立校验。公开状态接口和登录接口属于明确例外，不能通过宽泛路径匹配放开其它接口。

## 病案主档与关联

当前数据库使用 `mr_archive` 作为病案主档：

- `mr_archive.id` 是稳定的 `BIGINT IDENTITY` 主键。
- `sjh` 允许为空；非空值由部分唯一索引保证唯一。
- `bah` 保留为业务查询字段，不设置全局唯一。
- `mr_statistics`、`mr_scan`、`mr_archive_box_record` 通过 `archive_id` 关联。
- 触发器和解析函数负责兼容历史按 `bah/sjh` 写入的数据。

大表 `mr_scan` 的历史关联不应在 Flyway 启动事务中一次性全量回填。使用仓库脚本分批执行：

```powershell
./backend-repo/scripts/backfill-archive-links.ps1 -BatchSize 10000
```

脚本应支持按最后 ID 继续执行，并在完成后检查空关联和错误关联。

## 病案号与上架号

编号规范化只负责清理输入：

- 去除首尾空格。
- 空白字符串转换为 `NULL`。
- 不再把 1～8 位数字补齐为 8 位。
- `123` 保持为 `123`。
- `00000123` 保持为 `00000123`。

该规则用于避免数据库编号与本地图片文件夹名称不一致。已经被历史逻辑补零的数据不能自动去零，应根据原始数据源修复。

查询规则：

- 病案号小于 `10000000` 时可单独查询。
- 病案号大于或等于 `10000000` 时必须同时提供上架号。
- 非空唯一上架号可单独查询。
- 同时提供病案号和上架号时使用 `AND` 匹配。

## 图片存储边界

图片访问通过 `ImageStorage` 抽象和本地实现统一处理：

- 文件存在性与可读性检查。
- 根目录边界检查。
- 拒绝路径穿越、NUL、分隔符和 Windows 非法字符。
- 区分非法路径、文件不存在和存储读取故障。
- Controller 和导出服务不直接拼接 Windows 路径。

系统设置 `imageSource` 支持：

- `local`：默认，本地或 NAS 图片。
- `oss`：存在 `oss_url` 时生成签名地址，失败时回退本地。

服务端 ZIP 当前仍从本地存储流式读取；不要把大批量 ZIP 无限制回源 OSS。

## 大表查询

兼容接口 `GET /api/v1/scan` 和条件查询在数据库层限制最多返回 1000 条，避免先全表读取再由 Java 截断。

面向 `mr_scan` 大表的顺序遍历使用主键游标：

```http
GET /api/v1/scan/cursor?afterId=0&size=100
```

核心查询：

```sql
SELECT *
FROM mr_scan
WHERE id > :afterId
ORDER BY id
LIMIT :sizePlusOne;
```

新功能不要使用不断增大的 `OFFSET` 扫描数千万行数据。

## 身份证查询与 URL 令牌

1. 前端通过 POST 提交明文身份证号。
2. 后端查询患者关联病案。
3. 返回脱敏值和 URL 安全令牌。
4. 前端使用 `router.replace` 替换地址中的明文。
5. 刷新或前进后退时通过令牌恢复。

令牌使用 AES-GCM 与随机 IV，并验证篡改。响应和日志中不得返回完整身份证号。

## 文档访问会话

后台帮助中心打开文档前，后端签发短期 HttpOnly Cookie：

- 用户手册要求已登录。
- 内部文档和 Swagger 要求 `system:read` 或管理员。
- Nginx 使用 `auth_request` 校验页面和静态资源。
- 后端直连 Springdoc 路径也执行权限检查。

## 日志、数据质量与状态

- 日志不得记录 JWT、密码、OSS 密钥或完整身份证号。
- 数据质量后台调度默认关闭，只允许管理员手动触发。
- Prometheus 标签必须保持低基数，禁止使用病案号、上架号或患者字段。
- 状态服务周期写入 `UP/DOWN` 区间，恢复后根据最后心跳补录停机时间。

## Flyway 规则

当前配置以代码为准：

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

规则：

1. 不删除或手工修改 `app.flyway_schema_history`。
2. 不修改已经在任何环境执行过的迁移文件。
3. 新迁移统一使用 `VyyyyMMddHHmmss__description.sql`。
4. `out-of-order` 只用于补执行尚未应用的低版本迁移，不代表可以重写历史。
5. `missing/future` 忽略用于兼容已归档或尚未合入的迁移记录，部署前仍需核对目标数据库历史。
6. 大表回填与并发索引应使用独立脚本和维护窗口，不放入普通启动事务。

## 验证

```bash
mvn test
mvn package
```

数据库和权限变更还应验证：

- 空数据库可完整执行当前 `db/migration`。
- 现有数据库的 Flyway 历史与待发布迁移一致。
- `archive_id` 关联覆盖率。
- Mapper SQL 在 PostgreSQL 上执行正确。
- 权限允许和拒绝路径。
- 身份证令牌往返、随机 IV 和篡改失败。
- 本地与 OSS 图片来源及回退路径。