# 性能监控

## 内置监控端点

Spring Boot Actuator 提供以下监控端点：

| 端点 | 路径 | 说明 |
|------|------|------|
| 健康检查 | `/actuator/health` | 服务状态 |
| 指标 | `/actuator/metrics` | JVM、内存、GC 等 |
| 请求追踪 | `/actuator/httpexchanges` | HTTP 请求记录 |
| 日志级别 | `/actuator/loggers` | 动态调整日志级别 |
| 线程信息 | `/actuator/threaddump` | 线程栈快照 |
| 堆信息 | `/actuator/heapdump` | 堆转储文件 |

### 健康检查

```bash
curl http://localhost:18045/actuator/health
```

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP", "database": "PostgreSQL" },
    "diskSpace": { "status": "UP", "total": 512345678, "free": 234567890 }
  }
}
```

## Prometheus 监控

### 配置

```properties
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.prometheus.metrics.export.enabled=true
management.metrics.tags.application=mrr-backend
```

### 采集指标

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'mrr-backend'
    scrape_interval: 15s
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['localhost:18045']
```

### 关键指标

| 指标名 | 类型 | 说明 |
|--------|------|------|
| `jvm_memory_used_bytes` | Gauge | JVM 内存使用 |
| `jvm_gc_pause_seconds` | Summary | GC 暂停时间 |
| `http_server_requests_seconds` | Summary | HTTP 请求延迟 |
| `db_connections_active` | Gauge | 活跃数据库连接 |
| `logback_events_total` | Counter | 日志事件计数 |

## Grafana 仪表盘

推荐添加的仪表盘面板：

1. **系统概览** - CPU、内存、磁盘、网络
2. **JVM 监控** - 堆内存、GC 次数、线程数
3. **API 监控** - 请求量、延迟分布、错误率
4. **数据库监控** - 连接数、查询性能

## 告警规则

```yaml
groups:
  - name: mrr-alerts
    rules:
      - alert: HighErrorRate
        expr: rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 0.1
        for: 5m
        labels: { severity: critical }
        annotations:
          summary: "API 错误率超过 10%"

      - alert: HighMemoryUsage
        expr: jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} > 0.9
        for: 10m
        labels: { severity: warning }
        annotations:
          summary: "JVM 堆内存使用超过 90%"

      - alert: DatabaseDown
        expr: spring_datasource_connections_active == 0
        for: 1m
        labels: { severity: critical }
        annotations:
          summary: "数据库连接丢失"
```
