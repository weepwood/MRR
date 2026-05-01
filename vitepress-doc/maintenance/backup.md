# 数据备份与恢复

## 备份策略

| 备份类型 | 频率 | 保留时间 | 说明 |
|----------|------|----------|------|
| 全量备份 | 每日 | 7 天 | 凌晨低峰期执行 |
| 增量备份 | 每 6 小时 | 3 天 | WAL 归档 |
| 周备份 | 每周日 | 30 天 | 完整备份 |
| 月备份 | 每月 1 日 | 12 个月 | 归档备份 |

## PostgreSQL 备份

### 使用 pg_dump

```bash
# 全量备份
pg_dump -U postgres -d imgapi -F c -f backup_$(date +%Y%m%d).dump

# 压缩备份
pg_dump -U postgres -d imgapi -Z 9 -f backup_$(date +%Y%m%d).sql.gz

# 只备份 schema（不含数据）
pg_dump -U postgres -d imgapi -s -f schema_$(date +%Y%m%d).sql
```

### 使用 Docker

```bash
# 备份运行中的数据库
docker exec mrr-postgres pg_dump -U postgres imgapi > backup_$(date +%Y%m%d).sql

# 从备份文件恢复
cat backup.sql | docker exec -i mrr-postgres psql -U postgres -d imgapi
```

## 数据恢复

### 恢复全量备份

```bash
# 从自定义格式恢复
pg_restore -U postgres -d imgapi -c backup_20260413.dump

# 从 SQL 文件恢复
psql -U postgres -d imgapi < backup_20260413.sql
```

### 恢复到新环境

```bash
# 1. 创建数据库
createdb -U postgres imgapi_restore

# 2. 恢复数据
pg_restore -U postgres -d imgapi_restore backup_20260413.dump

# 3. 验证数据
psql -U postgres -d imgapi_restore -c "SELECT count(*) FROM app.mr_scan;"
```

## 自动化备份脚本

创建 `scripts/backup.sh`：

```bash
#!/bin/bash
BACKUP_DIR="/data/backups"
DB_NAME="imgapi"
DB_USER="postgres"
DATE=$(date +%Y%m%d_%H%M%S)

# 创建备份目录
mkdir -p $BACKUP_DIR

# 执行备份
pg_dump -U $DB_USER -d $DB_NAME -F c \
  -f $BACKUP_DIR/${DB_NAME}_$DATE.dump

# 压缩
gzip $BACKUP_DIR/${DB_NAME}_$DATE.dump

# 删除 7 天前的备份
find $BACKUP_DIR -name "*.gz" -mtime +7 -delete

echo "Backup completed: ${DB_NAME}_$DATE.dump.gz"
```

## 验证备份

```bash
# 检查备份文件完整性
pg_restore -l backup.dump > /dev/null && echo "Backup is valid"

# 定期恢复测试（建议每月一次）
# 在测试环境执行完整恢复流程
```
