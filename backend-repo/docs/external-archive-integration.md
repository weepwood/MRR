# 外部系统访问影像档案袋

MRR 的后台管理功能继续使用用户名、密码和 JWT 登录。HIS、EMR 等外部系统不共用内部账号，而是通过服务器端 HMAC 签名申请一次性访问票据。

## 1. 启用配置

在 Windows 部署的 `application-secrets.properties` 中配置：

```properties
mrr.integration.enabled=true
mrr.integration.ticket-ttl-seconds=90
mrr.integration.session-ttl-seconds=1800
mrr.integration.timestamp-tolerance-seconds=300
mrr.integration.max-archives-per-ticket=100

mrr.integration.clients[0].client-id=his-system
mrr.integration.clients[0].secret=请替换为独立的长随机密钥
mrr.integration.clients[0].enabled=true
mrr.integration.clients[0].allowed-ips[0]=10.10.20.15
```

`JWT_SECRET_KEY` 和外部系统 HMAC 密钥必须使用两个不同的随机值，并且长度不少于 32 个字符。

默认不开放浏览器跨域。只有确实存在跨域前端时才配置精确 Origin：

```properties
mrr.cors.allowed-origins[0]=https://his.example.internal
```

服务器到服务器的票据请求不受浏览器 CORS 限制。

## 2. 支持的访问条件

请求体中的所有条件采用**并集**语义，MRR 将查询结果解析为精确的病案号与上架号组合并去重。

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

可以只传其中一种形式，也可以混合传入：

- `idCard`：授权该身份证对应的全部病案；
- `bah`：单个病案号；
- `sjh`：单个唯一上架号；
- `bahs`：多个病案号；
- `sjhs`：多个上架号；
- `archives`：多个精确的病案号、上架号组合；
- `allowDownload`：是否允许服务器批量导出 ZIP，默认 `false`。

病案号大于等于 `10000000` 时不保证唯一，必须通过 `archives` 同时提供上架号。`bahs` 与 `sjhs` 是独立的并集条件，不按数组下标自动配对；需要成对指定时使用 `archives`。

## 3. 申请一次性票据

```http
POST /api/v1/integration/archive/tickets
X-MRR-Client-Id: his-system
X-MRR-Timestamp: 1784383200
X-MRR-Nonce: 7fd72b36-d39a-4ea5-80cb-aafeeaed1815
X-MRR-Signature: 16进制HMAC签名
Content-Type: application/json
```

服务端签名原文：

```text
POST
/api/v1/integration/archive/tickets
{timestamp}
{nonce}
{SHA256(rawBody)}
```

签名算法：

```text
HMAC-SHA256(clientSecret, canonicalText)
```

签名必须针对实际发送的原始 JSON 字节计算。不要在签名后再次格式化 JSON。

成功响应：

```json
{
  "code": 200,
  "data": {
    "ticket": "一次性随机票据",
    "launchUrl": "https://mrr.example/archive/external?ticket=...",
    "expiresIn": 90,
    "archiveCount": 6
  }
}
```

外部系统只需要让浏览器打开 `launchUrl`。MRR 会立即兑换票据，写入短期 HttpOnly Cookie，并从地址栏移除票据。

## 4. Java 签名示例

```java
String timestamp = String.valueOf(Instant.now().getEpochSecond());
String nonce = UUID.randomUUID().toString();
String bodyHash = HexFormat.of().formatHex(
    MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8))
);
String canonical = "POST\n/api/v1/integration/archive/tickets\n"
    + timestamp + "\n" + nonce + "\n" + bodyHash;

Mac mac = Mac.getInstance("HmacSHA256");
mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
String signature = HexFormat.of().formatHex(
    mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8))
);
```

## 5. Node.js 签名示例

```js
import crypto from 'node:crypto'

const timestamp = Math.floor(Date.now() / 1000).toString()
const nonce = crypto.randomUUID()
const body = JSON.stringify(payload)
const bodyHash = crypto.createHash('sha256').update(body, 'utf8').digest('hex')
const canonical = `POST\n/api/v1/integration/archive/tickets\n${timestamp}\n${nonce}\n${bodyHash}`
const signature = crypto.createHmac('sha256', secret).update(canonical, 'utf8').digest('hex')
```

## 6. 前端认证接口测试台

管理员登录后，从菜单进入：

```text
运维 → 认证接口测试
```

页面路径：

```text
/auth-test
```

该页面要求 `user:manage` 权限，支持：

- 测试用户名、密码登录并查看完整响应；
- 本地解析 JWT Header 与 Payload；
- 测试 `/api/v1/auth/me` 和 `/api/v1/auth/logout`；
- 自定义受保护 API 的方法、路径、请求头、请求体和 Bearer Token；
- 编辑身份证、单个或多个病案号、单个或多个上架号以及精确病案组合；
- 查看原始 JSON、SHA-256、Canonical Text 和 HMAC-SHA256；
- 在“自动重新签名”和“手工签名”之间切换；
- 测试错误签名、过期时间戳、重复 nonce、无权限 Token 和已撤销 Token；
- 创建并打开一次性 `launchUrl`；
- 查看当前页面生命周期内的请求记录。

测试台使用独立的 Axios 实例，故意测试 `401` 或 `403` 不会触发后台全局自动退出。登录接口返回的测试 Token 只有点击“应用到当前会话”后才会替换当前登录会话。

HMAC Secret 只保存在页面内存，不写入 `localStorage`、`sessionStorage` 或数据库。为了兼容内网 HTTP 部署，浏览器无法使用 `crypto.subtle` 时会自动回退到纯 JavaScript SHA-256/HMAC 实现。

注意：

- 正式 HIS、EMR 接入必须在外部系统服务端完成签名，不能依赖该前端测试页面；
- 浏览器发起票据请求时，来源 IP 是管理员电脑或反向代理识别到的 IP，需要位于对应客户端 IP 白名单内；
- 票据 TTL、会话 TTL、客户端密钥和 IP 白名单属于服务端部署配置，不允许从测试页面修改；
- 不要在共享电脑、录屏或截图环境中输入生产 HMAC 密钥。

## 7. 安全限制

- 票据默认 90 秒有效且只能兑换一次；
- 外部影像会话默认 30 分钟有效；
- 时间戳默认允许前后 5 分钟偏差；
- nonce 在有效窗口内不能重复；
- 可为每个外部客户端设置来源 IP 白名单；
- 外部页面只能访问票据解析出的精确病案集合；
- 修改影像分类、用户管理、统计、设置和日志等后台接口不会向外部会话开放；
- 身份证号不会进入浏览器跳转 URL；
- 单次票据默认最多授权 100 份病案；
- 批量下载在前端与后端同时校验 `allowDownload`；
- 图片响应与 ZIP 导出使用私有缓存策略。

## 8. 数据库存储与审计

Flyway 会创建以下表：

- `mr_external_archive_nonce`：持久化 nonce 防重放记录；
- `mr_external_archive_ticket`：一次性票据状态；
- `mr_external_archive_session`：短期外部会话；
- `mr_external_archive_access_log`：票据、会话、病案、图片和下载审计。

数据库只保存票据和会话令牌的 SHA-256 摘要，不保存可直接使用的令牌明文。过期 nonce 会及时删除，过期票据和会话保留 7 天后清理；访问审计日志不会随临时凭证一起删除。

## 9. 反向代理

为了让返回的 `launchUrl` 使用正确的 HTTPS 域名，Nginx 需要传递：

```nginx
proxy_set_header Host $host;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
```

生产环境必须使用 HTTPS，否则外部会话 Cookie 无法获得完整的传输保护。

## 10. 本地联调模拟器

仓库提供 `scripts/simulate-external-archive.py`，使用 Python 标准库模拟外部系统的完整访问链路：申请票据、兑换会话、读取授权上下文、读取影像列表、校验单张影像重定向、可选下载 ZIP，最后退出会话。

```powershell
python scripts/simulate-external-archive.py `
  --base-url http://localhost:18045 `
  --client-id his-system `
  --secret "替换为外部客户端密钥" `
  --external-user-id HIS-USER-10086 `
  --archive 789508:123456 `
  --allow-download `
  --download .\tmp\789508-123456.zip
```

也可以使用 `--bah`、`--sjh`、`--id-card` 作为访问条件；同一个参数可重复传入。模拟器会复用会话 Cookie，并在任一步返回非成功响应时以非零退出码结束。`--download` 只应在同时指定 `--allow-download` 时使用。
