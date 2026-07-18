package com.zjcxph.imgapi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.config.IntegrationProperties;
import com.zjcxph.imgapi.dto.req.ExternalArchiveTicketRequest;
import com.zjcxph.imgapi.dto.resp.BAHDataResponseDTO;
import com.zjcxph.imgapi.dto.resp.ExternalArchiveCaseDTO;
import com.zjcxph.imgapi.dto.resp.IdCardArchiveSearchResponse;
import com.zjcxph.imgapi.entity.ExternalArchiveStoredGrant;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.mapper.ExternalArchiveAccessMapper;
import com.zjcxph.imgapi.security.ExternalArchiveGrant;
import com.zjcxph.imgapi.utils.MedicalRecordCodeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ExternalArchiveAccessService {

    public static final String SESSION_COOKIE_NAME = "MRR_ARCHIVE_SESSION";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final TypeReference<List<ExternalArchiveCaseDTO>> CASE_LIST_TYPE = new TypeReference<>() { };
    private static final Logger logger = LoggerFactory.getLogger(ExternalArchiveAccessService.class);

    private final IntegrationProperties properties;
    private final SearchService searchService;
    private final ScanService scanService;
    private final ExternalArchiveAccessMapper accessMapper;
    private final ObjectMapper objectMapper;

    public ExternalArchiveAccessService(
            IntegrationProperties properties,
            SearchService searchService,
            ScanService scanService,
            ExternalArchiveAccessMapper accessMapper,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.searchService = searchService;
        this.scanService = scanService;
        this.accessMapper = accessMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
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
        if (request == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        IntegrationProperties.Client client = authenticateClient(
                clientId, timestamp, nonce, signature, method, path, rawBody, clientIp
        );
        validateExternalUser(request.getExternalUserId());

        List<ExternalArchiveCaseDTO> cases = resolveCases(request);
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(properties.getTicketTtlSeconds());
        long expiresAtMillis = toEpochMillis(expiresAt);
        ExternalArchiveGrant grant = new ExternalArchiveGrant(
                client.getClientId(),
                request.getExternalUserId().trim(),
                request.isAllowDownload(),
                expiresAtMillis,
                List.copyOf(cases)
        );

        String ticket = randomToken();
        accessMapper.insertTicket(
                sha256Hex(ticket),
                grant.clientId(),
                grant.externalUserId(),
                grant.allowDownload(),
                serializeCases(grant.cases()),
                expiresAt,
                clientIp
        );
        return new IssuedTicket(ticket, grant, properties.getTicketTtlSeconds());
    }

    @Transactional(rollbackFor = Exception.class)
    public IssuedSession consumeTicket(String ticket, String clientIp) {
        if (!StringUtils.hasText(ticket)) {
            throw new BusinessException(400, "ticket 不能为空");
        }
        ExternalArchiveStoredGrant stored = accessMapper.consumeTicket(sha256Hex(ticket.trim()));
        if (stored == null) {
            throw new BusinessException(401, "访问票据无效、已使用或已过期");
        }

        List<ExternalArchiveCaseDTO> cases = deserializeCases(stored.getGrantJson());
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(properties.getSessionTtlSeconds());
        ExternalArchiveGrant sessionGrant = new ExternalArchiveGrant(
                stored.getClientId(),
                stored.getExternalUserId(),
                Boolean.TRUE.equals(stored.getAllowDownload()),
                toEpochMillis(expiresAt),
                List.copyOf(cases)
        );

        String sessionToken = randomToken();
        accessMapper.insertSession(
                sha256Hex(sessionToken),
                sessionGrant.clientId(),
                sessionGrant.externalUserId(),
                sessionGrant.allowDownload(),
                serializeCases(sessionGrant.cases()),
                expiresAt,
                clientIp
        );
        return new IssuedSession(sessionToken, sessionGrant, properties.getSessionTtlSeconds());
    }

    public ExternalArchiveGrant requireSession(String sessionToken) {
        if (!StringUtils.hasText(sessionToken)) {
            throw new BusinessException(401, "外部影像会话不存在");
        }
        String sessionHash = sha256Hex(sessionToken.trim());
        ExternalArchiveStoredGrant stored = accessMapper.findSession(sessionHash);
        if (stored == null) {
            throw new BusinessException(401, "外部影像会话已过期或已退出");
        }
        accessMapper.touchSession(sessionHash);
        return toGrant(stored);
    }

    public void revokeSession(String sessionToken) {
        if (StringUtils.hasText(sessionToken)) {
            accessMapper.revokeSession(sha256Hex(sessionToken.trim()));
        }
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

    public void recordAudit(
            ExternalArchiveGrant grant,
            String bah,
            String sjh,
            String action,
            Integer imageId,
            String clientIp,
            String userAgent,
            String requestId,
            String result,
            String detail
    ) {
        if (grant == null) {
            return;
        }
        try {
            accessMapper.insertAccessLog(
                    grant.clientId(),
                    grant.externalUserId(),
                    blankToNull(MedicalRecordCodeUtils.normalizeOrEmpty(bah)),
                    blankToNull(MedicalRecordCodeUtils.normalizeOrEmpty(sjh)),
                    truncate(action, 64),
                    imageId,
                    truncate(clientIp, 64),
                    truncate(userAgent, 2000),
                    truncate(result, 20),
                    truncate(requestId, 128),
                    truncate(detail, 500)
            );
        } catch (RuntimeException exception) {
            logger.error("Failed to persist external archive audit log: {}", exception.getMessage(), exception);
        }
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

        String bodyHash = sha256Hex(rawBody == null ? "" : rawBody);
        String canonical = method.toUpperCase(Locale.ROOT) + "\n"
                + path + "\n"
                + timestamp.trim() + "\n"
                + nonce.trim() + "\n"
                + bodyHash;
        String expectedHex = hmacSha256Hex(client.getSecret(), canonical);
        String supplied = signature.trim().replaceFirst("(?i)^sha256=", "").toLowerCase(Locale.ROOT);
        if (!constantTimeEquals(expectedHex, supplied)) {
            throw new BusinessException(401, "外部系统签名无效");
        }

        accessMapper.deleteExpiredNonces();
        int inserted = accessMapper.insertNonce(
                client.getClientId(),
                sha256Hex(nonce.trim()),
                LocalDateTime.now().plusSeconds(properties.getTimestampToleranceSeconds() * 2L)
        );
        if (inserted == 0) {
            throw new BusinessException(409, "签名 nonce 已使用");
        }
        return client;
    }

    private List<ExternalArchiveCaseDTO> resolveCases(ExternalArchiveTicketRequest request) {
        LinkedHashMap<String, ExternalArchiveCaseDTO> resolved = new LinkedHashMap<>();

        if (StringUtils.hasText(request.getIdCard())) {
            String idCard = request.getIdCard().trim();
            if (!idCard.matches("\\d{15}|\\d{17}[0-9Xx]")) {
                throw new BusinessException(400, "身份证号格式不正确");
            }
            List<IdCardArchiveSearchResponse.ArchiveCase> idCases = searchService.getArchiveCasesByID(idCard);
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
        validateCodeIfPresent(rawBah, "病案号");
        validateCodeIfPresent(rawSjh, "上架号");
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
            addCase(resolved, new ExternalArchiveCaseDTO(scan.getBah(), scan.getSjh(), null, null, null));
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

    private ExternalArchiveGrant toGrant(ExternalArchiveStoredGrant stored) {
        return new ExternalArchiveGrant(
                stored.getClientId(),
                stored.getExternalUserId(),
                Boolean.TRUE.equals(stored.getAllowDownload()),
                toEpochMillis(stored.getExpiresAt()),
                List.copyOf(deserializeCases(stored.getGrantJson()))
        );
    }

    private String serializeCases(List<ExternalArchiveCaseDTO> cases) {
        try {
            return objectMapper.writeValueAsString(cases);
        } catch (Exception exception) {
            throw new IllegalStateException("无法序列化外部影像授权范围", exception);
        }
    }

    private List<ExternalArchiveCaseDTO> deserializeCases(String grantJson) {
        try {
            return objectMapper.readValue(grantJson, CASE_LIST_TYPE);
        } catch (Exception exception) {
            throw new IllegalStateException("无法读取外部影像授权范围", exception);
        }
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

    private void validateCodeIfPresent(String code, String label) {
        if (StringUtils.hasText(code) && !MedicalRecordCodeUtils.isSupportedNumericCode(code)) {
            throw new BusinessException(400, label + "必须是 1-8 位数字");
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

    private long toEpochMillis(LocalDateTime value) {
        return value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private <T> List<T> safeList(List<T> value) {
        return value == null ? List.of() : value;
    }

    public record IssuedTicket(String token, ExternalArchiveGrant grant, int expiresIn) {
    }

    public record IssuedSession(String token, ExternalArchiveGrant grant, int expiresIn) {
    }
}
