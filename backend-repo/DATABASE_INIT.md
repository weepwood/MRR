# 数据库初始化指南

## 重要说明

从 v2.0 开始,数据库 schema 初始化已改为**手动执行**,以避免 Spring Boot 自动初始化时的兼容性问题。

## 初始化步骤

### 1. 首次启动前 - 基础 Schema 初始化

```bash
# 方法 1: 使用 psql 命令行
psql -U postgres -d imgapi -f backend-repo/src/main/resources/schema-postgresql.sql

# 方法 2: 使用 Docker
docker exec -i postgres-container psql -U postgres -d imgapi < backend-repo/src/main/resources/schema-postgresql.sql
```

### 2. OSS 迁移功能初始化 (可选)

如果需要启用图片迁移到 OSS 功能,执行迁移脚本:

```bash
# 方法 1: 使用 psql 命令行
psql -U postgres -d imgapi -f mrr-db/migration_to_oss.sql

# 方法 2: 使用 Docker
docker exec -i postgres-container psql -U postgres -d imgapi < mrr-db/migration_to_oss.sql

# 初始化迁移日志
psql -U postgres -d imgapi -c "SELECT app.init_migration_logs();"
```

### 3. 验证表结构

```sql
-- 检查核心表是否存在
SELECT table_name 
FROM information_schema.tables 
WHERE table_schema = 'app' 
ORDER BY table_name;

-- 应该看到以下表:
-- mr_scan
-- mr_statistics
-- mr_patient
-- mr_user
-- mr_auth_role
-- mr_auth_user
-- access_log
-- image_migration_log (如果执行了迁移脚本)
```

### 4. 启动应用

```bash
cd backend-repo
mvn spring-boot:run
```

## 常见问题

### Q1: 启动时报错 "relation does not exist"

**原因**: 数据库表未创建

**解决**: 执行步骤 1 的基础 Schema 初始化

### Q2: 启动时报错 "column does not exist: oss_url"

**原因**: 缺少 OSS 迁移相关字段

**解决**: 执行步骤 2 的 OSS 迁移脚本

### Q3: 想重新初始化数据库

```sql
-- 警告: 这将删除所有数据!
DROP SCHEMA app CASCADE;
CREATE SCHEMA app;

-- 然后重新执行步骤 1 和 2
```

## 配置说明

在 `application.properties` 中:

```properties
# 禁用自动 SQL 初始化 (已设置为 never)
spring.sql.init.mode=never

# 如需恢复自动初始化,取消下面注释
# spring.sql.init.mode=always
# spring.sql.init.platform=postgresql
# spring.sql.init.schema-locations=classpath:schema-postgresql.sql
```

## 版本历史

- **v1.x**: 使用 Spring Boot 自动初始化 (`spring.sql.init.mode=always`)
- **v2.0+**: 改为手动初始化,提高稳定性和可控性
