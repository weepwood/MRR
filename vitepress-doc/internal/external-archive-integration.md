# 外部系统影像接入

本页说明 HIS、EMR、电子病历门户如何调阅 MRR 影像档案袋。正式接入由外部系统后端使用 HMAC-SHA256 申请一次性 Ticket，浏览器只接收并打开 `launchUrl`。

完整协议与示例源码：

```text
backend-repo/docs/his-external-archive-integration-guide.md
backend-repo/examples/his-integration/python/
backend-repo/examples/his-integration/java/
backend-repo/examples/his-integration/csharp/
```

## 推荐架构

```text
医生、护士工作站
        ↓
HIS 前端、WPF、WinForms
        ↓ 调用 HIS 自己的后端
HIS、EMR 后端服务器
        ↓ HMAC-SHA256
MRR Ticket 接口
        ↓ launchUrl
浏览器打开外部影像档案袋
```

HMAC Secret 只能保存在 HIS、EMR 后端。不要放入浏览器源码、WPF、WinForms 安装包或 URL。

## MRR 配置

在实际部署使用的 `application-secrets.properties` 中配置：

```properties
mrr.integration.enabled=true
mrr.integration.ticket-ttl-seconds=90
mrr.integration.session-ttl-seconds=1800
mrr.integration.timestamp-tolerance-seconds=300
mrr.integration.max-archives-per-ticket=100

mrr.integration.clients[0].client-id=his-system
mrr.integration.clients[0].secret=替换为64位十六进制随机Secret
mrr.integration.clients[0].enabled=true
mrr.integration.clients[0].allowed-ips[0]=10.10.20.15
```

修改后必须重启 MRR 后端。

### allowed-ips

填写 HIS、EMR 后端服务器或固定网关出口 IP，不填写医生工作站 IP。

```properties
mrr.integration.clients[0].allowed-ips[0]=10.10.20.15
mrr.integration.clients[0].allowed-ips[1]=10.10.20.16
```

当前版本只支持精确 IP、`*` 和空白名单，不支持 CIDR。生产环境不建议使用 `*` 或空白名单。

## 请求体

推荐精确调阅病案：

```json
{
  "externalUserId": "DOC-10086",
  "archives": [
    {
      "bah": "789508",
      "sjh": "123456"
    }
  ],
  "allowDownload": false
}
```

`externalUserId` 必须来自 HIS 服务端当前登录会话，用于审计。

可使用的定位条件：

- `idCard`：身份证对应的全部病案；
- `bah`：单个病案号；
- `sjh`：单个上架号；
- `bahs`：多个病案号；
- `sjhs`：多个上架号；
- `archives`：病案号和上架号精确组合。

病案号大于等于 `10000000` 时必须同时提供上架号。`bahs` 和 `sjhs` 不按数组下标配对。

## 签名协议

请求：

```http
POST /api/v1/integration/archive/tickets
Content-Type: application/json; charset=utf-8
X-MRR-Client-Id: his-system
X-MRR-Timestamp: 1784383200
X-MRR-Nonce: 7fd72b36-d39a-4ea5-80cb-aafeeaed1815
X-MRR-Signature: 64位十六进制签名
```

Canonical Text：

```text
POST
/api/v1/integration/archive/tickets
{timestamp}
{nonce}
{lowercaseHex(SHA256(rawBodyBytes))}
```

签名：

```text
lowercaseHex(HMAC-SHA256(UTF8(secret), UTF8(canonicalText)))
```

请求体必须只序列化一次，签名和 HTTP 发送使用完全相同的 UTF-8 字节。

成功响应：

```json
{
  "code": 200,
  "data": {
    "ticket": "一次性票据",
    "launchUrl": "https://mrr.example.internal/archive/external?ticket=...",
    "expiresIn": 90,
    "archiveCount": 1
  }
}
```

HIS 后端只需把 `launchUrl` 返回前端，由前端立即打开。

## Python

示例：

```text
backend-repo/examples/his-integration/python/mrr_archive_ticket_client.py
```

```powershell
$env:MRR_BASE_URL="https://mrr.example.internal"
$env:MRR_CLIENT_ID="his-system"
$env:MRR_HMAC_SECRET="替换为与MRR一致的Secret"

python backend-repo/examples/his-integration/python/mrr_archive_ticket_client.py `
  DOC-10086 789508 123456
```

仅使用 Python 标准库。

## Java

示例：

```text
backend-repo/examples/his-integration/java
```

```powershell
$env:MRR_BASE_URL="https://mrr.example.internal"
$env:MRR_CLIENT_ID="his-system"
$env:MRR_HMAC_SECRET="替换为与MRR一致的Secret"

cd backend-repo/examples/his-integration/java
mvn -q compile exec:java -Dexec.args="DOC-10086 789508 123456"
```

Spring Boot 中调用 `MrrArchiveTicketClient.createLaunchUrl(...)`，并从认证上下文取得当前 HIS 用户 ID。

## C#

示例：

```text
backend-repo/examples/his-integration/csharp
```

```powershell
$env:MRR_BASE_URL="https://mrr.example.internal"
$env:MRR_CLIENT_ID="his-system"
$env:MRR_HMAC_SECRET="替换为与MRR一致的Secret"

cd backend-repo/examples/his-integration/csharp
dotnet run -- DOC-10086 789508 123456
```

ASP.NET Core 中注册 `MrrArchiveTicketClient`，WPF、WinForms 只调用 HIS 后端并使用系统浏览器打开返回的 `launchUrl`。

```csharp
Process.Start(new ProcessStartInfo
{
    FileName = launchUrl,
    UseShellExecute = true,
});
```

## 浏览器后续流程

浏览器打开 `launchUrl` 后，MRR 自动：

1. 将一次性 Ticket 兑换为短期 HttpOnly Cookie；
2. 保存当前外部 Session 的授权病案上下文；
3. 跳转到现有 `/archive` 影像档案袋页面，并从地址栏移除 Ticket；
4. 使用 `/api/v1/external/archive/**` 读取授权病案、图片和可选下载接口；
5. 只允许在 Ticket Session 的 `cases` 范围内切换病案。

实际跳转形式：

```text
/archive/external?ticket=...
        ↓
/archive?external=ticket&bah=...&sjh=...
```

正式 HIS 后端不需要管理 MRR 外部 Session Cookie。

### 外部页面界面边界

外部 Ticket 页面复用当前影像档案袋的缩略图、患者卡片、类型筛选和图片预览组件，不再维护独立的外部查看器。

所有外部调用均不提供 `search-card`：

- 不显示身份证号、病案号和上架号输入框；
- 不允许通过页面搜索 Ticket 未授权的其他病案；
- 不加载或展示内部账号的最近查询和收藏记录；
- 不允许修改图片类型；
- 不提供内部 PDF 导出入口；
- 下载按钮只在 Ticket 的 `allowDownload=true` 时可用。

浏览器刷新后若前端保存的外部 Session 已丢失，页面会重新请求外部 Context。Context 无效时直接显示外部访问错误，不会回退到开发者兼容模式。

## 认证测试台

管理员进入：

```text
运维 → 认证接口测试 → 外部影像票据
```

可检查：

- 集成功能是否启用；
- Client ID 是否存在；
- 服务端 Secret 是否配置；
- 来源 IP 是否在白名单；
- 页面 Secret 是否填写。

还可以模拟错误签名、过期时间戳和 nonce 重放。

## 常见错误

| 状态 | 原因 |
|---|---|
| `503 外部系统集成未启用` | `mrr.integration.enabled=false` 或未重启 |
| `503 客户端密钥未配置` | 对应 Client ID 的 Secret 为空 |
| `401 客户端无效` | Client ID 不一致或客户端已停用 |
| `401 签名无效` | Secret、JSON 原始字节、路径、时间戳或 nonce 不一致 |
| `401 时间戳已过期` | HIS 与 MRR 服务器时间偏差过大 |
| `403 IP 不在白名单` | MRR 识别的来源 IP 未配置 |
| `404 未定位到病案` | 病案参数没有匹配数据 |
| `409 nonce 已使用` | 重复使用同一个 nonce |

## 安全要求

- 每个外部系统使用独立 Client ID 和 Secret；
- 默认 `allowDownload=false`；
- MRR 与 HIS 之间使用 HTTPS；
- Secret、Ticket 和完整 `launchUrl` 不写入普通日志；
- Ticket 返回后立即打开；
- HIS 与 MRR 服务器使用 NTP 同步时间；
- 对票据创建、病案查看、图片查看和下载保留审计。
