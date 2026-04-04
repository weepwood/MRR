# MRR Backend

后端服务基于 Spring Boot 4 + MyBatis + PostgreSQL，提供登录认证、权限管理、病案/影像查询、统计、日志和系统监控接口。

## 环境要求

- JDK 21 或更高版本
- Maven 3.9+
- PostgreSQL 15+

## 本地启动

1. 创建数据库并确保可访问。
2. 配置环境变量，或在本地补充 `src/main/resources/application-local.properties`。
3. 执行构建并启动：

```bash
mvn -DskipTests package
```

也可以直接运行 Spring Boot 主类 `com.zjcxph.imgapi.ImageApiApplication`。

## 关键配置

默认配置位于 [`src/main/resources/application.properties`](src/main/resources/application.properties)：

- `SERVER_PORT` 默认 `18045`
- `SPRING_DATASOURCE_URL` 默认 `jdbc:postgresql://localhost:5432/imgapi?currentSchema=app`
- `SPRING_DATASOURCE_USERNAME` 默认 `postgres`
- `SPRING_DATASOURCE_PASSWORD` 默认 `weepwood`
- `IMAGE_URL` 默认 `http://localhost:8005/ba-img`
- `IMAGE_BASE_PATH` 默认 `./data/img`
- `IMAGE_USERNAME` 默认 `br_admin`
- `IMAGE_PASSWORD` 默认 `br_password`
- `AES_SECRET_KEY` 默认 `change-this-in-production-32-bytes`

## 数据初始化

启动时会自动执行 PostgreSQL 初始化脚本：

- [`src/main/resources/schema-postgresql.sql`](src/main/resources/schema-postgresql.sql)

脚本会创建 `app` schema、业务表、权限表和默认种子数据。`br_admin` 的默认密码为 `br_password`，可通过以下脚本重置：

- [`src/main/resources/reset-br-admin-password.sql`](src/main/resources/reset-br-admin-password.sql)

## 主要入口

- Swagger UI: `http://localhost:18045/v1/swagger-ui/index.html`
- Health Check: `http://localhost:18045/actuator/health`

## 相关文档

- [项目说明文档](../frontend-repo/docs/guide/项目说明文档.md)
- [系统架构说明](../SYSTEM_ARCHITECTURE.md)
- [质量门禁](../QUALITY_GATE.md)
