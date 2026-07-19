# 认证测试台：外部影像票据配置与模拟

## 1. 为什么随机生成 Secret 后仍返回 503

下面的响应：

```json
{
  "code": 503,
  "message": "外部系统集成未启用"
}
```

说明后端当前仍然读取到：

```properties
mrr.integration.enabled=false
```

认证测试页中的“生成 256 位 Secret”只会在浏览器内存中生成随机值，不会自动修改 Windows 服务器上的 `application-secrets.properties`，也不会自动重启后端。

后端验证顺序是：

1. 外部集成是否启用；
2. Client ID 是否存在并启用；
3. 服务端是否配置 Secret；
4. 来源 IP 是否在白名单；
5. 时间戳、签名和 nonce 是否有效；
6. 病案参数是否能够解析。

因此集成未启用时，请求还没有进入 HMAC Secret 比对阶段。

## 2. 页面生成 Secret 后的正确操作

在后台进入：

```text
运维 → 认证接口测试 → 外部影像票据
```

执行：

1. 点击“生成 256 位 Secret”；
2. 页面会生成完整的 `application-secrets.properties` 配置片段；
3. 复制配置片段；
4. 在 MRR 实际部署目录中打开 `application-secrets.properties`；
5. 把同一个 Client ID 和同一个 Secret 写入后端配置；
6. 将 `mrr.integration.enabled` 改为 `true`；
7. 设置真实来源 IP；
8. 重启 MRR 后端；
9. 回到页面点击“检查后端配置”。

页面检查项全部通过后，才发送正常票据请求。

示例：

```properties
mrr.integration.enabled=true
mrr.integration.ticket-ttl-seconds=90
mrr.integration.session-ttl-seconds=1800
mrr.integration.timestamp-tolerance-seconds=300
mrr.integration.max-archives-per-ticket=100

mrr.integration.clients[0].client-id=his-system
mrr.integration.clients[0].secret=页面生成的同一个值
mrr.integration.clients[0].enabled=true
mrr.integration.clients[0].allowed-ips[0]=MRR状态检查显示的请求IP
```

如果已经配置了其他客户端，不要覆盖其索引。使用下一个未占用的 `clients[n]`。

## 3. 后端配置检查接口

测试页调用：

```http
GET /api/v1/integration/archive/status
Authorization: Bearer <MRR管理员JWT>
```

该接口要求 `user:manage` 权限，返回：

- 外部集成是否启用；
- 当前请求 IP；
- Ticket、Session 和时间戳有效期；
- 已配置的 Client ID；
- 客户端是否启用；
- Secret 是否已配置；
- 当前 IP 是否允许；
- IP 白名单。

接口只返回 `secretConfigured=true/false`，不会返回 Secret 明文。

## 4. 页面上的五项检查

正常请求前应全部通过：

1. **集成功能**：`mrr.integration.enabled=true`；
2. **Client ID**：页面 Client ID 与服务端配置一致；
3. **服务端 Secret**：对应客户端配置了非空 Secret；
4. **来源 IP**：当前请求 IP 位于白名单，或白名单为空；
5. **页面 Secret**：页面填写了与后端完全相同的值。

页面无法读取服务端 Secret，因此只能检查“服务端是否配置”，不能直接显示或回填真实值。若前四项通过但返回“签名无效”，通常是页面 Secret 与后端不同。

## 5. 模拟场景

### 正常请求

自动使用当前时间、新 nonce，并在发送前根据原始 JSON 重新签名。

预期：

```text
HTTP 200
```

成功响应包含 `ticket`、`launchUrl`、`expiresIn` 和 `archiveCount`。

### 过期时间戳

页面根据后端时间容差自动生成一个超出允许范围的时间戳。

预期：

```text
HTTP 401：签名时间戳已过期
```

### 错误签名

页面生成正确 Canonical Text，但把签名替换为 64 个 `0`。

预期：

```text
HTTP 401：外部系统签名无效
```

### nonce 重放

先成功创建一次 Ticket，然后点击“重放上次成功请求”。页面会复用相同时间戳、nonce、原始 JSON 和签名。

预期：

```text
HTTP 409：签名 nonce 已使用
```

## 6. 常见错误

### 503 外部系统集成未启用

修改：

```properties
mrr.integration.enabled=true
```

然后重启后端。

### 503 外部系统客户端密钥未配置

对应 `clients[n].secret` 为空。写入页面生成的同一个值并重启。

### 401 外部系统客户端无效

页面中的 Client ID 与后端配置不同，或客户端 `enabled=false`。

### 401 外部系统签名无效

检查：

- 页面 Secret 与后端是否逐字符一致；
- Secret 前后是否带空格；
- 签名后是否修改过原始 JSON；
- 接口路径是否一致；
- 时间戳和 nonce 是否在签名后被修改。

### 403 当前来源 IP 不在白名单

以状态检查接口显示的 `requestIp` 为准，将它加入对应客户端：

```properties
mrr.integration.clients[0].allowed-ips[0]=实际请求IP
```

经 Nginx 代理时，应正确传递 `X-Forwarded-For`，否则 MRR 看到的可能是代理服务器 IP。

### 404 未定位到可访问病案

HMAC 认证已经通过，但身份证、病案号或上架号没有匹配数据。换成数据库中真实存在的病案参数。

## 7. 安全要求

- 测试页生成的 Secret 只保存在页面内存，刷新后消失；
- 不要把生产 Secret 提交到 Git；
- 不要把 Secret 放入 URL、日志或前端源码；
- 正式 HIS/EMR 接入必须在外部系统后端签名；
- 每个外部系统使用独立 Client ID 和独立 Secret；
- 修改 Secret 后必须同步更新双方配置并重启相关服务。
