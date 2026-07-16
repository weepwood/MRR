# 生产运行手册

本文提供 Windows 内网部署下的日常检查、故障处置和变更操作清单。命令中的路径、服务名和数据库名应按实际环境调整。

## 每日检查

- [ ] `/status` 当前状态正常，最近异常区间已确认。
- [ ] `http://127.0.0.1:18046/actuator/health` 返回 `UP`。
- [ ] 后端日志没有持续数据库连接、Flyway、OSS 签名或图片读取错误。
- [ ] PostgreSQL 连接数、锁等待和长事务处于正常范围。
- [ ] HikariCP 没有持续耗尽。
- [ ] 系统盘、数据库盘、日志盘和图片盘空间充足。
- [ ] 最近备份任务成功。
- [ ] 抽样打开一份本地图片病案和一份 OSS 已迁移病案。

## 服务启停

### 启动前

1. 检查 PostgreSQL 已启动。
2. 检查配置文件和环境变量。
3. 确认业务端口 `18045`、管理端口 `18046` 未被其它进程占用。
4. 查看待执行 Flyway 迁移和数据库备份状态。
5. 大迁移、大索引或数据回填期间不要直接重启应用。

### 前台启动并保留完整错误

```powershell
java -jar imgapi-0.2.0.jar --spring.config.additional-location=file:./application.properties
```

需要更详细诊断时可临时增加：

```powershell
$env:LOGGING_LEVEL_COM_ZJCXPH_IMGAPI='DEBUG'
$env:MAPPER_LOG_LEVEL='DEBUG'
java -jar imgapi-0.2.0.jar --spring.config.additional-location=file:./application.properties
```

问题确认后应恢复日志级别，避免 SQL 日志影响性能并泄露数据。

### 停止

优先使用 Windows 服务管理器或部署脚本的正常停止操作，让 Spring Boot 在配置的优雅关闭时间内完成请求。只有进程失去响应且无法正常停止时才强制结束。

## 发布流程

1. 记录当前版本、JAR、前端目录、文档目录和配置文件校验和。
2. 完成 PostgreSQL 备份并验证备份文件可读。
3. 在数据库副本执行新迁移。
4. 构建并验证后端、前端和两套文档。
5. 进入维护窗口，停止对数据有写入的业务入口。
6. 更新 JAR 和静态资源，保留上一版本。
7. 启动后端，确认 Flyway 和健康检查。
8. 测试登录、查询、档案袋、下载、PDF、统计、日志和文档。
9. 观察错误率、连接池、锁、CPU、内存和磁盘。
10. 记录发布结果和异常。

## PostgreSQL 快速检查

### 当前连接

```sql
SELECT datname, usename, state, wait_event_type, wait_event, count(*)
FROM pg_stat_activity
GROUP BY datname, usename, state, wait_event_type, wait_event
ORDER BY count(*) DESC;
```

### 长事务

```sql
SELECT pid, usename, now() - xact_start AS xact_age, state, wait_event_type, query
FROM pg_stat_activity
WHERE xact_start IS NOT NULL
ORDER BY xact_start;
```

### 锁等待

```sql
SELECT pid, usename, wait_event_type, wait_event, query
FROM pg_stat_activity
WHERE wait_event_type = 'Lock';
```

### 表行数

精确统计大表可能耗时：

```sql
SELECT count(*) FROM app.mr_scan;
```

日常观察可先使用统计估算：

```sql
SELECT relname, n_live_tup
FROM pg_stat_user_tables
WHERE schemaname = 'app'
ORDER BY n_live_tup DESC;
```

## 常见故障

### 后端启动失败：数据库连接

检查：

- `SPRING_DATASOURCE_URL`、用户名和密码。
- PostgreSQL 服务和端口。
- 数据库名与 `currentSchema=app`。
- `pg_hba.conf` 和防火墙。
- 连接池是否被旧进程占满。

### 后端启动失败：Flyway

1. 保存完整错误和迁移版本。
2. 查询 `app.flyway_schema_history`。
3. 确认迁移文件是否被修改、删除或重命名。
4. 不要先执行 `repair`。
5. 恢复已部署迁移原文件，再新增独立修正迁移。

### 图片无法访问

检查：

1. `bah/sjh` 是否保持原始格式。
2. 图片来源设置是 `local` 还是 `oss`。
3. 本地根目录、folder、filename 和文件权限。
4. 路径是否被安全校验拒绝。
5. OSS URL、区域、时间和签名配置。
6. OSS 模式回退本地后，本地文件是否存在。

### PDF 跨域失败

使用浏览器网络面板确认图片响应是否包含正确的 `Access-Control-Allow-Origin`。来源必须与管理端协议、域名和端口一致。

### 导入长时间停住

- `wait_event_type=Client`：检查 psql 客户端、网络、CSV 读取盘和输出接收端。
- `wait_event_type=Lock`：定位阻塞会话，不要直接结束未知生产事务。
- WAL 或磁盘满：停止新批次，释放空间并确认数据库一致性。
- 单事务过大：改为分卷、分批和每文件独立提交。

### Nginx 启动失败

```powershell
nginx.exe -t
```

先修复配置测试错误再重启。Windows 配置文件保存为 UTF-8 无 BOM，检查不可见字符、错误路径分隔符和重复 `server`/`listen`。

查看端口占用：

```powershell
netstat -ano | findstr :8001
tasklist /FI "PID eq <PID>"
```

## 数据质量处置

数据质量检查默认手动触发。发现问题后：

1. 保存检查批次和异常样本。
2. 判断是源数据错误、历史导入、关联回填还是文件缺失。
3. 不直接批量修改生产数据。
4. 在副本编写修复 SQL 并验证影响行数。
5. 备份后在维护窗口执行。
6. 重新运行检查并抽样打开图片。

重点关注：

- `archive_id` 为空或指向错误病案。
- 高位病案号缺少上架号。
- 上架号重复。
- 编号被错误补零。
- 类型超出 `0`～`15`。
- 图片元数据与文件不一致。
- OSS 已标记迁移但对象不可访问。

## 回滚原则

- 静态资源和 JAR：保留上一版本，可快速切回。
- 配置：保留发布前备份，密钥不写入仓库。
- 数据库：优先前向修复。已执行迁移通常不通过删除迁移记录回滚。
- 数据导入：每批独立事务和日志，出现问题时只撤销可明确定位的批次。
- 图片：删除或覆盖前必须有文件级备份或对象版本能力。

## 事件记录

每次故障至少记录：

- 开始、发现、恢复时间。
- 影响范围。
- 当前版本和最近变更。
- 关键日志、SQL 状态和监控截图。
- 临时处置。
- 根因和永久修复。
- 是否需要补充监控、测试或文档。
