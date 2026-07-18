package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.config.IntegrationProperties;
import com.zjcxph.imgapi.dto.req.ExternalArchiveTicketRequest;
import com.zjcxph.imgapi.dto.resp.BAHDataResponseDTO;
import com.zjcxph.imgapi.dto.resp.ExternalArchiveCaseDTO;
import com.zjcxph.imgapi.dto.resp.IdCardArchiveSearchResponse;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.security.ExternalArchiveGrant;
import com.zjcxph.imgapi.utils.MedicalRecordCodeUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ExternalArchiveAccessService {

    public static final String SESSION_COOKIE_NAME = "MRR_ARCHIVE_SESSION";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final IntegrationProperties properties;
    private final SearchService searchService;
    private final ScanService scanService;
    private final ConcurrentHashMap<String, TimedGrant> tickets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TimedGrant> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> usedNonces = new ConcurrentHashMap<>();

    public ExternalArchiveAccessService(
            IntegrationProperties properties,
            SearchService searchService,
            ScanService scanService
    ) {
        this.properties = properties;
        this.searchService = searchService;
        this.scanService = scanService;
    }

    public IssuedTicket createTicket(
            String clientId,
            String timestamp,
            String nonce,
            String signature,
            String method,
            String path,
            String rawBody,
            String clientIp,
            ExternalArchiveTicketRequest request
    ) {
        IntegrationProperties.Client client = authenticateClient(
                clientId, timestamp, nonce, signature, method, path, rawBody, clientIp
        );
        validateExternalUser(request.getExternalUserId());

        List<ExternalArchiveCaseDTO> cases = resolveCases(request);
        long expiresAt = System.currentTimeMillis() + properties.getTicketTtlSeconds() * 1000L;
        ExternalArchiveGrant grant = new ExternalArchiveGrant(
                client.getClientId(),
                request.getExternalUserId().trim(),
                request.isAllowDownload(),
                expiresAt,
                List.copyOf(cases)
        );
        String ticket = randomToken();
        tickets.put(ticket, new TimedGrant(grant, expiresAt));
        cleanupExpired();
        return new IssuedTicket(ticket, grant, properties.getTicketTtlSeconds());
    }

    public IssuedSession consumeTicket(String ticket) {
        if (!StringUtils.hasText(ticket)) {
            throw new BusinessException(400, "ticket 不能为空");
        }
        TimedGrant stored = tickets.remove(ticket.trim());
        if (stored == null || stored.expiresAt() < System.currentTimeMillis()) {
            throw new BusinessException(401, "访问票据无效、已使用或已过期");
        }

        long expiresAt = System.currentTimeMillis() + properties.getSessionTtlSeconds() * 1000L;
        ExternalArchiveGrant source = stored.grant();
        ExternalArchiveGrant sessionGrant = new ExternalArchiveGrant(
                source.clientId(), source.externalUserId(), source.allowDownload(), expiresAt, source.cases()
        );
        String sessionToken = randomToken();
        sessions.put(sessionToken, new TimedGrant(sessionGrant, expiresAt));
        cleanupExpired();
        return new IssuedSession(sessionToken, sessionGrant, properties.getSessionTtlSeconds());
    }

    public ExternalArchiveGrant requireSession(String sessionToken) {
        if (!StringUtils.hasText(sessionToken)) {
            throw new BusinessException(401, "外部影像会话不存在");
        }
        TimedGrant stored = sessions.get(sessionToken.trim());
        if (stored == null || stored.expiresAt() < System.currentTimeMillis()) {
            sessions.remove(sessionToken.trim());
            throw new BusinessException(401, "外部影像会话已过期");
        }
        return stored.grant();
    }

    public List<BAHDataResponseDTO> loadImages(ExternalArchiveGrant grant, String bah, String sjh) {
        String normalizedBah = MedicalRecordCodeUtils.normalizeOrEmpty(bah);
        String normalizedSjh = MedicalRecordCodeUtils.normalizeOrEmpty(sjh);
        if (!grant.allows(normalizedBah, normalizedSjh)) {
            throw new BusinessException(403, "当前外部会话无权访问该病案");
        }

        List<Scan> scans = scanService.getImageListByCode(
                normalizedBah,
                MedicalRecordCodeUtils.toSearchTerm(bah),
                normalizedSjh,
                MedicalRecordCodeUtils.toSearchTerm(sjh)
        );
        List<BAHDataResponseDTO> result = new ArrayList<>(scans.size());
        for (Scan scan : scans) {
            if (!grant.allows(scan.getBah(), scan.getSjh())) {
                continue;
            }
            BAHDataResponseDTO dto = new BAHDataResponseDTO();
            BeanUtils.copyProperties(scan, dto);
            dto.setBah(MedicalRecordCodeUtils.normalize(scan.getBah()));
            dto.setSjh(MedicalRecordCodeUtils.normalize(scan.getSjh()));
            dto.setImg_url("/api/v1/external/archive/image/" + scan.getId());
            dto.setOssUrl(null);
            result.add(dto);
        }
        return result;
    }

    public Scan requireImage(ExternalArchiveGrant grant, Integer imageId) {
        if (imageId == null) {
            throw new BusinessException(400, "影像 ID 不能为空");
        }
        Scan scan = scanService.findById(imageId);
        if (scan == null) {
            throw new BusinessException(404, "影像不存在");
        }
        if (!grant.allows(scan.getBah(), scan.getSjh())) {
            throw new BusinessException(403, "当前外部会话无权访问该影像");
        }
        return scan;
    }

    private IntegrationProperties.Client authenticateClient(
            String clientId,
            String timestamp,
            String nonce,
            String signature,
            String method,
            String path,
            String rawBody,
            String clientIp
    ) {
        if (!properties.isEnabled()) {
            throw new BusinessException(503, "外部系统集成未启用");
        }
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(timestamp)
                || !StringUtils.hasText(nonce) || !StringUtils.hasText(signature)) {
            throw new BusinessException(401, "缺少外部系统签名请求头");
        }

        IntegrationProperties.Client client = properties.getClients().stream()
                .filter(IntegrationProperties.Client::isEnabled)
                .filter(item -> clientId.trim().equals(item.getClientId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(401, "外部系统客户端无效"));
        if (!StringUtils.hasText(client.getSecret())) {
            throw new BusinessException(503, "外部系统客户端密钥未配置");
        }
        if (!isIpAllowed(client, clientIp)) {
            throw new BusinessException(403, "当前来源 IP 不在外部系统白名单中");
        }

        long requestEpochSeconds;
        try {
            requestEpochSeconds = Long.parseLong(timestamp.trim());
            if (requestEpochSeconds > 10_000_000_000L) {
                requestEpochSeconds /= 1000L;
            }
        } catch (NumberFormatException exception) {
            throw new BusinessException(401, "时间戳格式无效");
        }
        long difference = Math.abs(Instant.now().getEpochSecond() - requestEpochSeconds);
        if (difference > properties.getTimestampToleranceSeconds()) {
            throw new BusinessException(401, "签名时间戳已过期");
        }

        String nonceKey = client.getClientId() + ":" + nonce.trim();
        long nonceExpiresAt = System.currentTimeMillis() + properties.getTimestampToleranceSeconds() * 2000L;
        Long existing = usedNonces.putIfAbsent(nonceKey, nonceExpiresAt);
        if (existing != null && existing > System.currentTimeMillis()) {
            throw new BusinessException(409, "签名 nonce 已使用");
        }

        String bodyHash = sha256Hex(rawBody == null ? "" : rawBody);
        String canonical = method.toUpperCase(Locale.ROOT) + "\n"
                + path + "\n"
                + timestamp.trim() + "\n"
                + nonce.trim() + "\n"
                + bodyHash;
        String expectedHex = hmacSha256Hex(client.getSecret(), canonical);
        String supplied = signature.trim().replaceFirst("(?i)^sha256=", "").toLowerCase(Locale.ROOT);
        if (!constantTimeEquals(expectedHex, supplied)) {
            usedNonces.remove(nonceKey);
            throw new BusinessException(401, "外部系统签名无效");
        }
        return client;
    }

    private List<ExternalArchiveCaseDTO> resolveCases(ExternalArchiveTicketRequest request) {
        LinkedHashMap<String, ExternalArchiveCaseDTO> resolved = new LinkedHashMap<>();

        if (StringUtils.hasText(request.getIdCard())) {
            List<IdCardArchiveSearchResponse.ArchiveCase> idCases =
                    searchService.getArchiveCasesByID(request.getIdCard().trim());
            for (IdCardArchiveSearchResponse.ArchiveCase archiveCase : idCases) {
                addCase(resolved, new ExternalArchiveCaseDTO(
                        archiveCase.getBah(),
                        archiveCase.getSjh(),
                        archiveCase.getName(),
                        archiveCase.getDepartment(),
                        archiveCase.getAdmissionTime()
                ));
            }
        }

        if (StringUtils.hasText(request.getBah()) || StringUtils.hasText(request.getSjh())) {
            resolveSelector(resolved, request.getBah(), request.getSjh());
        }
        safeList(request.getBahs()).forEach(value -> resolveSelector(resolved, value, null));
        safeList(request.getSjhs()).forEach(value -> resolveSelector(resolved, null, value));
        safeList(request.getArchives()).forEach(value -> {
            if (value != null) {
                resolveSelector(resolved, value.getBah(), value.getSjh());
            }
        });

        if (resolved.isEmpty()) {
            throw new BusinessException(404, "未根据外部请求定位到可访问的影像病案");
        }
        if (resolved.size() > properties.getMaxArchivesPerTicket()) {
            throw new BusinessException(400, "单次票据最多允许访问 " + properties.getMaxArchivesPerTicket() + " 份病案");
        }
        return new ArrayList<>(resolved.values());
    }

    private void resolveSelector(Map<String, ExternalArchiveCaseDTO> resolved, String rawBah, String rawSjh) {
        String normalizedBah = MedicalRecordCodeUtils.normalizeOrEmpty(rawBah);
        String normalizedSjh = MedicalRecordCodeUtils.normalizeOrEmpty(rawSjh);
        if (normalizedBah.isEmpty() && normalizedSjh.isEmpty()) {
            return;
        }
        if (MedicalRecordCodeUtils.requiresSjhForBah(normalizedBah) && normalizedSjh.isEmpty()) {
            throw new BusinessException(400, "病案号大于等于 10000000 时必须在 archives 中同时提供上架号");
        }

        List<Scan> scans = scanService.getImageListByCode(
                normalizedBah,
                MedicalRecordCodeUtils.toSearchTerm(rawBah),
                normalizedSjh,
                MedicalRecordCodeUtils.toSearchTerm(rawSjh)
        );
        if (scans.isEmpty()) {
            throw new BusinessException(404, "未找到病案：bah=" + normalizedBah + ", sjh=" + normalizedSjh);
        }
        for (Scan scan : scans) {
            addCase(resolved, new ExternalArchiveCaseDTO(
                    scan.getBah(), scan.getSjh(), null, null, null
            ));
            if (resolved.size() > properties.getMaxArchivesPerTicket()) {
                throw new BusinessException(400, "单次票据最多允许访问 " + properties.getMaxArchivesPerTicket() + " 份病案");
            }
        }
    }

    private void addCase(Map<String, ExternalArchiveCaseDTO> resolved, ExternalArchiveCaseDTO item) {
        String bah = MedicalRecordCodeUtils.normalizeOrEmpty(item.getBah());
        String sjh = MedicalRecordCodeUtils.normalizeOrEmpty(item.getSjh());
        if (bah.isEmpty() && sjh.isEmpty()) {
            return;
        }
        item.setBah(bah);
        item.setSjh(sjh);
        resolved.putIfAbsent(ExternalArchiveGrant.keyOf(bah, sjh), item);
    }

    private void validateExternalUser(String externalUserId) {
        if (!StringUtils.hasText(externalUserId)) {
            throw new BusinessException(400, "externalUserId 不能为空");
        }
        String value = externalUserId.trim();
        if (value.length() > 128 || value.chars().anyMatch(Character::isISOControl)) {
            throw new BusinessException(400, "externalUserId 格式无效");
        }
    }

    private boolean isIpAllowed(IntegrationProperties.Client client, String clientIp) {
        List<String> allowedIps = client.getAllowedIps();
        if (allowedIps == null || allowedIps.isEmpty()) {
            return true;
        }
        return allowedIps.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .anyMatch(value -> "*".equals(value) || value.equals(clientIp));
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        tickets.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
        sessions.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
        usedNonces.entrySet().removeIf(entry -> entry.getValue() < now);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private String hmacSha256Hex(String secret, String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", exception);
        }
    }

    private boolean constantTimeEquals(String expected, String supplied) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                supplied.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private <T> List<T> safeList(List<T> value) {
        return value == null ? List.of() : value;
    }

    private record TimedGrant(ExternalArchiveGrant grant, long expiresAt) {
    }

    public record IssuedTicket(String token, ExternalArchiveGrant grant, int expiresIn) {
    }

    public record IssuedSession(String token, ExternalArchiveGrant grant, int expiresIn) {
    }
}
