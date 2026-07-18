package com.zjcxph.imgapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.config.IntegrationProperties;
import com.zjcxph.imgapi.dto.req.ExternalArchiveTicketRequest;
import com.zjcxph.imgapi.dto.resp.IdCardArchiveSearchResponse;
import com.zjcxph.imgapi.entity.ExternalArchiveStoredGrant;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.mapper.ExternalArchiveAccessMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalArchiveAccessServiceTest {

    private static final String CLIENT_ID = "his-system";
    private static final String SECRET = "test-secret-that-is-long-enough";
    private static final String PATH = "/api/v1/integration/archive/tickets";

    @Mock
    private SearchService searchService;

    @Mock
    private ScanService scanService;

    @Mock
    private ExternalArchiveAccessMapper accessMapper;

    private ObjectMapper objectMapper;
    private ExternalArchiveAccessService service;

    @BeforeEach
    void setUp() {
        IntegrationProperties properties = new IntegrationProperties();
        properties.setEnabled(true);
        properties.setTicketTtlSeconds(90);
        properties.setSessionTtlSeconds(1800);
        properties.setTimestampToleranceSeconds(300);
        properties.setMaxArchivesPerTicket(100);

        IntegrationProperties.Client client = new IntegrationProperties.Client();
        client.setClientId(CLIENT_ID);
        client.setSecret(SECRET);
        client.setEnabled(true);
        client.setAllowedIps(List.of("127.0.0.1"));
        properties.setClients(List.of(client));

        objectMapper = new ObjectMapper();
        service = new ExternalArchiveAccessService(
                properties, searchService, scanService, accessMapper, objectMapper
        );
        when(accessMapper.insertNonce(anyString(), anyString(), any(LocalDateTime.class))).thenReturn(1);
        when(accessMapper.insertTicket(
                anyString(), anyString(), anyString(), anyBoolean(), anyString(),
                any(LocalDateTime.class), anyString()
        )).thenReturn(1);
        when(accessMapper.insertSession(
                anyString(), anyString(), anyString(), anyBoolean(), anyString(),
                any(LocalDateTime.class), anyString()
        )).thenReturn(1);
    }

    @Test
    void resolvesMixedSelectorsAsUnionAndConsumesTicketOnlyOnce() throws Exception {
        when(searchService.getArchiveCasesByID("330000000000000000")).thenReturn(List.of(
                new IdCardArchiveSearchResponse.ArchiveCase(
                        1, "1", "10", "患者甲", null, "2026-07-18 08:00",
                        "内科", null, null
                )
        ));
        when(scanService.getImageListByCode(anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String bah = invocation.getArgument(0);
                    String sjh = invocation.getArgument(2);
                    if ("00000002".equals(bah)) {
                        return List.of(scan(2, "2", "20"));
                    }
                    if ("00000003".equals(sjh)) {
                        return List.of(scan(3, "3", "3"));
                    }
                    if ("10000001".equals(bah) && "20000001".equals(sjh)) {
                        return List.of(scan(4, bah, sjh));
                    }
                    return List.of();
                });

        ExternalArchiveTicketRequest request = new ExternalArchiveTicketRequest();
        request.setExternalUserId("HIS-USER-10086");
        request.setIdCard("330000000000000000");
        request.setBahs(List.of("2"));
        request.setSjhs(List.of("3"));
        ExternalArchiveTicketRequest.ArchiveSelector exact = new ExternalArchiveTicketRequest.ArchiveSelector();
        exact.setBah("10000001");
        exact.setSjh("20000001");
        request.setArchives(List.of(exact));

        String rawBody = "{\"externalUserId\":\"HIS-USER-10086\"}";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String signature = signature(timestamp, nonce, rawBody);

        ExternalArchiveAccessService.IssuedTicket ticket = service.createTicket(
                CLIENT_ID, timestamp, nonce, signature, "POST", PATH, rawBody,
                "127.0.0.1", request
        );

        assertThat(ticket.grant().cases()).hasSize(4);
        assertThat(ticket.grant().allows("1", "10")).isTrue();
        assertThat(ticket.grant().allows("2", "20")).isTrue();
        assertThat(ticket.grant().allows("3", "3")).isTrue();
        assertThat(ticket.grant().allows("10000001", "20000001")).isTrue();

        ExternalArchiveStoredGrant storedTicket = storedGrant(ticket.grant().cases());
        when(accessMapper.consumeTicket(anyString())).thenReturn(storedTicket, null);

        ExternalArchiveAccessService.IssuedSession session = service.consumeTicket(ticket.token(), "127.0.0.1");
        ExternalArchiveStoredGrant storedSession = storedGrant(session.grant().cases());
        storedSession.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        when(accessMapper.findSession(anyString())).thenReturn(storedSession);

        assertThat(service.requireSession(session.token()).externalUserId()).isEqualTo("HIS-USER-10086");
        assertThatThrownBy(() -> service.consumeTicket(ticket.token(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无效");
    }

    @Test
    void requiresExactPairForNonUniqueLargeBah() throws Exception {
        ExternalArchiveTicketRequest request = new ExternalArchiveTicketRequest();
        request.setExternalUserId("HIS-USER-1");
        request.setBah("10000001");

        String rawBody = "{}";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();

        assertThatThrownBy(() -> service.createTicket(
                CLIENT_ID,
                timestamp,
                nonce,
                signature(timestamp, nonce, rawBody),
                "POST",
                PATH,
                rawBody,
                "127.0.0.1",
                request
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须在 archives 中同时提供上架号");
    }

    @Test
    void rejectsReplayedNonce() throws Exception {
        when(scanService.getImageListByCode(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(scan(1, "1", "10")));
        when(accessMapper.insertNonce(anyString(), anyString(), any(LocalDateTime.class)))
                .thenReturn(1, 0);
        ExternalArchiveTicketRequest request = new ExternalArchiveTicketRequest();
        request.setExternalUserId("HIS-USER-1");
        request.setBah("1");

        String rawBody = "{}";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String signature = signature(timestamp, nonce, rawBody);

        service.createTicket(CLIENT_ID, timestamp, nonce, signature, "POST", PATH, rawBody, "127.0.0.1", request);
        assertThatThrownBy(() -> service.createTicket(
                CLIENT_ID, timestamp, nonce, signature, "POST", PATH, rawBody, "127.0.0.1", request
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("nonce 已使用");
    }

    private ExternalArchiveStoredGrant storedGrant(List<?> cases) throws Exception {
        ExternalArchiveStoredGrant stored = new ExternalArchiveStoredGrant();
        stored.setClientId(CLIENT_ID);
        stored.setExternalUserId("HIS-USER-10086");
        stored.setAllowDownload(false);
        stored.setGrantJson(objectMapper.writeValueAsString(cases));
        stored.setExpiresAt(LocalDateTime.now().plusMinutes(2));
        return stored;
    }

    private Scan scan(int id, String bah, String sjh) {
        Scan scan = new Scan();
        scan.setId(id);
        scan.setBah(bah);
        scan.setSjh(sjh);
        scan.setFilename(id + ".jpg");
        return scan;
    }

    private String signature(String timestamp, String nonce, String body) throws Exception {
        String bodyHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8))
        );
        String canonical = "POST\n" + PATH + "\n" + timestamp + "\n" + nonce + "\n" + bodyHash;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
    }
}
