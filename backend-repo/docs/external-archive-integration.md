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

`JWT_SECRET_KEY` 和外部系统 HMAC 密钥必须使用两个不同的随机值。

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
- `allowDownload`：是否在外部页面显示批量下载功能，默认 `false`。

病案号大于等于 `10000000` 时不保证唯一，必须通过 `archives` 同时提供上架号。

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

注意：签名必须针对实际发送的原始 JSON 字节计算。不要在签名后再次格式化 JSON。

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

## 6. 安全限制

- 票据默认 90 秒有效且只能兑换一次；
- 外部影像会话默认 30 分钟有效；
- 时间戳默认允许前后 5 分钟偏差；
- nonce 在有效窗口内不能重复；
- 可为每个外部客户端设置来源 IP 白名单；
- 外部页面只能访问票据解析出的精确病案集合；
- 修改影像分类、用户管理、统计、设置和日志等后台接口不会向外部会话开放；
- 身份证号不会进入浏览器跳转 URL；
- 单次票据默认最多授权 100 份病案。

## 7. 反向代理

为了让返回的 `launchUrl` 使用正确的 HTTPS 域名，Nginx 需要传递：

```nginx
proxy_set_header Host $host;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
```

生产环境必须使用 HTTPS，否则外部会话 Cookie 无法获得完整的传输保护。
