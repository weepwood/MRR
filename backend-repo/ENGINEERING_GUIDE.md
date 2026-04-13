# 工程化优化使用指南

本文档说明最近实施的工程化优化功能的使用方法。

## 📋 目录

1. [配置文件管理](#配置文件管理)
2. [结构化日志](#结构化日志)
3. [自动化测试](#自动化测试)
4. [性能压测](#性能压测)

---

## 配置文件管理

### 新的配置文件命名规范

- ✅ **模板文件**: `application-local.template.properties` (提交到版本控制)
- ❌ **本地配置**: `application-local.properties` (已加入 .gitignore)

### 使用步骤

```bash
# 1. 复制模板文件
cp src/main/resources/application-local.template.properties src/main/resources/application-local.properties

# 2. 编辑本地配置（修改数据库密码、路径等）
# 使用你喜欢的编辑器打开 application-local.properties

# 3. 启动应用时使用 local profile
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### 配置文件结构

所有配置项已按功能分组并添加详细注释：
- Server Configuration
- Image Service Configuration
- Database Configuration
- Security Configuration
- OSS Configuration (可选)
- Log Retention Configuration
- Logging Level
- DevTools

---

## 结构化日志

### 特性

✅ **JSON 格式输出** - 便于日志聚合和分析  
✅ **MDC 上下文追踪** - 每个请求有唯一 ID  
✅ **多环境支持** - 开发环境人类可读，生产环境 JSON  
✅ **异步日志** - 提升性能  
✅ **自动轮转** - 防止日志文件过大  

### 日志格式示例

#### 开发环境 (控制台 - 人类可读)
```
2026-04-13 10:30:45.123 INFO  12345 --- [async-1] c.z.i.controller.ImageController      : 获取图片信息成功
```

#### 生产环境 (JSON 格式)
```json
{
  "@timestamp": "2026-04-13T10:30:45.123+08:00",
  "level": "INFO",
  "logger_name": "com.zjcxph.imgapi.controller.ImageController",
  "message": "获取图片信息成功",
  "app_name": "img-api",
  "requestId": "a1b2c3d4e5f6g7h8",
  "userId": "user123",
  "clientIp": "192.168.1.100",
  "thread_name": "http-nio-18045-exec-1"
}
```

### MDC 上下文字段

| 字段 | 说明 | 来源 |
|------|------|------|
| requestId | 请求唯一标识 | 自动生成 (UUID) |
| clientIp | 客户端 IP | 从请求头提取 |
| userId | 用户 ID | 从请求头 X-User-Id |
| userRole | 用户角色 | 从请求头 X-User-Role |

### 日志级别配置

```properties
# 开发环境 - 显示详细信息
logging.level.com.zjcxph.imgapi=DEBUG
logging.level.com.zjcxph.imgapi.mapper=DEBUG

# 生产环境 - 减少日志量
logging.level.com.zjcxph.imgapi=INFO
logging.level.com.zjcxph.imgapi.mapper=WARN
```

### 集成 ELK Stack (可选)

如果使用 Elasticsearch + Logstash + Kibana：

```yaml
# logback-spring.xml 已配置好 JSON 输出
# 只需在 Filebeat 中收集日志文件即可
filebeat.inputs:
  - type: log
    paths:
      - /path/to/img-api.log*.gz
    json.keys_under_root: true
    json.add_error_key: true
```

---

## 自动化测试

### CI/CD 工作流

项目包含两个 GitHub Actions 工作流：

#### 1. CI/CD Pipeline (`.github/workflows/ci-cd.yml`)

**触发条件**:
- Push 到 `main` 或 `develop` 分支
- Pull Request
- Tag 推送 (v*)

**执行任务**:
- ✅ 代码编译和风格检查
- ✅ 单元测试
- ✅ 集成测试 (带 PostgreSQL 服务)
- ✅ 代码覆盖率检查 (阈值: 50%)
- ✅ Docker 镜像构建和推送
- ✅ OWASP 依赖安全扫描

**查看结果**:
```
GitHub -> Actions -> CI/CD Pipeline
```

#### 2. Performance Testing (`.github/workflows/performance-testing.yml`)

**触发条件**:
- 定时调度 (每周日凌晨 2 点)
- 手动触发 (workflow_dispatch)
- Push 到 main 分支的代码变更

**执行任务**:
- 🚀 轻量级测试 (10 并发, 50 请求)
- 🚀 中量级测试 (20 并发, 100 请求)
- 🚀 重量级测试 (50 并发, 200 请求)
- 📊 生成性能报告
- ⚠️ 性能回归检测

**性能阈值**:
| 指标 | 轻量级 | 中量级 |
|------|--------|--------|
| 成功率 | ≥ 95% | ≥ 90% |
| 平均延迟 | ≤ 1000ms | - |

---

## 性能压测

### 本地测试脚本

提供两种平台的测试脚本：

#### Linux/macOS (Bash)
```bash
# 赋予执行权限
chmod +x test-performance.sh

# 运行所有测试
./test-performance.sh

# 运行特定测试
./test-performance.sh light    # 轻量级
./test-performance.sh medium   # 中量级
./test-performance.sh heavy    # 重量级
./test-performance.sh all      # 全部 (默认)

# 自定义服务器地址
BASE_URL=http://your-server:18045 ./test-performance.sh
```

#### Windows (PowerShell)
```powershell
# 运行所有测试
.\test-performance.ps1

# 运行特定测试
.\test-performance.ps1 -TestType light
.\test-performance.ps1 -TestType medium
.\test-performance.ps1 -TestType heavy
.\test-performance.ps1 -TestType all

# 自定义服务器地址
.\test-performance.ps1 -BaseUrl http://your-server:18045
```

### 通过 API 直接测试

```bash
# 发起压力测试
curl -X POST http://localhost:18045/api/v1/monitoring/pressure-tests \
  -H "Content-Type: application/json" \
  -d '{
    "name": "my-test",
    "targetUrl": "http://localhost:18045/api/v1/system/info",
    "method": "GET",
    "concurrency": 20,
    "totalRequests": 100,
    "timeoutMillis": 5000
  }'

# 查看测试结果
curl http://localhost:18045/api/v1/monitoring/pressure-tests

# 查看最新测试结果
curl http://localhost:18045/api/v1/monitoring/pressure-tests/latest

# 查看历史测试
curl http://localhost:18045/api/v1/monitoring/pressure-tests/history
```

### 测试报告解读

响应示例：
```json
{
  "runId": "pt-20260413103045",
  "name": "medium-load-test",
  "targetUrl": "http://localhost:18045/api/v1/system/info",
  "method": "GET",
  "concurrency": 20,
  "totalRequests": 100,
  "successCount": 98,
  "failureCount": 2,
  "successRate": 98.0,
  "minLatencyMs": 15,
  "avgLatencyMs": 120,
  "p95LatencyMs": 250,
  "maxLatencyMs": 450,
  "requestsPerSecond": 85.47,
  "durationMillis": 1170,
  "startTime": "2026-04-13T10:30:45Z",
  "endTime": "2026-04-13T10:30:46Z"
}
```

**关键指标**:
- **successRate**: 成功率 (越高越好)
- **avgLatencyMs**: 平均延迟 (越低越好)
- **p95LatencyMs**: 95% 请求的延迟 (关注长尾延迟)
- **requestsPerSecond**: 每秒请求数 (吞吐量)

---

## 🔧 故障排查

### 问题 1: 日志不是 JSON 格式

**原因**: 未指定 Spring Profile  
**解决**: 
```bash
# 显式指定 prod 或 test profile
java -jar app.jar --spring.profiles.active=prod
```

### 问题 2: MDC 字段为空

**原因**: 请求未经过 LogInterceptor  
**解决**: 确保请求路径未被排除 (检查 `LogInterceptor.shouldSkipLogging`)

### 问题 3: CI/CD 失败

**常见原因**:
- 数据库连接失败 → 检查 PostgreSQL 服务状态
- 测试超时 → 增加 timeout 或优化测试
- 覆盖率不足 → 补充单元测试

### 问题 4: 压测脚本无法执行

**Linux/macOS**:
```bash
# 检查权限
ls -l test-performance.sh

# 添加执行权限
chmod +x test-performance.sh
```

**Windows**:
```powershell
# 允许执行脚本
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
```

---

## 📚 相关文档

- [Spring Boot Logging](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.logging)
- [Logstash Logback Encoder](https://github.com/logfellow/logstash-logback-encoder)
- [GitHub Actions](https://docs.github.com/en/actions)
- [JaCoCo Code Coverage](https://www.jacoco.org/jacoco/)

---

## 🎯 下一步优化建议

1. **添加分布式追踪**: 集成 OpenTelemetry + Jaeger
2. **监控告警**: Prometheus + Grafana + AlertManager
3. **API 文档**: 使用 Spring REST Docs 生成更详细的文档
4. **数据库迁移**: 引入 Flyway 管理 schema 变更
5. **密钥管理**: 使用 HashiCorp Vault 管理敏感配置

---

**最后更新**: 2026-04-13
