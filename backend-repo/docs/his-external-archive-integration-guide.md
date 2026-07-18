# HIS、EMR 调阅 MRR 影像档案袋接入指南

本文面向 HIS、EMR、电子病历门户和医院信息集成平台的开发人员，说明如何使用 Python、Java 或 C# 在外部系统后端申请 MRR 一次性影像访问票据，并把影像档案袋安全地交给当前用户浏览器打开。

相关文档：

- [外部系统访问影像档案袋](./external-archive-integration.md)
- [认证测试台：外部影像票据配置与模拟](./external-archive-auth-test-guide.md)

可直接运行的示例：

- [Python 示例](../examples/his-integration/python/mrr_archive_ticket_client.py)
- [Java 示例](../examples/his-integration/java/MrrArchiveTicketClient.java)
- [C# 示例](../examples/his-integration/csharp/MrrArchiveTicketClient.cs)

## 1. 推荐架构

正式接入必须由 HIS、EMR 的**服务端**保存 HMAC Secret 并调用 MRR。

```text
医生、护士工作站
        │
        │ 当前 HIS 登录会话、病案号、上架号
        ▼
HIS 前端、WPF、WinForms 或浏览器
        │
        │ 调用 HIS 自己的后端接口
        ▼
HIS、EMR 后端服务器
        │
        │ 使用 HMAC Secret 签名
        │ POST /api/v1/integration/archive/tickets
        ▼
MRR 后端
        │
        │ 返回一次性 launchUrl
        ▼
HIS 前端打开 launchUrl
        │
        ▼
MRR 外部影像档案袋
```

不要采用下面的方式：

```text
100 台工作站分别保存 HMAC Secret
        ↓
每台工作站直接请求 MRR
```

这样会导致 Secret 分发到大量终端，难以轮换，也容易从浏览器、WPF、WinForms 安装目录、日志或内存中泄露。

正确方式通常只需要把 HIS 后端服务器的一个或几个 IP 写入 MRR `allowed-ips`。

## 2. MRR 服务端准备

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

### 2.1 allowed-ips 填写规则

填写发起 Ticket 请求的 HIS、EMR 后端服务器 IP，而不是所有医生电脑 IP。

单台 HIS 后端：

```properties
mrr.integration.clients[0].allowed-ips[0]=10.10.20.15
```

HIS 后端集群：

```properties
mrr.integration.clients[0].allowed-ips[0]=10.10.20.15
mrr.integration.clients[0].allowed-ips[1]=10.10.20.16
mrr.integration.clients[0].allowed-ips[2]=10.10.20.17
```

如果所有 HIS 节点通过固定网关访问 MRR，可以只填写网关出口 IP。

当前实现支持：

- 精确 IPv4 或 IPv6 地址；
- `*`；
- 空白名单。

当前实现尚不支持 `10.10.20.0/24` 这样的 CIDR。生产环境不建议使用 `*` 或空白名单。

### 2.2 生成 HMAC Secret

推荐生成 32 个随机字节，保存为 64 个十六进制字符。

Windows PowerShell：

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

同一个 Secret 必须分别写入：

1. MRR 的 `mrr.integration.clients[n].secret`；
2. HIS、EMR 后端的安全配置或 Secret Manager。

不要把 Secret 放到：

- Vue、React、JavaScript 前端源码；
- WPF、WinForms 安装包；
- URL 参数；
- Git 仓库；
- 普通业务日志；
- 数据库普通明文字段。

## 3. HIS 调阅流程

### 3.1 用户点击“调阅影像”

HIS 已经知道当前操作人员和当前病案，例如：

```text
externalUserId = DOC-10086
bah            = 789508
sjh            = 123456
```

其中：

- `externalUserId` 是当前医生、护士或 HIS 用户的唯一标识，用于审计；
- `bah` 是病案号；
- `sjh` 是上架号。

### 3.2 HIS 前端调用自己的后端

例如：

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

HIS 后端必须从当前登录上下文取得 `externalUserId`。不要相信前端任意提交的操作人员 ID。

### 3.3 HIS 后端申请 MRR Ticket

```http
POST /api/v1/integration/archive/tickets
Content-Type: application/json; charset=utf-8
X-MRR-Client-Id: his-system
X-MRR-Timestamp: 1784383200
X-MRR-Nonce: 7fd72b36-d39a-4ea5-80cb-aafeeaed1815
X-MRR-Signature: 64位十六进制HMAC签名
```

请求体示例：

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

### 3.4 MRR 返回 launchUrl

```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "ticket": "一次性票据",
    "launchUrl": "https://mrr.example.internal/archive/external?ticket=...",
    "expiresIn": 90,
    "archiveCount": 1
  }
}
```

HIS 后端通常只需要把 `launchUrl` 返回给 HIS 前端：

```json
{
  "launchUrl": "https://mrr.example.internal/archive/external?ticket=..."
}
```

### 3.5 HIS 前端打开影像档案袋

浏览器系统：

```js
const response = await fetch('/api/mrr/archive-launch', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ bah, sjh }),
})

const result = await response.json()
window.open(result.launchUrl, '_blank', 'noopener,noreferrer')
```

WPF、WinForms 客户端：

```csharp
Process.Start(new ProcessStartInfo
{
    FileName = launchUrl,
    UseShellExecute = true,
});
```

C# 桌面客户端只接收 `launchUrl`，不应直接保存 MRR HMAC Secret。

## 4. 请求参数

请求体所有定位条件采用并集语义，MRR 最终转换成精确的病案号和上架号组合并去重。

| 参数 | 必填 | 说明 |
|---|---:|---|
| `externalUserId` | 是 | 当前 HIS、EMR 操作人员唯一 ID，用于审计 |
| `idCard` | 条件之一 | 授权身份证对应的全部病案 |
| `bah` | 条件之一 | 单个病案号 |
| `sjh` | 条件之一 | 单个唯一上架号 |
| `bahs` | 条件之一 | 多个病案号 |
| `sjhs` | 条件之一 | 多个上架号 |
| `archives` | 条件之一 | 多个精确的病案号、上架号组合 |
| `allowDownload` | 否 | 是否允许 ZIP 下载，默认 `false` |

### 4.1 精确调阅一份病案

推荐使用：

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

### 4.2 按身份证调阅全部病案

```json
{
  "externalUserId": "DOC-10086",
  "idCard": "330000000000000000",
  "allowDownload": false
}
```

### 4.3 同时授权多份病案

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

病案号大于等于 `10000000` 时必须同时提供上架号。

`bahs` 和 `sjhs` 是两个独立的并集条件，不会按照数组下标自动配对。需要配对时必须使用 `archives`。

## 5. HMAC 签名规则

### 5.1 固定接口路径

```text
/api/v1/integration/archive/tickets
```

参与签名的是路径，不包含域名，也不包含查询参数。

### 5.2 生成原始 JSON

请求体必须先序列化为最终实际发送的 UTF-8 字节。

例如：

```text
{"externalUserId":"DOC-10086","archives":[{"bah":"789508","sjh":"123456"}],"allowDownload":false}
```

### 5.3 计算 Body Hash

```text
bodyHash = lowercaseHex(SHA256(rawBodyBytes))
```

### 5.4 构造 Canonical Text

```text
POST
/api/v1/integration/archive/tickets
{timestamp}
{nonce}
{bodyHash}
```

实际字符串使用 `\n` 连接，不要增加最后一行换行。

### 5.5 计算签名

```text
signature = lowercaseHex(
  HMAC-SHA256(
    UTF8(secret),
    UTF8(canonicalText)
  )
)
```

必须保证：

- 签名使用的 JSON 字节与 HTTP 实际发送的字节完全一致；
- 签名后不能再次格式化 JSON；
- `timestamp` 请求头与参与签名的字符串完全一致；
- `nonce` 请求头与参与签名的字符串完全一致；
- 接口路径大小写和斜杠完全一致；
- Secret 按普通 UTF-8 字符串使用，不要再次把 64 位十六进制文本解码成二进制。

## 6. Python HIS 后端接入

示例文件：

```text
backend-repo/examples/his-integration/python/mrr_archive_ticket_client.py
```

只使用 Python 标准库，不需要安装第三方依赖。

### 6.1 设置环境变量

PowerShell：

```powershell
$env:MRR_BASE_URL="https://mrr.example.internal"
$env:MRR_CLIENT_ID="his-system"
$env:MRR_HMAC_SECRET="替换为与MRR一致的Secret"
```

Linux：

```bash
export MRR_BASE_URL='https://mrr.example.internal'
export MRR_CLIENT_ID='his-system'
export MRR_HMAC_SECRET='替换为与MRR一致的Secret'
```

### 6.2 运行示例

```powershell
python backend-repo/examples/his-integration/python/mrr_archive_ticket_client.py `
  DOC-10086 `
  789508 `
  123456
```

输出：

```text
archiveCount=1
expiresIn=90
https://mrr.example.internal/archive/external?ticket=...
```

### 6.3 集成到 Python Web 服务

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

生产项目应从 Secret Manager、环境变量或受保护的配置文件读取 Secret。

仓库还提供完整接口模拟器：

```text
backend-repo/scripts/simulate-external-archive.py
```

该脚本会继续兑换 Session、读取影像列表和退出会话，适合联调，不是正式 HIS 浏览器跳转的必需步骤。

## 7. Java HIS 后端接入

示例目录：

```text
backend-repo/examples/his-integration/java
```

示例使用 Java 21、`java.net.http.HttpClient` 和 Jackson。

### 7.1 设置环境变量

PowerShell：

```powershell
$env:MRR_BASE_URL="https://mrr.example.internal"
$env:MRR_CLIENT_ID="his-system"
$env:MRR_HMAC_SECRET="替换为与MRR一致的Secret"
```

### 7.2 运行示例

```powershell
cd backend-repo/examples/his-integration/java
mvn -q compile exec:java -Dexec.args="DOC-10086 789508 123456"
```

### 7.3 Spring Boot Service 使用

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

Controller 示例：

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

`externalUserId` 应来自服务端认证上下文，不应直接采用前端提交值。

## 8. C# HIS 后端接入

示例目录：

```text
backend-repo/examples/his-integration/csharp
```

示例使用 .NET 8 和系统自带的 `HttpClient`、`System.Text.Json`、`HMACSHA256`。

### 8.1 设置环境变量

PowerShell：

```powershell
$env:MRR_BASE_URL="https://mrr.example.internal"
$env:MRR_CLIENT_ID="his-system"
$env:MRR_HMAC_SECRET="替换为与MRR一致的Secret"
```

### 8.2 运行示例

```powershell
cd backend-repo/examples/his-integration/csharp
dotnet run -- DOC-10086 789508 123456
```

### 8.3 ASP.NET Core 注册服务

`appsettings.json` 不建议直接提交生产 Secret。可以只保存非敏感配置：

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

`Program.cs`：

```csharp
builder.Services.AddHttpClient<MrrArchiveTicketClient>((services, httpClient) =>
{
    httpClient.Timeout = TimeSpan.FromSeconds(30);
});

builder.Services.AddTransient(services =>
{
    IConfiguration configuration = services.GetRequiredService<IConfiguration>();
    IHttpClientFactory factory = services.GetRequiredService<IHttpClientFactory>();
    return new MrrArchiveTicketClient(
        factory.CreateClient(nameof(MrrArchiveTicketClient)),
        configuration["Mrr:BaseUrl"]!,
        configuration["Mrr:ClientId"]!,
        configuration["Mrr:HmacSecret"]!);
});
```

最小 API 示例：

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

### 8.4 WPF、WinForms HIS

推荐路线：

```text
WPF、WinForms
    ↓ 调用 HIS 自己的 ASP.NET Core 或其他后端
HIS 后端
    ↓ 使用 Secret 申请 Ticket
MRR
    ↓ 返回 launchUrl
WPF、WinForms 使用系统浏览器打开 launchUrl
```

不要把 `MRR_HMAC_SECRET` 编译进桌面客户端。

## 9. 完整接口模拟与正式跳转的区别

正式 HIS 调阅通常只需要：

```text
申请 Ticket
    ↓
取得 launchUrl
    ↓
让浏览器打开 launchUrl
```

浏览器进入 MRR 后会自动：

1. 使用 Ticket 调用 `/api/v1/external/archive/session`；
2. 将 Ticket 兑换为短期 HttpOnly Cookie；
3. 从地址栏移除 Ticket；
4. 调用 `/api/v1/external/archive/context`；
5. 读取授权病案影像。

外部 HIS 后端不需要自行管理这个 Cookie。

只有进行接口自动化测试时，才需要像 `simulate-external-archive.py` 一样模拟 Ticket 兑换、Cookie、影像列表和退出流程。

## 10. 常见错误

### 503 外部系统集成未启用

```properties
mrr.integration.enabled=true
```

保存后重启 MRR 后端。

### 503 外部系统客户端密钥未配置

对应 Client ID 的 `secret` 为空，或者修改的配置文件没有被实际加载。

### 401 外部系统客户端无效

检查：

- `X-MRR-Client-Id` 与 MRR 配置是否完全相同；
- 对应客户端是否 `enabled=true`；
- 是否使用了错误的 `clients[n]` 索引。

### 401 外部系统签名无效

检查：

- HIS Secret 与 MRR Secret 是否逐字符一致；
- Secret 前后是否存在空格；
- 签名后是否再次序列化或格式化 JSON；
- 是否将十六进制 Secret 错误地再次解码；
- 路径是否精确为 `/api/v1/integration/archive/tickets`；
- 请求头时间戳、nonce 是否与签名内容一致。

### 401 签名时间戳已过期

检查 HIS 服务器和 MRR 服务器系统时间，建议使用统一 NTP 时间源。

### 403 当前来源 IP 不在白名单

`allowed-ips` 应填写 MRR 实际识别到的 HIS 后端来源 IP。

经过 Nginx 时，需要正确传递：

```nginx
proxy_set_header Host $host;
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
proxy_set_header X-Forwarded-Proto $scheme;
```

### 404 未定位到可访问病案

说明 HMAC 认证已经通过，但提交的身份证、病案号、上架号没有匹配到病案数据。

### 409 签名 nonce 已使用

每次请求必须生成新的 nonce。不要在失败重试时复用旧 nonce，应重新生成时间戳、nonce 和签名。

## 11. 生产安全要求

- 每个 HIS、EMR、门户系统使用独立 Client ID 和 Secret；
- Secret 只保存在外部系统服务端；
- 外部用户 ID 必须来自 HIS 服务端登录上下文；
- 默认使用 `allowDownload=false`；
- MRR 和 HIS 之间使用 HTTPS；
- `allowed-ips` 只允许 HIS 后端或受控网关；
- 不记录 Secret、完整签名原文、Ticket 和 launchUrl；
- Ticket 有效期很短，HIS 前端收到后应立即打开；
- HIS 后端调用失败时，不要自动无限重试；
- 轮换 Secret 时先增加新 Client ID，切换完成后再禁用旧 Client ID；
- 对 401、403、409、503 做结构化监控，但日志中脱敏患者身份证和 Ticket。

## 12. 接入验收清单

### MRR 管理员

- [ ] `mrr.integration.enabled=true`；
- [ ] Client ID 已配置且启用；
- [ ] Secret 使用 256 位随机值；
- [ ] allowed-ips 是 HIS 后端 IP；
- [ ] MRR 已重启；
- [ ] 认证测试台五项检查通过；
- [ ] 使用真实病案测试返回 HTTP 200；
- [ ] 错误签名返回 401；
- [ ] 重复 nonce 返回 409；
- [ ] 审计表能够记录外部用户和病案访问。

### HIS、EMR 开发人员

- [ ] Secret 不进入前端和桌面客户端；
- [ ] `externalUserId` 来自服务端登录会话；
- [ ] JSON 只序列化一次；
- [ ] 实际发送字节与签名字节一致；
- [ ] 每次请求生成新时间戳和 nonce；
- [ ] 成功后只把 launchUrl 返回前端；
- [ ] 前端立即打开 launchUrl；
- [ ] 对 401、403、404、409、503 提供明确错误处理；
- [ ] 服务器时间已与 NTP 同步；
- [ ] 生产 Secret 存储在安全配置中。
