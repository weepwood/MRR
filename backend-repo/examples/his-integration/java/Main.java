package example.mrr;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("用法: mvn -q compile exec:java -Dexec.args=\"<externalUserId> <bah> <sjh>\"");
            System.err.println("需要环境变量: MRR_BASE_URL, MRR_CLIENT_ID, MRR_HMAC_SECRET");
            System.exit(2);
        }

        String baseUrl = requireEnvironment("MRR_BASE_URL");
        String clientId = requireEnvironment("MRR_CLIENT_ID");
        String secret = requireEnvironment("MRR_HMAC_SECRET");

        MrrArchiveTicketClient client = new MrrArchiveTicketClient(baseUrl, clientId, secret);
        MrrArchiveTicketClient.TicketRequest request =
                MrrArchiveTicketClient.TicketRequest.exactArchive(
                        args[0],
                        args[1],
                        args[2],
                        false
                );

        try {
            MrrArchiveTicketClient.TicketResult result = client.createTicket(request);
            System.out.println("archiveCount=" + result.archiveCount());
            System.out.println("expiresIn=" + result.expiresIn());
            System.out.println(result.launchUrl());
        } catch (MrrArchiveTicketClient.MrrIntegrationException exception) {
            System.err.println(exception.getMessage());
            System.err.println(exception.getResponseBody());
            System.exit(1);
        }
    }

    private static String requireEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少环境变量 " + name);
        }
        return value;
    }
}
