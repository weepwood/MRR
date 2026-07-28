# 外部系统访问影像档案袋

MRR 后台管理功能继续使用用户名、密码和 JWT 登录。HIS、EMR、电子病历门户等外部系统不共用 MRR 内部账号，而是由外部系统后端使用 HMAC-SHA256 申请一次性访问票据。

## 文档入口

- [HIS、EMR 调阅 MRR 影像档案袋接入指南](./his-external-archive-integration-guide.md)：Python、Java、C# 完整接入流程与示例；
- [认证测试台：外部影像票据配置与模拟](./external-archive-auth-test-guide.md)：MRR 管理员配置、状态检查和错误模拟；
- [可运行示例目录](../examples/his-integration/README.md)：三种语言的票据客户端。

## 1. 推荐调用路线

```text
医生、护士工作站
        ↓
HIS、EMR 前端或桌面客户端
        ↓ 调用 HIS 自己的后端
HIS、EMR 后端服务器
        ↓ 使用 HMAC Secret 签名
MRR Ticket 接口
        ↓ 返回 launchUrl
浏览器打开 MRR 外部影像档案袋
```

正式接入必须在外部系统后端完成签名。浏览器、Vue、React、WPF、WinForms 客户端都不应保存 HMAC Secret。

## 2. MRR 配置

在 Windows 部署的 `application-secrets.properties` 中配置：

```properties
mrr.integration.enabled=true
mrr.integration.ticket-ttl-seconds=90
mrr.integration.session-ttl-seconds=1800
mrr.integration.timestamp-tolerance-seconds=300
mrr.integration.max-archives-per-ticket=100

mrr.integration.clients[0].client-id=his-system
mrr.integration.clients[0].secret=请替换为独立的64位十六进制随机密钥
mrr.integration.clients[0].enabled=true
mrr.integration.clients[0].allowed-ips[0]=10.10.20.15
```

修改后必须重启 MRR 后端。

`JWT_SECRET_KEY` 与外部系统 HMAC Secret 必须使用两个不同的随机值。

### allowed-ips

填写发起 Ticket 请求的 HIS、EMR 后端服务器 IP，不是所有医生电脑 IP。

HIS 有多个后端节点时：

```properties
mrr.integration.clients[0].allowed-ips[0]=10.10.20.15
mrr.integration.clients[0].allowed-ips[1]=10.10.20.16
```

当前实现支持精确 IP、`*` 和空白名单，尚不支持 CIDR。生产环境不建议使用 `*` 或空白名单。

默认不开放浏览器跨域。正式的服务器到服务器 Ticket 请求不受浏览器 CORS 限制。

## 3. 支持的访问条件

请求体中的所有条件采用并集语义，MRR 将查询结果解析为精确的病案号和上架号组合并去重。

```json
{
  "externalUserId": "HIS-USER-10086",
  "idCard": "330000000000000000",
  "bah": "789508",
  "sjh": "123456",
  "bahs": ["789509", "789510"],
  "sjhs": ["123457", "123458"],
  "archives": [
    { "bah": "10000001", "sjh": "20000001" },
    { "bah": "10000002", "sjh": "20000002" }
  ],
  "allowDownload": false
}
```

参数：

- `externalUserId`：外部系统当前操作人员唯一 ID，用于审计，必填；
- `idCard`：授权身份证对应的全部病案；
- `bah`：单个病案号；
- `sjh`：单个唯一上架号；
- `bahs`：多个病案号；
- `sjhs`：多个上架号；
- `archives`：多个精确病案号、上架号组合；
- `allowDownload`：是否允许 ZIP 下载，默认 `false`。

病案号大于等于 `10000000` 时必须同时提供上架号。`bahs` 与 `sjhs` 不按数组下标配对，需要配对时使用 `archives`。

## 4. 申请一次性 Ticket

```http
POST /api/v1/integration/archive/tickets
Content-Type: application/json; charset=utf-8
X-MRR-Client-Id: his-system
X-MRR-Timestamp: 1784383200
X-MRR-Nonce: 7fd72b36-d39a-4ea5-80cb-aafeeaed1815
X-MRR-Signature: 64位十六进制HMAC签名
```

签名原文：

```text
POST
/api/v1/integration/archive/tickets
{timestamp}
{nonce}
{SHA256(rawBody)}
```

签名算法：

```text
lowercaseHex(HMAC-SHA256(UTF8(clientSecret), UTF8(canonicalText)))
```

签名必须针对最终实际发送的原始 UTF-8 JSON 字节计算。不要在签名后重新序列化、格式化或修改 JSON。

成功响应：

```json
{
  "code": 200,
  "data": {
    "ticket": "一次性随机票据",
    "launchUrl": "https://mrr.example/archive/external?ticket=...",
    "expiresIn": 90,
    "archiveCount": 1
  }
}
```

外部系统后端通常只需要把 `launchUrl` 返回给当前 HIS 前端，由前端立即打开。

## 5. 浏览器会话

浏览器打开 `launchUrl` 后，MRR 会自动：

1. 兑换一次性 Ticket；
2. 写入短期 HttpOnly Cookie；
3. 从地址栏移除 Ticket；
4. 读取 Ticket 授权的病案集合；
5. 按病案号和上架号范围访问影像。

正式 HIS 后端不需要管理 MRR 外部 Session Cookie。

## 6. 语言示例

### Python

```text
backend-repo/examples/his-integration/python/mrr_archive_ticket_client.py
```

仅使用 Python 标准库。

### Java

```text
backend-repo/examples/his-integration/java
```

使用 Java 21、Java HttpClient 和 Jackson。

### C#

```text
backend-repo/examples/his-integration/csharp
```

使用 .NET 8、HttpClient、System.Text.Json 和 HMACSHA256。

完整环境变量、运行命令、Spring Boot、ASP.NET Core、WPF 和 WinForms 接入方式见[HIS、EMR 接入指南](./his-external-archive-integration-guide.md)。

## 7. 认证测试台

管理员登录后进入：

```text
运维 → 认证接口测试 → 外部影像票据
```

页面路径：

```text
/auth-test
```

要求 `user:manage` 权限，支持：

- 检查集成功能、Client ID、Secret 配置状态和来源 IP；
- 生成 256 位随机 Secret；
- 生成可复制的 `application-secrets.properties` 配置片段；
- 编辑身份证、病案号、上架号和精确病案组合；
- 查看原始 JSON、Body Hash、Canonical Text 和 HMAC；
- 模拟正常请求、错误签名、过期时间戳和 nonce 重放；
- 创建并打开一次性 `launchUrl`。

测试台的 Secret 只保存在页面内存，不会自动修改后端配置。

## 8. 安全限制

- Ticket 默认 90 秒有效且只能兑换一次；
- 外部影像 Session 默认 30 分钟有效；
- 时间戳默认允许前后 5 分钟偏差；
- nonce 在有效窗口内不能重复；
- 每个外部系统应使用独立 Client ID 和 Secret；
- 外部页面只能访问 Ticket 中解析出的精确病案集合；
- 用户管理、统计、设置、日志和影像分类修改接口不会向外部 Session 开放；
- 身份证号不会进入浏览器跳转 URL；
- 单次 Ticket 默认最多授权 100 份病案；
- 批量下载在前端和后端同时校验 `allowDownload`；
- 数据库只保存 Ticket 和 Session Token 的 SHA-256 摘要。

## 9. 数据库存储与审计

Flyway 创建：

- `mr_external_archive_nonce`：nonce 防重放记录；
- `mr_external_archive_ticket`：一次性 Ticket 状态；
- `mr_external_archive_session`：短期外部 Session；
- `mr_external_archive_access_log`：Ticket、Session、病案、图片和下载审计。

审计日志记录 Client ID、外部用户 ID、病案号、上架号、动作、影像 ID、来源 IP、User-Agent 和结果。

## 10. 反向代理

为了让 `launchUrl` 使用正确的域名并识别来源 IP，Nginx 需要传递：

```nginx
proxy_set_header Host $host;
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header X-Forwarded-Proto $scheme;
# 单层本机 Nginx 必须覆盖客户端自行提交的同名头，防止伪造来源 IP。
proxy_set_header X-Forwarded-For $remote_addr;
```

不要使用 `$proxy_add_x_forwarded_for` 追加客户端自带的转发链。当前正式架构只有一层本机 Nginx，应以 TCP 直接来源 `$remote_addr` 覆盖该请求头。生产环境应使用 HTTPS。

## 11. 联调工具

只申请 Ticket：

```text
backend-repo/scripts/create-external-archive-ticket.py
```

模拟 Ticket、Session、上下文、影像、下载和退出完整链路：

```text
backend-repo/scripts/simulate-external-archive.py
```

示例：

```powershell
python backend-repo/scripts/simulate-external-archive.py `
  --base-url http://localhost:18045 `
  --client-id his-system `
  --secret "替换为外部客户端密钥" `
  --external-user-id HIS-USER-10086 `
  --archive 789508:123456
```

正式 HIS 跳转通常不需要自行兑换 Session，直接打开 Ticket 响应中的 `launchUrl` 即可。
