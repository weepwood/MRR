# 故障处理

## 常见问题

### 1. 后端服务无法启动

**现象**: 启动报错，进程退出

**排查步骤**:

1. 检查端口是否被占用
   ```bash
   netstat -ano | findstr :18045   # Windows
   lsof -i :18045                   # Linux/macOS
   ```

2. 检查数据库连接
   ```bash
   psql -U postgres -d imgapi -c "SELECT 1;"
   ```

3. 检查配置文件
   ```bash
   # 确认 application-local.properties 存在且配置正确
   # 数据库密码、URL 等关键参数
   ```

4. 查看详细错误日志
   ```bash
   # 以 DEBUG 级别启动
   mvn spring-boot:run --debug
   ```

### 2. 数据库连接失败

**现象**: `org.postgresql.util.PSQLException: Connection refused`

**原因及解决**:

| 原因 | 解决方法 |
|------|----------|
| PostgreSQL 服务未启动 | `systemctl start postgresql` (Linux) / 启动 Windows 服务 |
| 连接参数错误 | 检查 URL、用户名、密码 |
| 防火墙拦截 | 检查 5432 端口是否开放 |
| pg_hba.conf 配置 | 确认允许远程连接 |
| 连接池耗尽 | 增大 `maximum-pool-size` |

### 3. 前端页面白屏

**现象**: 访问前端页面显示空白

**排查步骤**:

1. 检查浏览器控制台（F12）
   - 是否有 JS 报错
   - 是否有 API 请求失败

2. 检查 API 代理配置
   ```javascript
   // vite.config.ts
   server: {
     proxy: {
       '/api': {
         target: 'http://localhost:18045',  // 确认地址正确
         changeOrigin: true
       }
     }
   }
   ```

3. 检查构建产物
   ```bash
   pnpm build
   # 检查 dist/ 目录是否正确生成
   ```

### 4. 登录失败

**现象**: 用户名密码正确但登录失败

**排查**:
```bash
# 1. 检查数据库中用户是否存在
psql -U postgres -d imgapi -c "SELECT user_name, status FROM app.mr_auth_user;"

# 2. 检查 JWT 密钥是否一致
# 确认 application.properties 中的 jwt.secret 未变更

# 3. 检查 Token 是否过期
# 默认 24 小时过期
```

### 5. 影像上传失败

**现象**: 上传影像文件时出错

**排查**:

1. 检查文件大小限制
   ```properties
   spring.servlet.multipart.max-file-size=100MB
   ```

2. 检查存储路径权限
   ```bash
   ls -la ./data/img/   # 确认目录可写
   ```

3. 检查磁盘空间
   ```bash
   df -h                # Linux/macOS
   wmic logicaldisk get size,freespace,caption  # Windows
   ```

### 6. Docker 部署问题

**现象**: `docker compose up` 后服务无法正常启动

**排查**:

```bash
# 查看容器日志
docker compose logs backend
docker compose logs frontend

# 检查容器状态
docker compose ps

# 进入容器调试
docker exec -it mrr-backend sh

# 重启单个服务
docker compose restart backend
```

## 紧急恢复流程

### 服务宕机

1. **确认影响范围**
   ```bash
   curl http://localhost:18045/actuator/health
   ```

2. **重启服务**
   ```bash
   # 后端
   systemctl restart mrr-backend    # systemd
   docker compose restart backend   # Docker

   # 前端（Nginx）
   systemctl restart nginx
   ```

3. **检查恢复**
   ```bash
   curl http://localhost:18045/actuator/health | grep UP
   ```

### 数据损坏

1. **停止服务**
2. **从备份恢复**
   ```bash
   pg_restore -U postgres -d imgapi last_good_backup.dump
   ```
3. **验证数据完整性**
4. **重启服务**

## 诊断命令速查

```bash
# 系统资源
top / htop                  # CPU 和内存
df -h                       # 磁盘空间
free -h                     # 内存使用

# Java 诊断
jstack <pid>                # 线程栈
jmap -heap <pid>            # 堆内存
jstat -gcutil <pid> 1s     # GC 状态

# 网络诊断
ping <host>                 # 连通性
telnet <host> <port>        # 端口连通性
curl -v <url>               # HTTP 请求诊断

# PostgreSQL 诊断
pg_stat_activity           # 当前连接和查询
pg_stat_database           # 数据库统计
pg_locks                   # 锁信息
```
