using System.Globalization;
using System.Net.Http.Headers;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace Mrr.HisIntegration;

/// <summary>
/// HIS/EMR 服务端申请 MRR 外部影像档案袋一次性票据。
/// HMAC Secret 只能保存在 ASP.NET Core 服务端或其他受控后端服务中。
/// </summary>
public sealed class MrrArchiveTicketClient
{
    private const string TicketPath = "/api/v1/integration/archive/tickets";

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
        WriteIndented = false,
    };

    private readonly HttpClient _httpClient;
    private readonly Uri _baseUri;
    private readonly string _clientId;
    private readonly string _secret;

    public MrrArchiveTicketClient(
        HttpClient httpClient,
        string baseUrl,
        string clientId,
        string secret)
    {
        _httpClient = httpClient ?? throw new ArgumentNullException(nameof(httpClient));
        _baseUri = new Uri(RequireValue(baseUrl, nameof(baseUrl)).TrimEnd('/') + "/");
        _clientId = RequireValue(clientId, nameof(clientId));
        _secret = RequireValue(secret, nameof(secret));
    }

    public async Task<TicketResult> CreateTicketAsync(
        TicketRequest payload,
        CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(payload);
        if (string.IsNullOrWhiteSpace(payload.ExternalUserId))
        {
            throw new ArgumentException("externalUserId 不能为空", nameof(payload));
        }

        // 只序列化一次：签名和实际 HTTP 请求必须使用完全相同的 UTF-8 字节。
        byte[] rawBody = JsonSerializer.SerializeToUtf8Bytes(payload, JsonOptions);
        string timestamp = DateTimeOffset.UtcNow
            .ToUnixTimeSeconds()
            .ToString(CultureInfo.InvariantCulture);
        string nonce = Guid.NewGuid().ToString();
        string bodyHash = Convert.ToHexString(SHA256.HashData(rawBody)).ToLowerInvariant();
        string canonicalText = $"POST\n{TicketPath}\n{timestamp}\n{nonce}\n{bodyHash}";
        string signature = CreateHmacSignature(_secret, canonicalText);

        using var request = new HttpRequestMessage(
            HttpMethod.Post,
            new Uri(_baseUri, TicketPath.TrimStart('/')));
        request.Headers.TryAddWithoutValidation("X-MRR-Client-Id", _clientId);
        request.Headers.TryAddWithoutValidation("X-MRR-Timestamp", timestamp);
        request.Headers.TryAddWithoutValidation("X-MRR-Nonce", nonce);
        request.Headers.TryAddWithoutValidation("X-MRR-Signature", signature);
        request.Content = new ByteArrayContent(rawBody);
        request.Content.Headers.ContentType = new MediaTypeHeaderValue("application/json")
        {
            CharSet = "utf-8",
        };

        using HttpResponseMessage response = await _httpClient.SendAsync(
            request,
            HttpCompletionOption.ResponseHeadersRead,
            cancellationToken);
        string responseBody = await response.Content.ReadAsStringAsync(cancellationToken);

        MrrApiResponse<TicketResult>? apiResponse;
        try
        {
            apiResponse = JsonSerializer.Deserialize<MrrApiResponse<TicketResult>>(
                responseBody,
                JsonOptions);
        }
        catch (JsonException exception)
        {
            throw new MrrIntegrationException(
                (int)response.StatusCode,
                (int)response.StatusCode,
                "MRR 返回的内容不是有效 JSON",
                responseBody,
                exception);
        }

        int businessCode = apiResponse?.Code ?? (int)response.StatusCode;
        if (!response.IsSuccessStatusCode || businessCode != 200 || apiResponse?.Data is null)
        {
            throw new MrrIntegrationException(
                (int)response.StatusCode,
                businessCode,
                apiResponse?.Message ?? "MRR 调用失败",
                responseBody);
        }

        if (string.IsNullOrWhiteSpace(apiResponse.Data.Ticket)
            || string.IsNullOrWhiteSpace(apiResponse.Data.LaunchUrl))
        {
            throw new MrrIntegrationException(
                (int)response.StatusCode,
                businessCode,
                "MRR 响应缺少 ticket 或 launchUrl",
                responseBody);
        }

        return apiResponse.Data;
    }

    public async Task<string> CreateLaunchUrlAsync(
        TicketRequest payload,
        CancellationToken cancellationToken = default)
    {
        TicketResult result = await CreateTicketAsync(payload, cancellationToken);
        return result.LaunchUrl;
    }

    private static string CreateHmacSignature(string secret, string canonicalText)
    {
        using var hmac = new HMACSHA256(Encoding.UTF8.GetBytes(secret));
        byte[] digest = hmac.ComputeHash(Encoding.UTF8.GetBytes(canonicalText));
        return Convert.ToHexString(digest).ToLowerInvariant();
    }

    private static string RequireValue(string value, string name)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            throw new ArgumentException($"{name} 不能为空", name);
        }

        return value.Trim();
    }
}

public sealed record ArchiveSelector(string Bah, string Sjh);

public sealed record TicketRequest(
    string ExternalUserId,
    string? IdCard = null,
    string? Bah = null,
    string? Sjh = null,
    IReadOnlyList<string>? Bahs = null,
    IReadOnlyList<string>? Sjhs = null,
    IReadOnlyList<ArchiveSelector>? Archives = null,
    bool AllowDownload = false)
{
    public static TicketRequest ExactArchive(
        string externalUserId,
        string bah,
        string sjh,
        bool allowDownload = false)
    {
        return new TicketRequest(
            ExternalUserId: externalUserId,
            Archives: [new ArchiveSelector(bah, sjh)],
            AllowDownload: allowDownload);
    }
}

public sealed record TicketResult(
    string Ticket,
    string LaunchUrl,
    int ExpiresIn,
    int ArchiveCount);

public sealed record MrrApiResponse<T>(
    int Code,
    string? Message,
    T? Data,
    DateTimeOffset? Timestamp);

public sealed class MrrIntegrationException : Exception
{
    public MrrIntegrationException(
        int httpStatus,
        int businessCode,
        string message,
        string responseBody,
        Exception? innerException = null)
        : base(
            $"MRR 调用失败：HTTP {httpStatus}, code={businessCode}, message={message}",
            innerException)
    {
        HttpStatus = httpStatus;
        BusinessCode = businessCode;
        ResponseBody = responseBody;
    }

    public int HttpStatus { get; }

    public int BusinessCode { get; }

    public string ResponseBody { get; }
}
