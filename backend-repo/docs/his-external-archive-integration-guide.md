# HIS、EMR 调阅 MRR 影像档案袋接入指南

本文面向 HIS、EMR、电子病历门户和医院信息集成平台开发人员，说明如何使用 Python、Java 或 C# 在外部系统后端申请 MRR 一次性影像访问票据，并将影像档案袋安全地交给当前用户浏览器打开。

相关内容：

- [外部访问协议速查](./external-archive-integration.md)
- [MRR 认证测试台配置与模拟](./external-archive-auth-test-guide.md)
- [Python、Java、C# 可运行示例](../examples/his-integration/README.md)

## 1. 推荐架构

正式接入必须由 HIS、EMR **后端服务器**持有 HMAC Secret 并调用 MRR。

```text
医生、护士工作站
        ↓
HIS 前端、WPF、WinForms 或浏览器
        ↓ 调用 HIS 自己的后端
HIS、EMR 后端服务器
        ↓ 使用 HMAC Secret 签名
MRR /api/v1/integration/archive/tickets
        ↓ 返回一次性 launchUrl
HIS 前端打开 launchUrl
        ↓
MRR 外部影像档案袋
```

不要让大量终端直接保存 Secret 并请求 MRR。即使 HIS 是 C# WPF 或 WinForms 客户端，也应先调用 HIS 自己的后端，再由后端返回 `launchUrl`。

## 2. MRR 服务端配置

在 MRR 实际加载的 `application-secrets.properties` 中配置：

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

### 2.1 allowed-ips

`allowed-ips` 填写发起 Ticket 请求的 HIS、EMR 后端服务器 IP，不是所有医生电脑 IP。

单台 HIS 后端：

```properties
mrr.integration.clients[0].allowed-ips[0]=10.10.20.15
```

多台后端节点：

```properties
mrr.integration.clients[0].allowed-ips[0]=10.10.20.15
mrr.integration.clients[0].allowed-ips[1]=10.10.20.16
mrr.integration.clients[0].allowed-ips[2]=10.10.20.17
```

如果所有 HIS 节点通过固定网关访问 MRR，可以只填写网关出口 IP。

当前版本支持精确 IP、`*` 和空白名单，尚不支持 `10.10.20.0/24` 这样的 CIDR。生产环境不建议使用 `*` 或空白名单。

### 2.2 生成 HMAC Secret

推荐生成 32 个随机字节，保存为 64 个十六进制字符。

PowerShell：

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$rng.Dispose()
$secret = -join ($bytes | ForEach-Object { $_.ToString("x2") })
$secret
```

OpenSSL：

```bash
openssl rand -hex 32
```

同一个 Secret 分别写入：

1. MRR 的 `mrr.integration.clients[n].secret`；
2. HIS、EMR 后端的 Secret Manager、环境变量或受保护配置文件。

不要把 Secret 放入前端源码、桌面安装包、URL、Git 仓库或普通日志。

## 3. HIS 用户调阅流程

### 3.1 HIS 前端请求自己的后端

用户在 HIS 中点击“调阅影像”时，HIS 前端只把病案参数提交给 HIS 后端：

```http
POST /api/mrr/archive-launch
Content-Type: application/json
Authorization: HIS 自己的登录凭证
```

```json
{
  "bah": "789508",
  "sjh": "123456"
}
```

HIS 后端必须从当前登录会话取得操作人员 ID，例如 `DOC-10086`，并将其作为 MRR 的 `externalUserId`。不要直接相信前端提交的操作人员 ID。

### 3.2 HIS 后端申请 MRR Ticket

```http
POST /api/v1/integration/archive/tickets
Content-Type: application/json; charset=utf-8
X-MRR-Client-Id: his-system
X-MRR-Timestamp: 1784383200
X-MRR-Nonce: 7fd72b36-d39a-4ea5-80cb-aafeeaed1815
X-MRR-Signature: 64位十六进制HMAC签名
```

推荐请求体：

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

### 3.3 HIS 后端返回 launchUrl

MRR 成功响应：

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

HIS 后端通常只向前端返回：

```json
{
  "launchUrl": "https://mrr.example.internal/archive/external?ticket=..."
}
```

### 3.4 HIS 前端打开影像档案袋

浏览器前端：

```js
const response = await fetch('/api/mrr/archive-launch', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ bah, sjh }),
})

const result = await response.json()
window.open(result.launchUrl, '_blank', 'noopener,noreferrer')
```

WPF、WinForms：

```csharp
Process.Start(new ProcessStartInfo
{
    FileName = launchUrl,
    UseShellExecute = true,
});
```

Ticket 默认只有 90 秒有效，前端收到后应立即打开。

## 4. 请求参数

请求体中的定位条件采用并集语义，MRR 最终转换为精确病案号和上架号组合并去重。

| 参数 | 必填 | 作用 |
|---|---:|---|
| `externalUserId` | 是 | 当前 HIS、EMR 操作人员唯一 ID，用于审计 |
| `idCard` | 条件之一 | 授权身份证对应的全部病案 |
| `bah` | 条件之一 | 单个病案号 |
| `sjh` | 条件之一 | 单个唯一上架号 |
| `bahs` | 条件之一 | 多个病案号 |
| `sjhs` | 条件之一 | 多个上架号 |
| `archives` | 条件之一 | 多个精确病案号、上架号组合 |
| `allowDownload` | 否 | 是否允许 ZIP 下载，默认 `false` |

精确调阅一份病案：

```json
{
  "externalUserId": "DOC-10086",
  "archives": [
    { "bah": "789508", "sjh": "123456" }
  ],
  "allowDownload": false
}
```

按身份证调阅全部病案：

```json
{
  "externalUserId": "DOC-10086",
  "idCard": "330000000000000000",
  "allowDownload": false
}
```

同时授权多份病案：

```json
{
  "externalUserId": "DOC-10086",
  "archives": [
    { "bah": "10000001", "sjh": "20000001" },
    { "bah": "10000002", "sjh": "20000002" }
  ],
  "allowDownload": false
}
```

病案号大于等于 `10000000` 时必须同时提供上架号。`bahs` 与 `sjhs` 不按数组下标自动配对，需要配对时使用 `archives`。

## 5. HMAC 签名规则

固定路径：

```text
/api/v1/integration/archive/tickets
```

签名步骤：

1. 将请求体序列化为最终实际发送的 UTF-8 JSON 字节；
2. 计算 `bodyHash = lowercaseHex(SHA256(rawBodyBytes))`；
3. 构造 Canonical Text；
4. 使用 Secret 计算 HMAC-SHA256；
5. 使用同一份 `rawBodyBytes` 发送请求。

Canonical Text：

```text
POST
/api/v1/integration/archive/tickets
{timestamp}
{nonce}
{bodyHash}
```

实际使用 `\n` 连接，不增加最后一行换行。

签名：

```text
signature = lowercaseHex(
  HMAC-SHA256(
    UTF8(secret),
    UTF8(canonicalText)
  )
)
```

必须保证：

- JSON 只序列化一次；
- 签名后不再格式化或修改 JSON；
- 请求头中的时间戳和 nonce 与签名内容完全一致；
- 路径大小写和斜杠完全一致；
- 64 位十六进制 Secret 作为普通 UTF-8 字符串使用，不要再次解码成二进制。

## 6. Python HIS 后端接入

示例：

```text
backend-repo/examples/his-integration/python/mrr_archive_ticket_client.py
```

只使用 Python 标准库。

设置环境变量：

```powershell
$env:MRR_BASE_URL="https://mrr.example.internal"
$env:MRR_CLIENT_ID="his-system"
$env:MRR_HMAC_SECRET="替换为与MRR一致的Secret"
```

运行：

```powershell
python backend-repo/examples/his-integration/python/mrr_archive_ticket_client.py `
  DOC-10086 `
  789508 `
  123456
```

输出最后一行是 `launchUrl`。

Python Web 服务调用：

```python
from mrr_archive_ticket_client import MrrArchiveTicketClient

mrr_client = MrrArchiveTicketClient(
    base_url=settings.MRR_BASE_URL,
    client_id=settings.MRR_CLIENT_ID,
    secret=settings.MRR_HMAC_SECRET,
)


def create_archive_launch_url(current_user_id: str, bah: str, sjh: str) -> str:
    return mrr_client.create_launch_url({
        "externalUserId": current_user_id,
        "archives": [{"bah": bah, "sjh": sjh}],
        "allowDownload": False,
    })
```

完整 Session、影像列表、下载和退出模拟使用：

```text
backend-repo/scripts/simulate-external-archive.py
```

## 7. Java HIS 后端接入

示例目录：

```text
backend-repo/examples/his-integration/java
```

使用 Java 21、`java.net.http.HttpClient` 和 Jackson。

设置环境变量：

```powershell
$env:MRR_BASE_URL="https://mrr.example.internal"
$env:MRR_CLIENT_ID="his-system"
$env:MRR_HMAC_SECRET="替换为与MRR一致的Secret"
```

运行：

```powershell
cd backend-repo/examples/his-integration/java
mvn -q compile exec:java -Dexec.args="DOC-10086 789508 123456"
```

Spring Boot Service：

```java
@Service
public class HisArchiveService {

    private final MrrArchiveTicketClient client;

    public HisArchiveService(
            @Value("${mrr.base-url}") String baseUrl,
            @Value("${mrr.client-id}") String clientId,
            @Value("${mrr.hmac-secret}") String secret
    ) {
        this.client = new MrrArchiveTicketClient(baseUrl, clientId, secret);
    }

    public String createLaunchUrl(String currentUserId, String bah, String sjh)
            throws Exception {
        var request = MrrArchiveTicketClient.TicketRequest.exactArchive(
                currentUserId,
                bah,
                sjh,
                false
        );
        return client.createLaunchUrl(request);
    }
}
```

Controller：

```java
@RestController
@RequestMapping("/api/mrr")
public class HisArchiveController {

    private final HisArchiveService service;

    public HisArchiveController(HisArchiveService service) {
        this.service = service;
    }

    @PostMapping("/archive-launch")
    public Map<String, String> createLaunch(
            Authentication authentication,
            @RequestBody ArchiveLaunchRequest request
    ) throws Exception {
        String currentUserId = authentication.getName();
        String launchUrl = service.createLaunchUrl(
                currentUserId,
                request.bah(),
                request.sjh()
        );
        return Map.of("launchUrl", launchUrl);
    }

    public record ArchiveLaunchRequest(String bah, String sjh) {
    }
}
```

## 8. C# HIS 后端接入

示例目录：

```text
backend-repo/examples/his-integration/csharp
```

使用 .NET 8、`HttpClient`、`System.Text.Json` 和 `HMACSHA256`。

设置环境变量：

```powershell
$env:MRR_BASE_URL="https://mrr.example.internal"
$env:MRR_CLIENT_ID="his-system"
$env:MRR_HMAC_SECRET="替换为与MRR一致的Secret"
```

运行：

```powershell
cd backend-repo/examples/his-integration/csharp
dotnet run -- DOC-10086 789508 123456
```

ASP.NET Core 非敏感配置：

```json
{
  "Mrr": {
    "BaseUrl": "https://mrr.example.internal",
    "ClientId": "his-system"
  }
}
```

Secret 使用环境变量：

```powershell
$env:Mrr__HmacSecret="替换为与MRR一致的Secret"
```

`Program.cs` 注册：

```csharp
builder.Services.AddHttpClient("MrrArchive", httpClient =>
{
    httpClient.Timeout = TimeSpan.FromSeconds(30);
});

builder.Services.AddTransient(services =>
{
    IConfiguration configuration = services.GetRequiredService<IConfiguration>();
    IHttpClientFactory factory = services.GetRequiredService<IHttpClientFactory>();

    return new MrrArchiveTicketClient(
        factory.CreateClient("MrrArchive"),
        configuration["Mrr:BaseUrl"]
            ?? throw new InvalidOperationException("缺少 Mrr:BaseUrl"),
        configuration["Mrr:ClientId"]
            ?? throw new InvalidOperationException("缺少 Mrr:ClientId"),
        configuration["Mrr:HmacSecret"]
            ?? throw new InvalidOperationException("缺少 Mrr:HmacSecret"));
});
```

最小 API：

```csharp
app.MapPost("/api/mrr/archive-launch", async (
    ArchiveLaunchInput input,
    ClaimsPrincipal user,
    MrrArchiveTicketClient client,
    CancellationToken cancellationToken) =>
{
    string currentUserId = user.Identity?.Name
        ?? throw new UnauthorizedAccessException("当前 HIS 用户未登录");

    var request = TicketRequest.ExactArchive(
        currentUserId,
        input.Bah,
        input.Sjh,
        allowDownload: false);

    string launchUrl = await client.CreateLaunchUrlAsync(
        request,
        cancellationToken);

    return Results.Ok(new { launchUrl });
}).RequireAuthorization();

public sealed record ArchiveLaunchInput(string Bah, string Sjh);
```

WPF、WinForms 只调用这个 HIS 后端接口并打开返回的 `launchUrl`，不要把 MRR Secret 编译进桌面客户端。

## 9. 正式跳转与完整接口模拟

正式 HIS 调阅通常只需要：

```text
申请 Ticket
    ↓
取得 launchUrl
    ↓
浏览器打开 launchUrl
```

浏览器进入 MRR 后自动：

1. 调用 `/api/v1/external/archive/session` 兑换 Ticket；
2. 写入短期 HttpOnly Cookie；
3. 从地址栏移除 Ticket；
4. 读取授权上下文和影像列表。

HIS 后端不需要管理 MRR 外部 Session Cookie。

只有自动化联调时才需要模拟 Ticket 兑换、Cookie、影像列表、下载和退出。

## 10. 常见错误

### 503 外部系统集成未启用

将：

```properties
mrr.integration.enabled=true
```

保存并重启 MRR 后端。

### 503 外部系统客户端密钥未配置

对应 Client ID 的 `secret` 为空，或者修改的配置文件没有被实际加载。

### 401 外部系统客户端无效

检查 Client ID 是否完全一致、客户端是否启用、`clients[n]` 索引是否正确。

### 401 外部系统签名无效

检查 Secret、JSON 原始字节、路径、时间戳和 nonce。最常见原因是签名后再次序列化 JSON，或 HIS 与 MRR 使用了不同 Secret。

### 401 签名时间戳已过期

检查 HIS 服务器和 MRR 服务器时间，建议使用统一 NTP 时间源。

### 403 来源 IP 不在白名单

填写 MRR 实际识别到的 HIS 后端来源 IP。

经 Nginx 代理时传递：

```nginx
proxy_set_header Host $host;
proxy_set_header X-Real-IP $remote_addr;
# 单层本机 Nginx 必须覆盖客户端自行提交的同名头，防止伪造来源 IP。
proxy_set_header X-Forwarded-For $remote_addr;
proxy_set_header X-Forwarded-Proto $scheme;
```

不要使用 `$proxy_add_x_forwarded_for` 追加客户端自带的转发链。当前正式架构只有一层本机 Nginx，应以 TCP 直接来源 `$remote_addr` 覆盖该请求头。

### 404 未定位到可访问病案

HMAC 已通过，但身份证、病案号或上架号没有匹配到病案数据。

### 409 nonce 已使用

每次请求生成新的时间戳、nonce 和签名。失败重试时也不要复用旧 nonce。

## 11. 生产安全要求

- 每个外部系统使用独立 Client ID 和 Secret；
- Secret 只保存在外部系统后端；
- `externalUserId` 来自 HIS 服务端登录上下文；
- 默认 `allowDownload=false`；
- MRR 与 HIS 之间使用 HTTPS；
- `allowed-ips` 只允许 HIS 后端或受控网关；
- 不记录 Secret、Ticket、完整 `launchUrl` 或患者身份证明文；
- Ticket 返回后立即交给浏览器打开；
- 对 401、403、404、409、503 做结构化监控；
- 服务器时间使用 NTP 同步；
- Secret 轮换时先新增新 Client ID，切换完成后再停用旧 Client ID。

## 12. 接入验收清单

### MRR 管理员

- [ ] 集成功能已启用；
- [ ] Client ID、Secret、allowed-ips 已配置；
- [ ] MRR 已重启；
- [ ] 认证测试台五项检查通过；
- [ ] 正常请求返回 200；
- [ ] 错误签名返回 401；
- [ ] 重复 nonce 返回 409；
- [ ] 审计日志记录外部用户和病案访问。

### HIS、EMR 开发人员

- [ ] Secret 未进入前端或桌面客户端；
- [ ] `externalUserId` 来自服务端登录会话；
- [ ] JSON 只序列化一次；
- [ ] 每次请求生成新时间戳和 nonce；
- [ ] 成功后只把 `launchUrl` 返回前端；
- [ ] 前端立即打开 `launchUrl`；
- [ ] 对 401、403、404、409、503 提供错误处理；
- [ ] 生产 Secret 存储在安全配置中。
