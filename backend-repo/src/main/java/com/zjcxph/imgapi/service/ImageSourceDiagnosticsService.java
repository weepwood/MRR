package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.utils.MedicalRecordCodeUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 单张影像来源解析诊断。
 *
 * <p>本地资源使用实际 HTTP 状态判断文件是否存在；OSS 使用对象存储 API
 * 检查 Object Key，避免用 GET 预签名地址执行 HEAD 导致签名方法不匹配。</p>
 */
@Service
public class ImageSourceDiagnosticsService {

    private final ScanService scanService;
    private final ImageUrlService imageUrlService;
    private final OssService ossService;
    private final HttpClient httpClient;

    public ImageSourceDiagnosticsService(
            ScanService scanService,
            ImageUrlService imageUrlService,
            OssService ossService
    ) {
        this.scanService = scanService;
        this.imageUrlService = imageUrlService;
        this.ossService = ossService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public Map<String, Object> diagnose(String bah, String sjh, Integer imageId) {
        List<Map<String, Object>> steps = new ArrayList<>();
        String normalizedBah = MedicalRecordCodeUtils.normalizeOrEmpty(bah);
        String normalizedSjh = MedicalRecordCodeUtils.normalizeOrEmpty(sjh);

        steps.add(step("INPUT_NORMALIZE", true, "输入编号已规范化", Map.of(
                "bah", normalizedBah,
                "sjh", normalizedSjh,
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
            scan = scanService.findById(imageId);
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
            scan = scans.isEmpty() ? null : scans.getFirst();
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

        String localUrl = imageUrlService.buildImageUrl(scan);
        HttpProbe localProbe = probeHttpResource(localUrl);
        steps.add(step(
                "LOCAL_SOURCE",
                localProbe.resourceAvailable(),
                localSourceMessage(localUrl, localProbe),
                Map.of(
                        "url", localUrl == null ? "" : localUrl,
                        "serverReachable", localProbe.serverReachable(),
                        "resourceAvailable", localProbe.resourceAvailable(),
                        "statusCode", localProbe.statusCode()
                )
        ));

        boolean hasOssKey = StringUtils.hasText(scan.getOssUrl());
        OssProbe ossProbe = probeOss(scan.getOssUrl());
        steps.add(step(
                "OSS_SOURCE",
                ossProbe.objectExists(),
                ossSourceMessage(hasOssKey, ossProbe),
                Map.of(
                        "ossKey", scan.getOssUrl() == null ? "" : scan.getOssUrl(),
                        "migrationStatus", scan.getMigrationStatus() == null ? "" : scan.getMigrationStatus(),
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
            fallbackReason = hasOssKey ? "OSS_SIGNING_OR_OBJECT_CHECK_FAILED" : "OSS_KEY_MISSING";
        }

        steps.add(step(
                "FINAL_SELECTION",
                StringUtils.hasText(selectedUrl) && selectedReachable,
                StringUtils.hasText(selectedUrl) ? "已确定最终图片来源" : "没有可用的最终图片地址",
                Map.of(
                        "preferredSource", preferredSource,
                        "selectedSource", selectedType,
                        "selectedUrl", selectedUrl == null ? "" : selectedUrl,
                        "reachable", selectedReachable,
                        "fallbackReason", fallbackReason == null ? "" : fallbackReason
                )
        ));

        return diagnosisResult(scan, steps, selectedType, selectedUrl, fallbackReason);
    }

    private HttpProbe probeHttpResource(String url) {
        if (!StringUtils.hasText(url)) {
            return new HttpProbe(false, false, 0);
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            int status = httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            boolean serverReachable = status > 0;
            boolean resourceAvailable = status >= 200 && status < 400;
            return new HttpProbe(serverReachable, resourceAvailable, status);
        } catch (Exception exception) {
            return new HttpProbe(false, false, 0);
        }
    }

    private OssProbe probeOss(String ossKey) {
        if (!StringUtils.hasText(ossKey)) {
            return new OssProbe(false, "");
        }
        try {
            return new OssProbe(ossService.doesObjectExist(ossKey), "");
        } catch (Exception exception) {
            String message = exception.getMessage();
            return new OssProbe(false, message == null ? exception.getClass().getSimpleName() : message);
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
        result.put("selectedUrl", selectedUrl == null ? "" : selectedUrl);
        result.put("fallbackReason", fallbackReason == null ? "" : fallbackReason);
        result.put("steps", steps);
        return result;
    }

    private Map<String, Object> scanDetails(Scan scan) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("id", scan.getId());
        details.put("archiveId", scan.getArchiveId() == null ? "" : scan.getArchiveId());
        details.put("bah", scan.getBah() == null ? "" : scan.getBah());
        details.put("sjh", scan.getSjh() == null ? "" : scan.getSjh());
        details.put("folder", scan.getFolder() == null ? "" : scan.getFolder());
        details.put("filename", scan.getFilename() == null ? "" : scan.getFilename());
        details.put("sourceType", scan.getSourceType() == null ? "" : scan.getSourceType());
        details.put("sourceNode", scan.getSourceNode() == null ? "" : scan.getSourceNode());
        details.put("sourceRef", scan.getSourceRef() == null ? "" : scan.getSourceRef());
        details.put("ossKey", scan.getOssUrl() == null ? "" : scan.getOssUrl());
        details.put("migrationStatus", scan.getMigrationStatus() == null ? "" : scan.getMigrationStatus());
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

    private record HttpProbe(boolean serverReachable, boolean resourceAvailable, int statusCode) {
    }

    private record OssProbe(boolean objectExists, String error) {
    }
}
