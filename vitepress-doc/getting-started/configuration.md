# 配置说明

> 本文档详细介绍 MRR 系统的配置参数

## 配置文件位置

### 后端配置

- **主配置**: `backend-repo/src/main/resources/application.properties`
- **环境配置**: `application-{profile}.properties`
  - `application-local.properties` - 本地开发环境
  - `application-prod.properties` - 生产环境

### 前端配置

- **环境变量**: `frontend-fantastic-admin/.env`
- **本地配置**: `.env.local`
- **开发配置**: `.env.development`
- **生产配置**: `.env.production`

## 后端配置参数

### 数据库配置

```properties
# 数据源配置
spring.datasource.url=jdbc:postgresql://localhost:5432/imgapi?currentSchema=app
spring.datasource.username=postgres
spring.datasource.password=your_password
spring.datasource.driver-class-name=org.postgresql.Driver

# 连接池配置
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000
```

### 服务器配置

```properties
# 服务端口
server.port=18045

# 上下文路径
server.servlet.context-path=/

# SSL 配置 (HTTPS)
server.ssl.enabled=false
server.ssl.key-store=classpath:keystore.p12
server.ssl.key-store-password=changeit
server.ssl.key-store-type=PKCS12
```

### JWT 认证配置

```properties
# JWT 密钥 (生产环境必须修改!)
jwt.secret=your-jwt-secret-key-at-least-256-bits

# Token 有效期 (毫秒)
jwt.expiration=86400000  # 24小时
jwt.refresh-expiration=604800000  # 7天
```

### AES 加密配置

```properties
# AES 加密密钥 (必须是 32 字节)
aes.secret.key=change-this-in-production-32-bytes
```

### 影像存储配置

```properties
# 本地存储路径
image.base-path=./data/img

# 影像服务 URL
image.url=http://localhost:8005/ba-img

# 影像服务认证
image.username=br_admin
image.password=br_password
```

### OSS 对象存储配置 (可选)

```properties
# OSS 配置
oss.enabled=false
oss.endpoint=https://oss-cn-hangzhou.aliyuncs.com
oss.access-key-id=your-access-key-id
oss.access-key-secret=your-access-key-secret
oss.bucket-name=mrr-images
```

### 日志配置

```properties
# 日志级别
logging.level.root=INFO
logging.level.com.zjcxph.imgapi=DEBUG

# 日志文件
logging.file.name=logs/mrr-backend.log
logging.file.max-size=10MB
logging.file.max-history=30

# 日志格式
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n
logging.pattern.file=%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n
```

### 日志保留策略

```properties
# 日志保留天数
log.retention.days=90

# 自动清理开关
log.retention.enabled=true
```

### Swagger API 文档

```properties
# Swagger 配置
springdoc.api-docs.enabled=true
springdoc.api-docs.path=/v1/api-docs
springdoc.swagger-ui.enabled=true
springdoc.swagger-ui.path=/v1/swagger-ui.html
```

## 前端配置参数

### 环境变量

```env
# 应用标题
VITE_APP_TITLE=MRR 医疗影像管理系统

# API 基础地址
VITE_API_URL=http://localhost:18045

# 上传文件大小限制 (MB)
VITE_MAX_FILE_SIZE=100

# Token 存储方式 (localStorage | sessionStorage)
VITE_TOKEN_STORAGE=localStorage

# 是否启用 Mock 数据
VITE_USE_MOCK=false
```

### 构建配置

`vite.config.ts` 关键配置:

```typescript
export default defineConfig({
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:18045',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      }
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    minify: 'terser',
    chunkSizeWarningLimit: 1500
  }
})
```

## 安全配置

### 密码策略

系统默认密码策略:
- 最小长度: 8 字符
- 必须包含: 大写字母、小写字母、数字、特殊字符

修改密码策略 (后端):

```java
// 在 PasswordUtil 类中修改正则表达式
private static final String PASSWORD_PATTERN = 
    "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=])(?=\\S+$).{8,}$";
```

### CORS 配置

后端 CORS 配置 (WebConfig.java):

```java
@Override
public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**")
        .allowedOrigins("http://localhost:5173", "https://your-domain.com")
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .allowCredentials(true)
        .maxAge(3600);
}
```

### 权限控制

系统使用基于角色的访问控制 (RBAC):

- **超级管理员**: 所有权限
- **管理员**: 用户管理、数据查看
- **操作员**: 数据录入、查看
- **查看者**: 仅查看权限

## 性能优化配置

### 数据库优化

```properties
# JPA/Hibernate 配置
spring.jpa.show-sql=false
spring.jpa.hibernate.ddl-auto=none
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect

# 批量操作
spring.jpa.properties.hibernate.jdbc.batch_size=50
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
```

### 缓存配置

```properties
# 启用缓存
spring.cache.type=redis

# Redis 配置 (可选)
spring.redis.host=localhost
spring.redis.port=6379
spring.redis.password=
spring.redis.database=0
```

### 文件上传限制

```properties
# 文件上传配置
spring.servlet.multipart.enabled=true
spring.servlet.multipart.max-file-size=100MB
spring.servlet.multipart.max-request-size=100MB
spring.servlet.multipart.file-size-threshold=2KB
```

## 监控配置

### Actuator 端点

```properties
# 启用 Actuator
management.endpoints.enabled-by-default=true
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoint.health.show-details=always
```

### Prometheus 监控

```properties
# Prometheus 配置
management.prometheus.metrics.export.enabled=true
management.metrics.tags.application=mrr-backend
```

## 配置最佳实践

### 生产环境清单

- [ ] 修改所有默认密码
- [ ] 更换 JWT 密钥
- [ ] 更换 AES 加密密钥
- [ ] 配置 HTTPS
- [ ] 配置数据库连接池
- [ ] 配置日志级别为 INFO 或 WARN
- [ ] 禁用 Swagger UI (可选)
- [ ] 配置 CORS 白名单
- [ ] 配置监控告警

### 敏感信息管理

::: danger 重要
不要将敏感信息提交到版本控制!
:::

1. 使用环境变量
2. 使用配置中心 (如 Spring Cloud Config)
3. 使用密钥管理服务 (如 Vault)

示例:

```properties
# 使用环境变量
spring.datasource.password=${DB_PASSWORD}
jwt.secret=${JWT_SECRET}
aes.secret.key=${AES_SECRET_KEY}
```

## 配置验证

### 后端配置检查

启动后访问:
- 健康检查: http://localhost:18045/actuator/health
- 配置信息: http://localhost:18045/actuator/configprops

### 前端配置检查

在浏览器控制台执行:
```javascript
console.log(import.meta.env)
```

## 相关链接

- [安装指南](/getting-started/installation)
- [首次运行](/getting-started/first-run)
- [系统架构](/architecture/overview)
