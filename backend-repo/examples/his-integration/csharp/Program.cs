using Mrr.HisIntegration;

static string RequireEnvironment(string name)
{
    string? value = Environment.GetEnvironmentVariable(name);
    if (string.IsNullOrWhiteSpace(value))
    {
        throw new InvalidOperationException($"缺少环境变量 {name}");
    }

    return value;
}

if (args.Length < 3)
{
    Console.Error.WriteLine("用法: dotnet run -- <externalUserId> <bah> <sjh>");
    Console.Error.WriteLine("需要环境变量: MRR_BASE_URL, MRR_CLIENT_ID, MRR_HMAC_SECRET");
    return 2;
}

string baseUrl = RequireEnvironment("MRR_BASE_URL");
string clientId = RequireEnvironment("MRR_CLIENT_ID");
string secret = RequireEnvironment("MRR_HMAC_SECRET");

using var httpClient = new HttpClient
{
    Timeout = TimeSpan.FromSeconds(30),
};
var mrrClient = new MrrArchiveTicketClient(httpClient, baseUrl, clientId, secret);
var request = TicketRequest.ExactArchive(
    externalUserId: args[0],
    bah: args[1],
    sjh: args[2],
    allowDownload: false);

try
{
    TicketResult result = await mrrClient.CreateTicketAsync(request);
    Console.WriteLine($"archiveCount={result.ArchiveCount}");
    Console.WriteLine($"expiresIn={result.ExpiresIn}");
    Console.WriteLine(result.LaunchUrl);
    return 0;
}
catch (MrrIntegrationException exception)
{
    Console.Error.WriteLine(exception.Message);
    Console.Error.WriteLine(exception.ResponseBody);
    return 1;
}
