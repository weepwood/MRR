package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.mapper.ImageContentMapper;
import com.zjcxph.imgapi.storage.ResolvedImageStorage;
import com.zjcxph.imgapi.utils.MedicalRecordCodeUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 单张影像来源解析诊断。
 *
 * <p>direct 保留浏览器路径 HEAD/OSS 对象检查，拒绝重定向；
 * gateway 复用真实受控来源解析和流打开/关闭，不签发 URL，不验证完整影像内容。</p>
 */
@Service
public class ImageSourceDiagnosticsService {

    private final ScanService scanService;
    private final ImageUrlService imageUrlService;
    private final OssService ossService;
    private final HttpClient httpClient;
    private final ImageContentMapper imageContentMapper;
    private final ResolvedImageStorage resolvedImageStorage;

    public ImageSourceDiagnosticsService(
            ScanService scanService,
            ImageUrlService imageUrlService,
            OssService ossService,
            ImageContentMapper imageContentMapper,
            ResolvedImageStorage resolvedImageStorage
    ) {
        this.scanService = scanService;
        this.imageUrlService = imageUrlService;
        this.ossService = ossService;
        this.imageContentMapper = imageContentMapper;
        this.resolvedImageStorage = resolvedImageStorage;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** Explicit single-image verification, not an archive-level integrity result. */
    public Map<String, Object> verifyContent(Integer imageId) {
        if (imageId == null || imageId <= 0) {
            throw new com.zjcxph.imgapi.exception.BusinessException(400, "请输入有效图片 ID");
        }
        Scan scan = imageContentMapper.findActiveById(imageId);
        if (scan == null) {
            throw new com.zjcxph.imgapi.exception.BusinessException(404, "未找到有效图片");
        }
        var verification = resolvedImageStorage.verifyContent(PathDO.fromScan(scan), scan.getChecksumMd5());
        return Map.of("imageId", imageId, "status", verification.status(), "bytesRead", verification.bytesRead(),
                "checkedAt", Instant.now().toString(), "scope", "SINGLE_IMAGE_BOUNDED");
    }

    public Map<String, Object> diagnose(String bah, String sjh, Integer imageId) {
        List<Map<String, Object>> steps = new ArrayList<>();
        String normalizedBah = MedicalRecordCodeUtils.normalizeOrEmpty(bah);
        String normalizedSjh = MedicalRecordCodeUtils.normalizeOrEmpty(sjh);

        steps.add(step("INPUT_NORMALIZE", true, "输入编号已规范化", Map.of(
                "bah", com.zjcxph.imgapi.utils.AuditPayloadSanitizer.protectedValue(normalizedBah),
                "sjh", com.zjcxph.imgapi.utils.AuditPayloadSanitizer.protectedValue(normalizedSjh),
                "imageId", imageId == null ? "" : imageId
        )));

        if (imageId == null && normalizedBah.isBlank() && normalizedSjh.isBlank()) {
            steps.add(step("INPUT_VALIDATE", false, "病案号、上架号、图片 ID 至少填写一项", Map.of()));
            return diagnosisResult(null, steps, null, null, null);
        }
        if (imageId == null && MedicalRecordCodeUtils.requiresSjhForBah(normalizedBah)
                && normalizedSjh.isBlank()) {
            steps.add(step("ARCHIVE_RULE", false, "病案号达到 10000000 后必须同时提供上架号", Map.of()));
            return diagnosisResult(null, steps, null, null, null);
        }

        Scan scan;
        if (imageId != null) {
            scan = imageId > 0 ? imageContentMapper.findActiveById(imageId) : null;
            steps.add(step(
                    "IMAGE_LOOKUP",
                    scan != null,
                    scan == null ? "未找到指定图片" : "已按图片 ID 找到扫描记录",
                    scan == null ? Map.of("imageId", imageId) : scanDetails(scan)
            ));
        } else {
            List<Scan> scans = scanService.getImageListByCode(
                    normalizedBah,
                    MedicalRecordCodeUtils.toSearchTerm(bah),
                    normalizedSjh,
                    MedicalRecordCodeUtils.toSearchTerm(sjh)
            );
            scan = scans.isEmpty() ? null : imageContentMapper.findActiveById(scans.getFirst().getId());
            steps.add(step(
                    "ARCHIVE_LOOKUP",
                    scan != null,
                    scan == null ? "未找到匹配的有效图片" : "已找到病案图片，默认诊断第一页",
                    Map.of("matchedImages", scans.size())
            ));
        }

        if (scan == null) {
            return diagnosisResult(null, steps, null, null, null);
        }

        boolean archiveLinked = scan.getArchiveId() != null;
        steps.add(step(
                "ARCHIVE_LINK",
                archiveLinked,
                archiveLinked ? "图片已关联病案主档" : "图片尚未关联 archive_id，将依赖兼容查询",
                Map.of("archiveId", scan.getArchiveId() == null ? "" : scan.getArchiveId())
        ));

        if (imageUrlService.isGatewayDelivery()) {
            return diagnoseGateway(scan, steps);
        }

        String localUrl = imageUrlService.buildImageUrl(scan);
        HttpProbe localProbe = probeHttpResource(localUrl);
        steps.add(step(
                "LOCAL_SOURCE",
                localProbe.resourceAvailable(),
                localSourceMessage(localUrl, localProbe),
                Map.of(
                        "url", "[REDACTED]",
                        "serverReachable", localProbe.serverReachable(),
                        "resourceAvailable", localProbe.resourceAvailable(),
                        "statusCode", localProbe.statusCode(),
                        "error", localProbe.error()
                )
        ));

        boolean hasOssKey = StringUtils.hasText(scan.getOssUrl());
        OssProbe ossProbe = probeOss(scan.getOssUrl());
        steps.add(step(
                "OSS_SOURCE",
                ossProbe.objectExists(),
                ossSourceMessage(hasOssKey, ossProbe),
                Map.of(
                        "ossKey", "[REDACTED]",
                        "migrationStatus", safeMigrationStatus(scan.getMigrationStatus()),
                        "objectExists", ossProbe.objectExists(),
                        "error", ossProbe.error()
                )
        ));

        String preferredSource = imageUrlService.getEffectiveImageSource();
        String selectedUrl = imageUrlService.buildPreferredImageUrl(scan);
        String selectedType;
        if (!StringUtils.hasText(selectedUrl)) {
            selectedType = "NONE";
        } else if (Objects.equals(selectedUrl, localUrl)) {
            selectedType = "LOCAL";
        } else {
            selectedType = "OSS";
        }

        boolean selectedReachable = switch (selectedType) {
            case "LOCAL" -> localProbe.resourceAvailable();
            case "OSS" -> ossProbe.objectExists();
            default -> false;
        };
        String fallbackReason = null;
        if ("oss".equalsIgnoreCase(preferredSource) && "LOCAL".equals(selectedType)) {
            fallbackReason = hasOssKey ? "OSS_SIGNING_FAILED" : "OSS_KEY_MISSING";
        }

        steps.add(step(
                "FINAL_SELECTION",
                StringUtils.hasText(selectedUrl) && selectedReachable,
                StringUtils.hasText(selectedUrl) ? "已确定最终图片来源" : "没有可用的最终图片地址",
                Map.of(
                        "preferredSource", preferredSource,
                        "selectedSource", selectedType,
                        "selectedUrl", "[REDACTED]",
                        "reachable", selectedReachable,
                        "fallbackReason", fallbackReason == null ? "" : fallbackReason
                )
        ));

        return diagnosisResult(scan, steps, selectedType, selectedUrl, fallbackReason);
    }

    private Map<String, Object> diagnoseGateway(Scan scan, List<Map<String, Object>> steps) {
        var probe = resolvedImageStorage.probe(PathDO.fromScan(scan));
        for (var attempt : probe.attempts()) {
            steps.add(step("GATEWAY_SOURCE_" + attempt.source(), attempt.available(),
                    attempt.available() ? "受控来源已打开" : "受控来源打开失败",
                    Map.of("source", attempt.source(), "error", attempt.error(), "url", "[REDACTED]")));
        }
        String fallback = probe.available() && probe.attempts().size() > 1 ? "CONTROLLED_SOURCE_FALLBACK" : "";
        steps.add(step("FINAL_SELECTION", probe.available(),
                probe.available() ? "已打开并关闭受控影像流，未验证完整内容" : "受控影像流不可用",
                Map.of("preferredSource", "GATEWAY_POLICY", "selectedSource", probe.selectedSource(),
                        "selectedUrl", "[REDACTED]", "reachable", probe.available(),
                        "error", probe.error(), "fallbackReason", fallback, "probeScope", "OPEN_AND_CLOSE")));
        return diagnosisResult(scan, steps, probe.selectedSource(), null, fallback);
    }

    private HttpProbe probeHttpResource(String url) {
        if (!StringUtils.hasText(url)) {
            return new HttpProbe(false, false, 0, "NO_URL");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            int status = httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            boolean serverReachable = status > 0;
            boolean resourceAvailable = status >= 200 && status < 300;
            String error = resourceAvailable ? "" : switch (status) {
                case 401, 403 -> "ACCESS_DENIED";
                case 404, 410 -> "NOT_FOUND";
                case 408, 504 -> "TIMEOUT";
                case 429 -> "SOURCE_BUSY";
                default -> status >= 300 && status < 400 ? "REDIRECT_REJECTED" : "HTTP_ERROR";
            };
            return new HttpProbe(serverReachable, resourceAvailable, status, error);
        } catch (HttpTimeoutException exception) {
            return new HttpProbe(false, false, 0, "TIMEOUT");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new HttpProbe(false, false, 0, "INTERRUPTED");
        } catch (Exception exception) {
            return new HttpProbe(false, false, 0, "SOURCE_UNAVAILABLE");
        }
    }

    private OssProbe probeOss(String ossKey) {
        if (!StringUtils.hasText(ossKey)) {
            return new OssProbe(false, "");
        }
        try {
            boolean exists = ossService.doesObjectExist(ossKey);
            return new OssProbe(exists, exists ? "" : "NOT_FOUND");
        } catch (com.zjcxph.imgapi.exception.OssOperationException exception) {
            return new OssProbe(false, exception.getType().name());
        } catch (Exception exception) {
            return new OssProbe(false, "SOURCE_UNAVAILABLE");
        }
    }

    private String localSourceMessage(String localUrl, HttpProbe probe) {
        if (!StringUtils.hasText(localUrl)) {
            return "无法构造本地图片地址";
        }
        if (probe.resourceAvailable()) {
            return "本地/Nginx 图片文件可访问";
        }
        if (probe.serverReachable()) {
            return "本地/Nginx 服务器可连接，但图片文件不可访问";
        }
        return "本地/Nginx 图片服务器不可连接";
    }

    private String ossSourceMessage(boolean hasOssKey, OssProbe probe) {
        if (!hasOssKey) {
            return "扫描记录没有 OSS Key，将使用本地来源";
        }
        if (probe.objectExists()) {
            return "OSS Object Key 对应文件存在";
        }
        return "扫描记录包含 OSS Key，但对象不存在或 OSS 检查失败";
    }

    private Map<String, Object> diagnosisResult(
            Scan scan,
            List<Map<String, Object>> steps,
            String selectedSource,
            String selectedUrl,
            String fallbackReason
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("diagnosedAt", Instant.now().toString());
        result.put("found", scan != null);
        result.put("scan", scan == null ? Map.of() : scanDetails(scan));
        result.put("selectedSource", selectedSource == null ? "" : selectedSource);
        result.put("selectedUrl", "");
        result.put("fallbackReason", fallbackReason == null ? "" : fallbackReason);
        result.put("steps", steps);
        return result;
    }

    private Map<String, Object> scanDetails(Scan scan) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("id", scan.getId());
        details.put("archiveId", scan.getArchiveId() == null ? "" : scan.getArchiveId());
        details.put("bah", "[REDACTED]");
        details.put("sjh", "[REDACTED]");
        details.put("folder", "[REDACTED]");
        details.put("filename", "[REDACTED]");
        String type = scan.getSourceType();
        details.put("sourceType", type == null ? "" : Set.of("AUTO", "LOCAL", "NAS", "NGINX", "HTTP", "OSS").contains(type) ? type : "UNKNOWN");
        details.put("sourceNode", "[REDACTED]");
        details.put("sourceRef", "[REDACTED]");
        details.put("ossKey", "[REDACTED]");
        details.put("migrationStatus", safeMigrationStatus(scan.getMigrationStatus()));
        return details;
    }

    private Map<String, Object> step(String code, boolean success, String message, Object details) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("code", code);
        step.put("success", success);
        step.put("message", message);
        step.put("details", details);
        return step;
    }

    private String safeMigrationStatus(String status) {
        return status == null ? "" : Set.of("not_migrated", "migrating", "migrated", "verified", "failed",
                "retry_wait", "waiting_sjh").contains(status) ? status : "UNKNOWN";
    }

    private record HttpProbe(boolean serverReachable, boolean resourceAvailable, int statusCode, String error) {
    }

    private record OssProbe(boolean objectExists, String error) {
    }
}
