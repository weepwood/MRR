# 日志管理

## 日志文件位置

```
backend-repo/logs/
├── mrr-backend.log          # 当前日志
├── mrr-backend.log.2026-04-13.0.gz  # 历史归档
└── ...
```

## 日志级别配置

`application-{profile}.properties` 中配置：

```properties
# 全局级别
logging.level.root=INFO

# 应用级别（开发环境建议 DEBUG）
logging.level.com.zjcxph.imgapi=INFO
logging.level.com.zjcxph.imgapi.mapper=DEBUG

# 第三方库级别
logging.level.org.springframework.web=INFO
```

## 日志轮转策略

```properties
# 文件大小轮转
logging.logback.rollingpolicy.max-file-size=10MB

# 保留历史天数
logging.logback.rollingpolicy.max-history=30

# 总大小限制
logging.logback.rollingpolicy.total-size-cap=1GB
```

## 结构化日志（生产环境）

生产环境使用 JSON 格式输出，便于 ELK 等日志平台采集：

```json
{
  "@timestamp": "2026-04-13T10:30:45.123+08:00",
  "level": "INFO",
  "logger": "com.zjcxph.imgapi.controller.ScanController",
  "message": "查询扫描记录成功",
  "requestId": "a1b2c3d4",
  "userId": "u001",
  "clientIp": "192.168.1.100",
  "durationMs": 45
}
```

## 常用操作

### 查看实时日志

```bash
# 实时跟踪
tail -f logs/mrr-backend.log

# 按关键字过滤
tail -f logs/mrr-backend.log | grep "ERROR"

# 查看最后 100 行
tail -100 logs/mrr-backend.log
```

### 清理历史日志

```bash
# 手动清理 30 天前的日志
find logs/ -name "*.gz" -mtime +30 -delete

# 系统自动清理（需开启配置）
# application.properties:
# log.retention.enabled=true
# log.retention.days=90
```
