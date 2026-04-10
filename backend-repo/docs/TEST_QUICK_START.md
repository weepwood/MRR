# 集成测试快速开始指南

## 📋 前置条件

在运行集成测试之前，请确保：

1. ✅ Java 21 已安装并配置
2. ✅ Maven 3.6+ 已安装
3. ✅ PostgreSQL 数据库正在运行
4. ✅ 数据库 schema 已初始化
5. ✅ 测试数据已准备（至少有一个测试用户）

## 🚀 快速开始

### 1. 运行所有集成测试

```bash
mvn verify
```

### 2. 运行特定测试类

```bash
# 用户认证测试
mvn test -Dtest=UserAuthIntegrationTest

# 扫描记录管理测试
mvn test -Dtest=ScanManagementIntegrationTest

# 图片操作测试
mvn test -Dtest=ImageOperationsIntegrationTest

# 日志查询测试
mvn test -Dtest=LogQueryIntegrationTest

# 搜索功能测试
mvn test -Dtest=SearchIntegrationTest

# 数据库操作测试
mvn test -Dtest=DatabaseOperationsIntegrationTest
```

### 3. 运行单个测试方法

```bash
mvn test -Dtest=UserAuthIntegrationTest#testLoginSuccess
```

### 4. 生成测试报告

```bash
mvn verify
# 报告位置: target/site/jacoco/index.html
```

## 📊 测试覆盖范围

| 测试类 | 测试数量 | 覆盖功能 |
|--------|---------|---------|
| UserAuthIntegrationTest | 12 | 登录、权限、用户管理 |
| ScanManagementIntegrationTest | 13 | CRUD、查询、分页 |
| ImageOperationsIntegrationTest | 16 | 图片下载、类型修改、OSS |
| LogQueryIntegrationTest | 16 | 日志查询、过滤、导出 |
| SearchIntegrationTest | 17 | 患者搜索、多条件查询 |
| DatabaseOperationsIntegrationTest | 16 | 事务、并发、性能 |
| **总计** | **90+** | **全面覆盖** |

## 🔧 常见问题解决

### 问题 1: 数据库连接失败

**错误信息**: `Connection refused`

**解决方案**:
```bash
# 检查 PostgreSQL 是否运行
pg_isready

# 启动 PostgreSQL (Windows)
net start postgresql-x64-15

# 启动 PostgreSQL (Linux/Mac)
sudo systemctl start postgresql
```

### 问题 2: 测试用户不存在

**错误信息**: `Invalid username or password`

**解决方案**:
```sql
-- 连接到数据库
psql -U postgres -d imgapi

-- 创建测试用户
INSERT INTO mr_auth_user (username, display_name, password_hash, role_code, status)
VALUES ('admin', 'Admin User', '$2a$10$...', 'ADMIN', 'ACTIVE');
```

### 问题 3: 图片路径不存在

**错误信息**: `文件不存在`

**解决方案**:
```properties
# 在 application-local.properties 中配置正确的路径
image.basePath=D:/test-images
```

### 问题 4: 端口被占用

**错误信息**: `Port already in use`

**解决方案**:
```bash
# 测试使用随机端口，通常不会出现此问题
# 如果确实遇到，检查是否有残留进程
netstat -ano | findstr :18045
taskkill /PID <pid> /F
```

## 📝 测试数据准备

### 最小测试数据集

```sql
-- 1. 创建测试角色
INSERT INTO mr_auth_role (code, name, description, permissions, sort_order)
VALUES 
('ADMIN', '管理员', '系统管理员', 'user:manage,role:read,role:manage', 1),
('USER', '普通用户', '普通用户', 'scan:read,image:read', 2);

-- 2. 创建测试用户 (密码: admin123)
INSERT INTO mr_auth_user (username, display_name, password_hash, role_code, status)
VALUES 
('admin', 'Admin User', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ADMIN', 'ACTIVE');

-- 3. 创建测试病案数据
INSERT INTO mr_scan (brxh, bah, filename, btype, pages, uploadflag, folder)
VALUES 
('605746', '00789508', 'test_image_001.jpg', 1, 1, 1, '24.04.30');

-- 4. 创建测试患者数据
INSERT INTO mr_patient (idcard, bah, admissiontime, department, name)
VALUES 
('110101199001011234', '00789508', '2024-01-01', '内科', '张三');
```

## 🎯 测试最佳实践

### 1. 本地开发环境

创建 `src/test/resources/application-test.properties`:

```properties
# 使用独立的测试数据库
spring.datasource.url=jdbc:postgresql://localhost:5432/imgapi_test
spring.datasource.username=postgres
spring.datasource.password=test_password

# 测试图片路径
image.basePath=./test-data/images

# 禁用不必要的功能
app.log-retention.enabled=false
```

### 2. CI/CD 环境

使用 TestContainers 进行隔离测试：

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>
```

### 3. 测试数据清理

在每个测试类中添加清理逻辑：

```java
@AfterEach
void cleanup() {
    // 清理测试数据
    jdbcTemplate.execute("DELETE FROM mr_scan WHERE brxh LIKE '999%'");
}
```

## 📈 性能基准

### 预期性能指标

| 测试类型 | 预期响应时间 | 说明 |
|---------|------------|------|
| 简单查询 | < 100ms | 单条记录查询 |
| 列表查询 | < 500ms | 分页查询 100 条 |
| 复杂查询 | < 2000ms | 多条件组合查询 |
| 文件下载 | < 5000ms | 取决于文件大小 |
| 并发测试 | < 10000ms | 10 个并发请求 |

### 性能优化建议

1. **数据库索引**: 确保常用查询字段有索引
2. **连接池**: 适当配置 HikariCP 参数
3. **缓存**: 对频繁访问的数据使用缓存
4. **分页**: 避免一次性加载大量数据

## 🔍 调试技巧

### 1. 启用详细日志

```properties
# application-test.properties
logging.level.com.zjcxph.imgapi=DEBUG
logging.level.org.springframework.web=DEBUG
logging.level.org.mybatis=DEBUG
```

### 2. 查看 SQL 语句

```properties
mybatis.configuration.log-impl=org.apache.ibatis.logging.stdout.StdOutImpl
```

### 3. 使用断点调试

```java
@Test
public void testDebug() {
    // 设置断点在此处
    ResponseEntity<Map> response = restTemplate.getForEntity(...);
    System.out.println(response.getBody()); // 查看响应
}
```

## 📚 相关文档

- [完整集成测试指南](./INTEGRATION_TEST_GUIDE.md)
- [API 文档](http://localhost:18045/swagger-ui.html)
- [数据库 Schema](../src/main/resources/schema-postgresql.sql)

## 💡 提示

1. **定期运行测试**: 每次代码变更后运行测试
2. **保持测试独立**: 避免测试之间的依赖
3. **及时更新测试**: API 变更时同步更新测试
4. **关注测试覆盖率**: 目标覆盖率 > 80%
5. **使用有意义的断言**: 提供清晰的失败消息

## 🆘 获取帮助

如果遇到问题：

1. 查看测试日志: `target/surefire-reports/`
2. 检查应用日志: `img-api.log`
3. 查阅文档: `docs/INTEGRATION_TEST_GUIDE.md`
4. 联系开发团队

---

**祝测试顺利！** 🎉
