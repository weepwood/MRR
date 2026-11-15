package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.config.ImageProperties;
import com.zjcxph.imgapi.dto.resp.BAHDataResponseDTO;
import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.setting.ImageDirectoryRoutingRules;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.utils.MedicalRecordCodeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 图片 URL 构建服务。
 *
 * <p>系统设置 {@code imageSource} 控制影像档案默认从本地或 OSS 获取：
 * 未设置、设置为空或设置为非法值时均回退为 {@code local}。</p>
 */
@Service
public class ImageUrlService {

    public static final String IMAGE_SOURCE_SETTING_KEY = "imageSource";
    public static final String IMAGE_SOURCE_LOCAL = "local";
    public static final String IMAGE_SOURCE_OSS = "oss";

    private static final Logger logger = LoggerFactory.getLogger(ImageUrlService.class);

    private final ImageProperties imageProperties;
    private final OssService ossService;
    private final SystemSettingService systemSettingService;

    public ImageUrlService(ImageProperties imageProperties,
                           OssService ossService,
                           SystemSettingService systemSettingService) {
        this.imageProperties = imageProperties;
        this.ossService = ossService;
        this.systemSettingService = systemSettingService;
    }

    /**
     * 根据扫描记录构建本地图片访问 URL。
     */
    public String buildImageUrl(Scan scan) {
        if (scan == null) {
            return null;
        }
        return buildImageUrl(
                scan.getFolder(),
                scan.getFilename(),
                scan.getBrxh(),
                scan.getBah(),
                scan.getSjh());
    }

    /**
     * 为后台导出构建与前端展示完全一致的 Nginx 静态图片 URL。
     */
    public String buildImageUrl(PathDO image) {
        if (image == null) {
            return null;
        }
        return buildImageUrl(
                image.getFolder(),
                image.getFilename(),
                image.getBrxh(),
                image.getBah(),
                image.getSjh());
    }

    private String buildImageUrl(String folder,
                                 String filename,
                                 String brxh,
                                 String bah,
                                 String sjh) {
        return buildImageUrl(folder, filename, brxh, bah, sjh,
                folder == null || folder.isBlank() ? null : loadRoutingRules());
    }

    private String buildImageUrl(String folder, String filename, String brxh, String bah, String sjh,
                                 ImageDirectoryRoutingRules routing) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        boolean useSjh = MedicalRecordCodeUtils.requiresSjhForBah(bah);
        String folderKey = useSjh ? sjh : brxh;
        if (folderKey == null || folderKey.isBlank()) {
            return null;
        }
        if (folder == null || folder.isBlank()) {
            return joinUrl(
                    imageProperties.getServerUrlDefault(),
                    folderKey + "-" + bah,
                    filename);
        }
        return joinUrl(
                determineImageUrl(folder, routing),
                extractYearMonth(folder),
                folder,
                folderKey + "-" + bah,
                filename);
    }

    private String joinUrl(String base, String... segments) {
        if (base == null || base.isBlank()) {
            return null;
        }
        StringBuilder result = new StringBuilder(base.trim().replaceAll("/+$", ""));
        for (String segment : segments) {
            if (segment == null || segment.isBlank()) {
                return null;
            }
            result.append('/').append(segment.trim().replaceAll("^/+|/+$", ""));
        }
        return result.toString();
    }

    /**
     * 根据系统设置返回当前首选图片 URL。
     * OSS 模式下，记录未迁移、签名为空或签名失败时自动回退本地 URL。
     */
    public String buildPreferredImageUrl(Scan scan) {
        if (!isOssPreferred() || scan == null || scan.getOssUrl() == null || scan.getOssUrl().isBlank()) {
            return buildImageUrl(scan);
        }
        try {
            String signedUrl = ossService.generatePresignedUrl(scan.getOssUrl());
            if (signedUrl != null && !signedUrl.isBlank()) {
                return signedUrl;
            }
        } catch (Exception exception) {
            logger.warn("生成 OSS 签名 URL 失败，回退本地图片: scan={}, reason={}",
                    scan.getId(), exception.getMessage());
        }
        return buildImageUrl(scan);
    }

    /**
     * 根据文件夹名（日期段）决定使用哪台本地图片服务器。
     */
    String determineImageUrl(String folder) {
        return determineImageUrl(folder, folder == null || folder.isBlank() ? null : loadRoutingRules());
    }

    private String determineImageUrl(String folder, ImageDirectoryRoutingRules routing) {
        if (folder == null || folder.isBlank()) {
            return configuredOrFallback(imageProperties.getServerUrlDefault());
        }
        String node = routing.nodeFor(folder);
        String url = switch (node) {
            case "default" -> imageProperties.getServerUrlDefault();
            case "ba01" -> imageProperties.getServerUrlBa01();
            case "ba02" -> imageProperties.getServerUrlBa02();
            case "ba03" -> imageProperties.getServerUrlBa03();
            default -> imageProperties.getUrl();
        };
        if (routing.inherited()) {
            return configuredOrFallback(url);
        }
        if (url == null || url.isBlank()) {
            throw new BusinessException(503, "目录路由目标节点未配置，请联系管理员");
        }
        return url;
    }

    private ImageDirectoryRoutingRules loadRoutingRules() {
        try {
            return ImageDirectoryRoutingRules.parse(systemSettingService.getSetting(ImageDirectoryRoutingRules.KEY));
        } catch (Exception exception) {
            // 不记录覆盖值、底层异常或路径；配置故障不能默认为另一个影像节点。
            logger.warn("目录路由配置不可用");
            throw new BusinessException(503, "目录路由配置不可用，请联系管理员");
        }
    }

    private String configuredOrFallback(String configuredUrl) {
        return configuredUrl == null || configuredUrl.isBlank()
                ? imageProperties.getUrl()
                : configuredUrl;
    }

    /**
     * 从日期字符串提取年月部分（如 "2026.06.05" → "2026.06"）。
     */
    public static String extractYearMonth(String dateStr) {
        if (dateStr == null) {
            throw new IllegalArgumentException("dateStr must not be null");
        }
        String[] parts = dateStr.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid date format: " + dateStr);
        }
        return parts[0] + "." + parts[1];
    }

    /**
     * 将病案号或上架号规范化为 8 位（左侧补零）。
     */
    public static String normalizeCode(String code) {
        return MedicalRecordCodeUtils.normalizeOrEmpty(code);
    }

    /**
     * 批量将 Scan 转换为前端 DTO。
     *
     * <p>{@code img_url} 始终表示当前系统设置选中的有效 URL：
     * 本地模式直接返回本地 URL；OSS 模式返回签名 URL，并在缺失或失败时回退本地。
     * {@code ossUrl} 仅在实际使用 OSS URL 时返回，避免本地模式为每张图片生成签名。</p>
     */
    public List<BAHDataResponseDTO> toDtoList(List<Scan> scans, Function<Scan, String> ossUrlResolver) {
        if (scans == null || scans.isEmpty()) {
            return new ArrayList<>();
        }

        boolean useOss = isOssPreferred();
        List<BAHDataResponseDTO> items = new ArrayList<>(scans.size());
        ImageDirectoryRoutingRules routing = null;
        for (Scan scan : scans) {
            BAHDataResponseDTO dto = new BAHDataResponseDTO();
            org.springframework.beans.BeanUtils.copyProperties(scan, dto);
            dto.setBah(MedicalRecordCodeUtils.normalize(scan.getBah()));
            dto.setSjh(MedicalRecordCodeUtils.normalize(scan.getSjh()));

            String selectedUrl = null;
            String signedOssUrl = null;
            if (useOss && ossUrlResolver != null && scan.getOssUrl() != null && !scan.getOssUrl().isBlank()) {
                try {
                    String resolvedOssUrl = ossUrlResolver.apply(scan);
                    if (resolvedOssUrl != null && !resolvedOssUrl.isBlank()) {
                        signedOssUrl = resolvedOssUrl;
                        selectedUrl = resolvedOssUrl;
                    }
                } catch (Exception exception) {
                    logger.warn("生成 OSS 签名 URL 失败，回退本地图片: scan={}, reason={}",
                            scan.getId(), exception.getMessage());
                }
            }

            if (selectedUrl == null) {
                if (routing == null && scan.getFolder() != null && !scan.getFolder().isBlank()) {
                    routing = loadRoutingRules();
                }
                selectedUrl = buildImageUrl(scan.getFolder(), scan.getFilename(), scan.getBrxh(),
                        scan.getBah(), scan.getSjh(), routing);
            }
            dto.setImg_url(selectedUrl);
            dto.setOssUrl(signedOssUrl);
            items.add(dto);
        }
        return items;
    }

    /**
     * 便捷重载：使用默认 OssService 生成签名 URL。
     */
    public List<BAHDataResponseDTO> toDtoList(List<Scan> scans) {
        return toDtoList(scans, scan -> ossService.generatePresignedUrl(scan.getOssUrl()));
    }

    public String getEffectiveImageSource() {
        try {
            String configured = systemSettingService.getSetting(IMAGE_SOURCE_SETTING_KEY);
            return IMAGE_SOURCE_OSS.equalsIgnoreCase(configured)
                    ? IMAGE_SOURCE_OSS
                    : IMAGE_SOURCE_LOCAL;
        } catch (Exception exception) {
            logger.warn("读取图片来源设置失败，回退本地图片: {}", exception.getMessage());
            return IMAGE_SOURCE_LOCAL;
        }
    }

    private boolean isOssPreferred() {
        return IMAGE_SOURCE_OSS.equals(getEffectiveImageSource());
    }
}
