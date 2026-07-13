package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.config.ImageProperties;
import com.zjcxph.imgapi.dto.resp.BAHDataResponseDTO;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.utils.MedicalRecordCodeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * 图片 URL 构建服务。
 * <p>
 * 从原 ImageController 抽取，承担以下职责：
 * 1. 根据病案号、病人序号、文件夹等构建图片访问 URL；
 * 2. 根据文件夹日期段决定使用哪台图片服务器（BAIMG01/02/03/默认）；
 * 3. 批量将 Scan Entity 转换为 BAHDataResponseDTO（含 OSS 签名 URL）。
 * </p>
 * <p>
 * 原 ImageController 的 determineImageUrl 内嵌大量硬编码日期集合，每次新增文件夹都要改代码。
 * 这里保留原逻辑，后续可演进为配置驱动或策略模式（见审查报告 02-规范与修复方案.md 2.1）。
 * </p>
 */
@Service
public class ImageUrlService {

    private static final Logger logger = LoggerFactory.getLogger(ImageUrlService.class);

    private final ImageProperties imageProperties;
    private final OssService ossService;

    public ImageUrlService(ImageProperties imageProperties, OssService ossService) {
        this.imageProperties = imageProperties;
        this.ossService = ossService;
    }

    /**
     * 根据扫描记录构建本地图片访问 URL。
     */
    public String buildImageUrl(Scan scan) {
        String folder = scan.getFolder();
        String brxh = scan.getBrxh();
        if (brxh == null) {
            return null;
        }
        String paddedBah = normalizeCode(scan.getBah());
        String folderKey = paddedBah.compareTo("10000000") >= 0 ? scan.getSjh() : brxh;
        if (folderKey == null || folderKey.isBlank()) {
            return null;
        }
        if (folder == null || folder.isBlank()) {
            return imageProperties.getServerUrlDefault() + "/" + folderKey + "-" +
                    scan.getBah() + "/" + scan.getFilename();
        }
        String imgUrl = determineImageUrl(folder);
        return imgUrl + "/" + extractYearMonth(folder) + "/" + folder + "/" +
                folderKey + "-" + scan.getBah() + "/" + scan.getFilename();
    }

    /**
     * 根据文件夹名（日期段）决定使用哪台图片服务器。
     */
    String determineImageUrl(String folder) {
        if (folder == null || folder.isBlank()) {
            return imageProperties.getServerUrlDefault();
        }

        Set<String> baImg03Exact = Set.of(
                "2026.06.05", "2026.06.08", "2026.06.09"
        );
        if (baImg03Exact.contains(folder)) {
            return imageProperties.getServerUrlBa03();
        }

        Set<String> baImg02YearMonth = Set.of(
                "2025.08", "2025.09", "2025.10", "2025.11", "2025.12",
                "2026.01", "2026.02", "2026.03", "2026.04", "2026.05", "2026.06"
        );
        String yearMonth = extractYearMonth(folder);
        if (baImg02YearMonth.contains(yearMonth)) {
            return imageProperties.getServerUrlBa02();
        }

        Set<String> baImg01YearMonth = Set.of(
                "24.04", "24.05", "24.06", "24.07", "24.08", "24.09",
                "24.10", "24.11", "25.07", "25.08"
        );
        if (baImg01YearMonth.contains(yearMonth)) {
            return imageProperties.getServerUrlBa01();
        }

        return imageProperties.getUrl();
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
     * 批量将 Scan 列表转换为 BAHDataResponseDTO 列表（含本地图片 URL 与 OSS 签名 URL）。
     * <p>
     * 消除原 ImageController.getDataByBAH 与 searchByCode 中重复的 DTO 构建逻辑。
     * </p>
     *
     * @param scans           扫描记录列表
     * @param ossUrlResolver  OSS 签名 URL 解析函数（传入 scan，返回签名 URL；若不需要可传 null）
     */
    public List<BAHDataResponseDTO> toDtoList(List<Scan> scans, Function<Scan, String> ossUrlResolver) {
        if (scans == null || scans.isEmpty()) {
            return new ArrayList<>();
        }
        List<BAHDataResponseDTO> items = new ArrayList<>(scans.size());
        for (Scan scan : scans) {
            BAHDataResponseDTO dto = new BAHDataResponseDTO();
            org.springframework.beans.BeanUtils.copyProperties(scan, dto);
            dto.setBah(MedicalRecordCodeUtils.normalize(scan.getBah()));
            dto.setSjh(MedicalRecordCodeUtils.normalize(scan.getSjh()));
            dto.setImg_url(buildImageUrl(scan));

            if (ossUrlResolver != null && scan.getOssUrl() != null && !scan.getOssUrl().isBlank()) {
                try {
                    dto.setOssUrl(ossUrlResolver.apply(scan));
                } catch (Exception e) {
                    logger.warn("生成 OSS 签名 URL 失败 scan {}: {}", scan.getId(), e.getMessage());
                }
            }
            items.add(dto);
        }
        return items;
    }

    /**
     * 便捷重载：使用默认的 OssService 生成签名 URL。
     */
    public List<BAHDataResponseDTO> toDtoList(List<Scan> scans) {
        return toDtoList(scans, scan -> ossService.generatePresignedUrl(scan.getOssUrl()));
    }
}
