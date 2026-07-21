# 安全维护

## 安全加固清单

### 生产环境检查项

- [ ] 修改所有默认密码
- [ ] 更换 JWT 签名密钥
- [ ] 更换 AES 加密密钥
- [ ] 配置 HTTPS（SSL 证书）
- [ ] 禁用 Swagger UI（生产）
- [ ] 配置 CORS 白名单
- [ ] 开启 CSRF 防护
- [ ] 配置安全响应头
- [ ] 关闭不必要的端口
- [ ] 配置防火墙规则

### 安全配置示例

```properties
# HTTPS 配置
server.ssl.enabled=true
server.ssl.key-store=/etc/ssl/mrr/keystore.p12
server.ssl.key-store-password=${SSL_KEYSTORE_PASSWORD}
server.ssl.key-store-type=PKCS12

# 安全响应头
server.http2.enabled=true
server.tomcat.max-http-header-size=32768
```

## 密钥管理

### 敏感信息配置

```properties
# 使用环境变量（推荐）
aes.secret.key=${AES_SECRET_KEY:}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}
# JWT 签名密钥由 JWT_SECRET_KEY 环境变量提供。
```

### 密钥轮换

```bash
# 生成新 JWT 密钥（256 位）
openssl rand -base64 32

# 生成新 AES 密钥（256 位）
openssl enc -aes-256-cbc -k secret -P -md sha1
```

## 访问控制

### 防火墙规则

```bash
# iptables 示例
iptables -A INPUT -p tcp --dport 18045 -s 10.0.0.0/8 -j ACCEPT
iptables -A INPUT -p tcp --dport 18045 -j DROP
iptables -A INPUT -p tcp --dport 5432 -s 10.0.0.0/8 -j ACCEPT
iptables -A INPUT -p tcp --dport 5432 -j DROP
```

### Nginx 安全配置

```nginx
# 隐藏版本号
server_tokens off;

# 安全响应头
add_header X-Frame-Options "SAMEORIGIN" always;
add_header X-Content-Type-Options "nosniff" always;
add_header X-XSS-Protection "1; mode=block" always;
add_header Strict-Transport-Security "max-age=31536000" always;

# 限制请求速率
limit_req_zone $binary_remote_addr zone=api:10m rate=10r/s;
```

## 依赖安全

### 定期扫描

```bash
# OWASP 依赖检查（后端）
mvn org.owasp:dependency-check-maven:check

# npm 审计（前端）
cd frontend-fantastic-admin
npm audit
```

### 更新策略

```bash
# 更新 Maven 依赖版本
mvn versions:display-dependency-updates

# 更新 npm 依赖
npm outdated
npm update
```

## 数据安全

### 敏感数据加密

```java
// AES 加密敏感字段
@Column(name = "id_number")
private String idNumber;  // 存储时加密，读取时解密
```

### SQL 注入防护

- 使用 MyBatis 参数绑定（`#{}`）而非拼接
- 禁止使用 `${}` 传递用户输入
- 定期审计 SQL 日志

### XSS 防护

- 前端对用户输入进行转义
- 后端设置 Content-Security-Policy 头
- 使用 Element Plus 内置的 XSS 防护

## 日志安全

### 日志脱敏

```java
// 敏感信息脱敏
String maskIdNumber(String idNumber) {
    if (idNumber == null || idNumber.length() < 6) return idNumber;
    return idNumber.substring(0, 3) + "****" +
           idNumber.substring(idNumber.length() - 4);
}
```

### 审计日志

```sql
-- 记录关键操作
INSERT INTO app.mr_audit_log
(user_id, action, target, detail, ip_address)
VALUES ('u001', 'DELETE_SCAN', 'S001',
        '删除扫描记录 S001', '192.168.1.100');
```

## 应急响应

### 安全事件处理流程

1. **发现** - 通过监控或报告发现安全事件
2. **评估** - 确定影响范围和严重等级
3. **遏制** - 隔离受影响系统
4. **根除** - 修复漏洞或清除威胁
5. **恢复** - 恢复正常服务
6. **总结** - 事件复盘和改进

### 密码泄露响应

1. 立即吊销泄露凭据
2. 通知所有用户修改密码
3. 审计近期的异常登录
4. 强制 Token 失效
5. 检查系统完整性
