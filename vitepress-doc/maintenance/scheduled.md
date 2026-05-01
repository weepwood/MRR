# 定期维护

## 日常巡检

### 每日检查项

| 检查项 | 命令/方法 | 期望结果 |
|--------|-----------|----------|
| 服务健康状态 | `curl /actuator/health` | `status: UP` |
| 磁盘使用率 | `df -h` | < 80% |
| 数据库连接数 | `SELECT count(*) FROM pg_stat_activity;` | < 50 |
| 日志错误数 | `grep ERROR logs/*.log | wc -l` | < 10 |
| API 响应时间 | 查看监控面板 p95 延迟 | < 500ms |

### 每周检查项

- [ ] 检查日志归档是否正常
- [ ] 检查备份文件完整性
- [ ] 检查系统安全更新
- [ ] 审查错误日志趋势
- [ ] 检查证书有效期

### 每月检查项

- [ ] 执行备份恢复演练
- [ ] 性能基准测试
- [ ] 数据库 VACUUM 和 ANALYZE
- [ ] 更新依赖版本（安全更新）
- [ ] 审查系统性能报告
- [ ] 清理临时文件和过期数据

## 数据库维护

### 定期 VACUUM

```sql
-- 分析表统计信息
ANALYZE;

-- 清理死元组
VACUUM (VERBOSE, ANALYZE);

-- 查看需要 VACUUM 的表
SELECT schemaname, tablename, n_dead_tup, n_live_tup,
       last_vacuum, last_autovacuum
FROM pg_stat_user_tables
WHERE n_dead_tup > 1000
ORDER BY n_dead_tup DESC;
```

### 索引维护

```sql
-- 重建索引（低峰期执行）
REINDEX INDEX idx_scan_date;
REINDEX INDEX idx_patient_name;

-- 检查索引使用率
SELECT schemaname, tablename, indexname, idx_scan
FROM pg_stat_user_indexes
WHERE idx_scan = 0;
```

## 日志清理

### 自动清理配置

```properties
# 日志保留策略
log.retention.enabled=true
log.retention.days=90
```

### 手动清理

```bash
# 清理 30 天前的日志归档
find logs/ -name "*.gz" -mtime +30 -delete

# 清理应用日志
truncate -s 0 logs/mrr-backend.log
```

## 证书管理

### SSL 证书检查

```bash
# 检查证书到期时间
openssl x509 -in cert.pem -noout -enddate

# 检查证书链
openssl verify -CAfile ca.pem cert.pem
```

### 证书更新流程

1. 获取新证书
2. 替换证书文件
3. 重启服务
4. 验证 HTTPS 访问

## 磁盘空间管理

### 监控指标

```
告警阈值:
- 磁盘使用率 > 80%: 警告
- 磁盘使用率 > 90%: 严重
- inode 使用率 > 80%: 警告
```

### 清理策略

```bash
# 清理 Docker 无用资源
docker system prune -f

# 清理 Maven 缓存
mvn dependency:purge-local-repository

# 清理 npm 缓存
npm cache clean --force

# 清理临时文件
rm -rf /tmp/*.tmp
```

## 维护日历

```
每日 09:00 - 自动健康检查
每日 02:00 - 数据库全量备份
每周日 03:00 - 日志归档和清理
每月 1 日 04:00 - 数据库 VACUUM
每季度 1 日 - 安全更新和性能测试
```

## 维护窗口

- **例行维护**: 每月第二个周六 02:00-06:00
- **紧急维护**: 根据需要通知相关人员
- **维护公告**: 提前 3 个工作日发布维护通知
