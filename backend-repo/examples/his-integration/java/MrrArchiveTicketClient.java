package example.mrr;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * HIS/EMR 服务端申请 MRR 外部影像档案袋一次性票据。
 *
 * <p>HMAC Secret 只能保存在 HIS/EMR 服务端。浏览器、Vue/React、WPF/WinForms
 * 客户端都不应直接持有 Secret。</p>
 */
public final class MrrArchiveTicketClient {

    private static final String TICKET_PATH = "/api/v1/integration/archive/tickets";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final URI baseUri;
    private final String clientId;
    private final String secret;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public MrrArchiveTicketClient(String baseUrl, String clientId, String secret) {
        this(
                baseUrl,
                clientId,
                secret,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build(),
                new ObjectMapper()
        );
    }

    public MrrArchiveTicketClient(
            String baseUrl,
            String clientId,
            String secret,
            HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        this.baseUri = URI.create(requireNonBlank(baseUrl, "baseUrl").replaceAll("/+$", ""));
        this.clientId = requireNonBlank(clientId, "clientId");
        this.secret = requireNonBlank(secret, "secret");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper").copy()
                .setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    }

    public TicketResult createTicket(TicketRequest payload) throws Exception {
        Objects.requireNonNull(payload, "payload");
        if (payload.externalUserId() == null || payload.externalUserId().isBlank()) {
            throw new IllegalArgumentException("externalUserId 不能为空");
        }

        // 必须先序列化一次，并对同一份字节同时计算 SHA-256 和发送 HTTP 请求。
        byte[] rawBody = objectMapper.writeValueAsBytes(payload);
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String bodyHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(rawBody)
        );
        String canonicalText = "POST\n" + TICKET_PATH + "\n"
                + timestamp + "\n" + nonce + "\n" + bodyHash;
        String signature = hmacSha256Hex(secret, canonicalText);

        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(TICKET_PATH))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("X-MRR-Client-Id", clientId)
                .header("X-MRR-Timestamp", timestamp)
                .header("X-MRR-Nonce", nonce)
                .header("X-MRR-Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofByteArray(rawBody))
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        JsonNode root = parseJson(response.body(), response.statusCode());
        int businessCode = root.path("code").asInt(response.statusCode());
        String message = root.path("message").asText("");
        if (response.statusCode() < 200 || response.statusCode() >= 300 || businessCode != 200) {
            throw new MrrIntegrationException(response.statusCode(), businessCode, message, response.body());
        }

        JsonNode data = root.path("data");
        String ticket = data.path("ticket").asText("");
        String launchUrl = data.path("launchUrl").asText("");
        if (ticket.isBlank() || launchUrl.isBlank()) {
            throw new MrrIntegrationException(
                    response.statusCode(), businessCode,
                    "MRR 响应缺少 ticket 或 launchUrl", response.body()
            );
        }
        return new TicketResult(
                ticket,
                launchUrl,
                data.path("expiresIn").asInt(),
                data.path("archiveCount").asInt()
        );
    }

    public String createLaunchUrl(TicketRequest payload) throws Exception {
        return createTicket(payload).launchUrl();
    }

    private JsonNode parseJson(String body, int httpStatus) throws Exception {
        try {
            return objectMapper.readTree(body);
        } catch (Exception exception) {
            throw new MrrIntegrationException(
                    httpStatus, httpStatus,
                    "MRR 返回的内容不是有效 JSON", body
            );
        }
    }

    private static String hmacSha256Hex(String secret, String canonicalText) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
        return HexFormat.of().formatHex(
                mac.doFinal(canonicalText.getBytes(StandardCharsets.UTF_8))
        );
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return value.trim();
    }

    public record ArchiveSelector(String bah, String sjh) {
    }

    public record TicketRequest(
            String externalUserId,
            String idCard,
            String bah,
            String sjh,
            List<String> bahs,
            List<String> sjhs,
            List<ArchiveSelector> archives,
            boolean allowDownload
    ) {
        public static TicketRequest exactArchive(
                String externalUserId,
                String bah,
                String sjh,
                boolean allowDownload
        ) {
            return new TicketRequest(
                    externalUserId,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    List.of(new ArchiveSelector(bah, sjh)),
                    allowDownload
            );
        }
    }

    public record TicketResult(
            String ticket,
            String launchUrl,
            int expiresIn,
            int archiveCount
    ) {
    }

    public static final class MrrIntegrationException extends RuntimeException {
        private final int httpStatus;
        private final int businessCode;
        private final String responseBody;

        public MrrIntegrationException(
                int httpStatus,
                int businessCode,
                String message,
                String responseBody
        ) {
            super("MRR 调用失败：HTTP " + httpStatus
                    + ", code=" + businessCode
                    + ", message=" + message);
            this.httpStatus = httpStatus;
            this.businessCode = businessCode;
            this.responseBody = responseBody;
        }

        public int getHttpStatus() {
            return httpStatus;
        }

        public int getBusinessCode() {
            return businessCode;
        }

        public String getResponseBody() {
            return responseBody;
        }
    }
}
