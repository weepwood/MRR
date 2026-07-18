# HIS、EMR 外部影像调阅示例

完整接入说明见：

```text
backend-repo/docs/his-external-archive-integration-guide.md
```

三个示例都遵循同一流程：

1. 从环境变量读取 MRR 地址、Client ID 和 HMAC Secret；
2. 使用当前 HIS 用户 ID、病案号和上架号生成请求体；
3. 对最终发送的 UTF-8 JSON 字节计算 SHA-256；
4. 构造 Canonical Text 并计算 HMAC-SHA256；
5. 调用 `/api/v1/integration/archive/tickets`；
6. 输出 `launchUrl`。

## 环境变量

```text
MRR_BASE_URL
MRR_CLIENT_ID
MRR_HMAC_SECRET
```

Secret 只能保存在 HIS、EMR 后端或受控的 Secret Manager 中，不要放入浏览器、WPF 或 WinForms 客户端。

## Python

```powershell
python python/mrr_archive_ticket_client.py DOC-10086 789508 123456
```

仅使用 Python 标准库。

## Java

```powershell
cd java
mvn -q compile exec:java -Dexec.args="DOC-10086 789508 123456"
```

示例使用 Java 21、Java HttpClient 和 Jackson。

## C#

```powershell
cd csharp
dotnet run -- DOC-10086 789508 123456
```

示例使用 .NET 8、HttpClient、System.Text.Json 和 HMACSHA256。

输出最后一行是一次性 `launchUrl`。正式 HIS 后端应把该 URL 返回当前 HIS 前端，由前端立即使用系统浏览器或新窗口打开。

## 本地验证说明

当前工作环境可用 Python 3.13 和 Java 21，但没有安装 Maven 与 .NET SDK。因此本次只能对 Java、C# 示例做代码审查，不能声称已在本环境完成 Maven 或 `dotnet build`。在医院接入环境中应执行：

```powershell
cd java
mvn test
mvn package

cd ..\csharp
dotnet build
```
