# 外部系统影像接入

本文面向 HIS、EMR、电子病历门户和医院信息集成平台的开发、测试与运维人员，说明如何由外部系统后端申请 MRR 一次性影像访问 Ticket，并把限定范围的影像档案袋交给当前用户浏览器打开。

本文描述的是当前正式 HMAC Ticket 接口。旧版无 Token 开发者兼容模式不适合作为新系统接入方案。

::: danger 密钥边界
HMAC Secret 只能保存在 HIS、EMR 后端或受控密钥服务中。浏览器、Vue/React 源码、WPF/WinForms 安装包、URL、日志和 Git 仓库都不得保存 Secret。
:::

本文示例使用的用户标识、病案号、上架号、域名和密钥均为虚构值。

## 1. 调用架构与职责

```text
医生、护士工作站
        ↓ 使用 HIS 自身登录态
HIS 前端、WPF、WinForms
        ↓ 只调用 HIS 自己的后端
HIS、EMR 后端服务器
        ↓ HMAC-SHA256 申请一次性 Ticket
MRR /api/v1/integration/archive/tickets
        ↓ 返回短期 launchUrl
HIS 前端立即用浏览器打开 launchUrl
        ↓ Ticket 兑换为受限 HttpOnly Session
MRR 外部影像档案袋
```

各组件职责：

| 组件 | 必须完成 | 不得完成 |
|---|---|---|
| HIS 前端或桌面端 | 向 HIS 后端提交病案条件，打开返回的 `launchUrl` | 保存 Secret、生成 HMAC、信任前端传入的操作人员 ID |
| HIS、EMR 后端 | 校验本系统登录态，从认证上下文取得操作人员 ID，签名并调用 MRR | 把 Secret、Ticket 或完整 `launchUrl` 写入普通日志 |
| MRR 后端 | 校验 Client ID、IP、时间戳、nonce、HMAC 和病案范围，签发 Ticket 并审计 | 向外部 Session 开放未授权病案或管理接口 |
| 本机 Nginx | 代理 MRR、传递 Host 和来源信息、提供 HTTPS | 绕过 MRR 应用层鉴权 |

## 2. 接入前准备

正式联调前确认：

- MRR 与 HIS 后端之间可以通过 HTTPS 互访；
- HIS 与 MRR 服务器使用 NTP 同步时间；
- 每个外部系统分配独立 Client ID 和独立 Secret；
- Secret 由 32 个随机字节生成，并分别写入双方受保护配置；
- MRR `allowed-ips` 填写 HIS 后端或固定网关出口 IP，不是医生工作站 IP；
- 先使用测试病案验证，示例、日志、截图和工单不包含真实患者信息；
- 默认不授予下载权限，确需下载时单独评估并设置 `allowDownload=true`。

### 2.1 生成 HMAC Secret

推荐生成 32 个随机字节，并保存为 64 个十六进制字符。

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

配置中的 64 位十六进制 Secret 在当前协议中作为普通 UTF-8 文本参与 HMAC，不要再次把它解码成二进制。

### 2.2 配置 MRR

在 MRR 实际加载的 `application-secrets.properties` 中配置：

```properties
mrr.integration.enabled=true
mrr.integration.ticket-ttl-seconds=90
mrr.integration.session-ttl-seconds=1800
mrr.integration.timestamp-tolerance-seconds=300
mrr.integration.max-archives-per-ticket=100

mrr.integration.clients[0].client-id=his-system
mrr.integration.clients[0].secret=替换为独立的64位十六进制随机Secret
mrr.integration.clients[0].enabled=true
mrr.integration.clients[0].allowed-ips[0]=10.10.20.15
```

修改后重启 MRR 后端，再由管理员在“运维 → 认证接口测试 → 外部影像票据”检查实际生效状态。

### 2.3 当前 IP 白名单语义

当前版本支持精确 IPv4/IPv6、`*` 和空白名单，不支持 CIDR：

```properties
mrr.integration.clients[0].allowed-ips[0]=10.10.20.15
mrr.integration.clients[0].allowed-ips[1]=10.10.20.16
```

当前兼容行为中，空白名单或 `*` 都会放行来源 IP。生产环境必须填写实际 HIS 后端或固定网关 IP，不应依赖空白名单或 `*`。IP/CIDR 运行时策略的后续改造见 [Issue #126](https://github.com/weepwood/MRR/issues/126)，不属于当前接口能力。

MRR 只在请求的直接来源是本机回环地址时信任 `X-Forwarded-For` 和 `X-Real-IP`，对应正式架构中的同机 Nginx。直接访问后端或经过非本机代理时，以 TCP 直接来源 IP 为准。

## 3. 申请 Ticket

### 3.1 HIS 前端只调用自己的后端

用户点击“调阅影像”后，HIS 前端向 HIS 自己的后端提交病案条件：

```http
POST /api/mrr/archive-launch
Content-Type: application/json
Authorization: <HIS 自身登录凭证>
```

```json
{
  "bah": "00001234",
  "sjh": "00005678"
}
```

HIS 后端必须从当前已认证会话取得操作人员 ID，例如 `HIS-USER-DEMO`。不得接受前端提交的任意 `externalUserId` 并直接转发。

### 3.2 MRR Ticket 请求

```http
POST /api/v1/integration/archive/tickets
Content-Type: application/json; charset=utf-8
X-MRR-Client-Id: his-system
X-MRR-Timestamp: 1784383200
X-MRR-Nonce: 7fd72b36-d39a-4ea5-80cb-aafeeaed1815
X-MRR-Signature: 64位小写十六进制HMAC签名
```

推荐使用精确病案组合：

```json
{
  "externalUserId": "HIS-USER-DEMO",
  "archives": [
    {
      "bah": "00001234",
      "sjh": "00005678"
    }
  ],
  "allowDownload": false
}
```

字段约束：

| 字段 | 必填 | 当前语义与限制 |
|---|---:|---|
| `externalUserId` | 是 | HIS/EMR 当前操作人员唯一 ID；去除首尾空白后不能为空，最长 128 字符且不能包含控制字符 |
| `idCard` | 条件之一 | 15 位数字，或 17 位数字加一位数字/`X`；仅通过服务器间请求传递 |
| `bah` | 条件之一 | 单个病案号，1–8 位数字 |
| `sjh` | 条件之一 | 单个上架号，1–8 位数字 |
| `bahs` | 条件之一 | 多个病案号；与 `sjhs` 不按数组下标配对 |
| `sjhs` | 条件之一 | 多个上架号 |
| `archives` | 条件之一 | 多个 `{bah, sjh}` 精确组合，推荐使用 |
| `allowDownload` | 否 | 是否允许外部 ZIP 下载，默认 `false` |

所有定位条件采用并集语义，MRR 将查询结果解析为病案号和上架号组合并去重。病案号大于等于 `10000000` 时必须同时提供上架号。解析后至少得到一份病案，且不得超过 `max-archives-per-ticket`。

### 3.3 HMAC 签名

固定签名路径：

```text
/api/v1/integration/archive/tickets
```

签名步骤：

1. 把请求对象序列化为最终要发送的 UTF-8 JSON 字节 `rawBodyBytes`；
2. 计算 `bodyHash = lowercaseHex(SHA256(rawBodyBytes))`；
3. 使用 `\n` 连接 Canonical Text，各行末尾不添加空格，最后一行后不换行；
4. 使用 Client Secret 对 Canonical Text 计算 HMAC-SHA256；
5. 把小写十六进制摘要写入 `X-MRR-Signature`；
6. 发送第 1 步的同一份 `rawBodyBytes`，不得重新序列化。

Canonical Text：

```text
POST
/api/v1/integration/archive/tickets
{timestamp}
{nonce}
{bodyHash}
```

公式：

```text
signature = lowercaseHex(
  HMAC-SHA256(
    UTF8(secret),
    UTF8(canonicalText)
  )
)
```

时间戳推荐使用当前 Unix 秒。每次请求都生成新的随机 nonce；即使重试，也必须重新生成时间戳、nonce 和签名。

### 3.4 离线签名测试向量

以下值只用于核对不同语言实现，不应发送到正式 MRR。

测试 Secret：

```text
0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef
```

原始请求体是一行 UTF-8 文本：

```json
{"externalUserId":"HIS-USER-DEMO","archives":[{"bah":"00001234","sjh":"00005678"}],"allowDownload":false}
```

固定输入：

```text
timestamp = 1784383200
nonce = 7fd72b36-d39a-4ea5-80cb-aafeeaed1815
```

预期 Body Hash：

```text
0817c1d2849ea9ea5b2cb24e8511f9246db276558989a3fbba100bce8237ca49
```

预期签名：

```text
7fa5bcf761b9175c7da7f83a0e6f3b9759f3e4884bae777d000b6321bb058344
```

若结果不同，先检查 JSON 空格、换行、字段顺序、字符编码、Secret 是否被十六进制解码，以及签名后是否再次序列化。

### 3.5 成功响应

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "ticket": "一次性随机票据",
    "launchUrl": "https://mrr.example.internal/archive/external?ticket=...",
    "expiresIn": 90,
    "archiveCount": 1
  },
  "timestamp": "2026-07-28T10:00:00"
}
```

HIS 后端通常只向当前前端返回：

```json
{
  "launchUrl": "https://mrr.example.internal/archive/external?ticket=..."
}
```

Ticket 默认 90 秒有效且只能兑换一次。前端收到后应立即打开；不要缓存、持久化或记录 Ticket 和完整 `launchUrl`。

## 4. 可运行客户端完整源码

下面的代码由 VitePress 在构建时直接从仓库中的可运行示例导入，因此文档展示与实际示例保持同一份源码。所有示例都从环境变量读取：

```text
MRR_BASE_URL
MRR_CLIENT_ID
MRR_HMAC_SECRET
```

### 4.1 Python

要求 Python 3.10 或更高版本，仅使用标准库。

运行：

```powershell
python backend-repo/examples/his-integration/python/mrr_archive_ticket_client.py `
  HIS-USER-DEMO 00001234 00005678
```

完整 `mrr_archive_ticket_client.py`：

<<< ../../backend-repo/examples/his-integration/python/mrr_archive_ticket_client.py

### 4.2 Java

要求 Java 21 和 Maven。示例使用 Java HttpClient 与 Jackson。

运行：

```powershell
cd backend-repo/examples/his-integration/java
mvn -q compile exec:java -Dexec.args="HIS-USER-DEMO 00001234 00005678"
```

完整 `pom.xml`：

<<< ../../backend-repo/examples/his-integration/java/pom.xml

完整 `MrrArchiveTicketClient.java`：

<<< ../../backend-repo/examples/his-integration/java/MrrArchiveTicketClient.java

完整 `Main.java`：

<<< ../../backend-repo/examples/his-integration/java/Main.java

### 4.3 C#

要求 .NET 8 SDK。示例使用 `HttpClient`、`System.Text.Json` 和 `HMACSHA256`。

运行：

```powershell
cd backend-repo/examples/his-integration/csharp
dotnet run -- HIS-USER-DEMO 00001234 00005678
```

完整项目文件：

<<< ../../backend-repo/examples/his-integration/csharp/MrrArchiveTicketClient.csproj { xml}

完整 `MrrArchiveTicketClient.cs`：

<<< ../../backend-repo/examples/his-integration/csharp/MrrArchiveTicketClient.cs

完整 `Program.cs`：

<<< ../../backend-repo/examples/his-integration/csharp/Program.cs

Spring Boot Service、ASP.NET Core 注册、WPF/WinForms 打开浏览器等框架接入示例见 `backend-repo/docs/his-external-archive-integration-guide.md`。

## 5. 浏览器打开与 Session

浏览器前端从 HIS 后端取得 `launchUrl` 后立即打开：

```js
const response = await fetch('/api/mrr/archive-launch', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ bah, sjh }),
})

if (!response.ok) {
  throw new Error('申请 MRR 影像访问地址失败')
}

const { launchUrl } = await response.json()
window.location.assign(launchUrl)
```

如需新窗口，应在用户点击事件内先创建窗口，避免异步申请 Ticket 后被浏览器弹窗策略拦截。

WPF、WinForms：

```csharp
Process.Start(new ProcessStartInfo
{
    FileName = launchUrl,
    UseShellExecute = true,
});
```

浏览器进入 MRR 后自动：

1. 调用 `POST /api/v1/external/archive/session` 兑换一次性 Ticket；
2. 写入短期、HttpOnly、SameSite=Lax 的外部 Session Cookie；
3. 从地址栏移除 Ticket；
4. 跳转到现有 `/archive` 影像档案袋；
5. 只使用 `/api/v1/external/archive/**` 访问 Ticket 授权的病案集合。

HIS 后端不需要管理 MRR 外部 Session Cookie，也不应自行调用影像、下载或退出接口来替代浏览器流程。

外部页面不会提供任意病案搜索、内部最近查询、收藏、图片类型修改或内部 PDF 导出。ZIP 下载同时受 Ticket 的 `allowDownload` 和后端病案范围校验。

## 6. 错误处理与重试

错误响应仍使用统一结构：

```json
{
  "code": 401,
  "message": "外部系统签名无效",
  "data": null,
  "timestamp": "2026-07-28T10:00:00"
}
```

常见状态：

| HTTP / `code` | 常见原因 | 处理 |
|---|---|---|
| `400` | JSON 无效、`externalUserId` 或病案条件格式错误、授权病案数超限 | 修正请求，不自动重试 |
| `401` | 缺少签名头、Client ID 无效、时间戳格式错误/过期、签名错误 | 检查配置、时钟和原始请求字节 |
| `403` | 来源 IP 未命中白名单，或外部 Session 访问未授权病案/下载 | 检查实际来源 IP 和授权范围，不扩大权限绕过 |
| `404` | 条件未解析到病案或病案没有影像 | 核对测试数据和精确组合 |
| `409` | nonce 已使用 | 生成新时间戳、新 nonce 和新签名 |
| `503` | 集成功能未启用或对应客户端 Secret 未配置 | 修正 MRR 配置并重启 |

重试规则：

- 不复用旧 nonce；
- 不只修改 nonce 而沿用旧签名；
- 网络超时后可有限重试，但每次必须重新生成时间戳、nonce 和签名；
- 如果 MRR 已创建 Ticket 但响应在网络中丢失，重试可能创建第二个 Ticket；未使用 Ticket 会按有效期自动失效；
- 不进行无上限重试，避免掩盖配置错误或持续放大请求；
- 生产日志只记录 HTTP 状态、业务码、Client ID、脱敏操作人员 ID、Request ID 和错误分类，不记录 Secret、签名、Ticket、完整 `launchUrl`、身份证号或完整病案条件。

## 7. Nginx 与来源地址

MRR 正式部署的本机 Nginx 至少传递：

```nginx
proxy_set_header Host $host;
proxy_set_header X-Real-IP $remote_addr;
# 单层本机 Nginx 必须覆盖客户端自行提交的同名头，防止伪造来源 IP。
proxy_set_header X-Forwarded-For $remote_addr;
proxy_set_header X-Forwarded-Proto $scheme;
```

这些头用于生成正确的 `launchUrl`、识别来源 IP 和设置安全 Cookie。不要使用 `$proxy_add_x_forwarded_for` 追加客户端自带的转发链；当前单层本机 Nginx 必须以 TCP 直接来源 `$remote_addr` 覆盖它。生产环境使用 HTTPS，并确认外部返回的 `launchUrl` 域名、协议和路径可从医生工作站访问。

不要把 MRR 后端端口直接开放给普通终端来绕过本机 Nginx。影像、下载和敏感响应不得设置公共缓存。

## 8. 联调与验收

### 8.1 MRR 管理员

- [ ] `mrr.integration.enabled=true` 已实际生效；
- [ ] Client ID、Secret 和精确来源 IP 已配置；
- [ ] MRR 重启后认证测试台检查通过；
- [ ] 正常请求返回 200；
- [ ] 错误签名返回 401；
- [ ] 过期时间戳返回 401；
- [ ] 重复 nonce 返回 409；
- [ ] 未授权来源 IP 返回 403；
- [ ] Ticket 只能兑换一次；
- [ ] 未授予下载时下载接口返回 403；
- [ ] 审计中可追踪 Client ID、外部用户、病案访问、图片访问和下载结果；
- [ ] 响应、日志和截图没有 Secret、Ticket、完整 `launchUrl` 或真实患者信息。

### 8.2 HIS、EMR 开发人员

- [ ] Secret 只存在于后端安全配置；
- [ ] `externalUserId` 来自服务端认证上下文；
- [ ] JSON 只序列化一次；
- [ ] 离线测试向量计算结果一致；
- [ ] 每次请求生成新时间戳、nonce 和签名；
- [ ] HIS 与 MRR 服务器时间已同步；
- [ ] 成功后只向当前前端返回 `launchUrl`；
- [ ] 前端立即打开 `launchUrl`；
- [ ] 400、401、403、404、409、503 有明确处理；
- [ ] 重试有次数上限且不复用 nonce；
- [ ] 默认 `allowDownload=false`。

### 8.3 示例与文档验证

```powershell
python -m py_compile backend-repo/examples/his-integration/python/mrr_archive_ticket_client.py

cd backend-repo/examples/his-integration/java
mvn -B -ntp package

cd ..\csharp
dotnet build

cd ..\..\..\..\vitepress-doc
npm test
npm run docs:build:internal
```

完整 Ticket、Session、Context、影像、下载和退出模拟可使用：

```text
backend-repo/scripts/simulate-external-archive.py
```

该脚本只用于受控测试环境。正式 HIS 跳转只需申请 Ticket 并打开 `launchUrl`。

## 9. 相关文档

- `backend-repo/docs/his-external-archive-integration-guide.md`：Spring Boot、ASP.NET Core 与桌面端接入方式；
- `backend-repo/docs/external-archive-auth-test-guide.md`：MRR 认证测试台配置与错误模拟；
- `backend-repo/examples/his-integration/README.md`：三种语言示例的目录与验证命令；
- [安全边界](./security.md)；
- [API 与权限](./api.md)；
- [配置参考](./configuration-reference.md)。
