package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.dto.resp.BAHStatisticsDTO;
import com.zjcxph.imgapi.dto.resp.DateStatisticsDTO;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.dto.resp.PageResult;
import com.zjcxph.imgapi.entity.Statistics;
import com.zjcxph.imgapi.service.StatisticsService;
import com.zjcxph.imgapi.utils.PaginationUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Statistics Controller
 * 提供 mr_statistics 表的统计查询接口
 */
@RestController
@RequestMapping("/api/v1/statistics")
@Tag(name = "Statistics Management", description = "病案统计管理接口")
@RequirePermissions({"statistics:read"})
public class StatisticsController {

    private static final Logger logger = LoggerFactory.getLogger(StatisticsController.class);

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @Operation(summary = "获取所有统计数据（分页+条件）")
    @GetMapping
    public Result<PageResult<Statistics>> getAllStatistics(
            @Parameter(description = "页码", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小", example = "100")
            @RequestParam(defaultValue = "100") int size,
            @Parameter(description = "关键字，匹配 cid/openerNo/date/type", example = "CT")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "病案号模糊匹配", example = "0078")
            @RequestParam(required = false) String bah,
            @Parameter(description = "上架号模糊匹配", example = "SJH001")
            @RequestParam(required = false) String sjh,
            @Parameter(description = "类型精确匹配", example = "普通")
            @RequestParam(required = false) String type,
            @Parameter(description = "开始日期，格式 yyyy-MM-dd", example = "2024-01-01")
            @RequestParam(required = false) String startDate,
            @Parameter(description = "结束日期，格式 yyyy-MM-dd", example = "2024-12-31")
            @RequestParam(required = false) String endDate,
            @Parameter(description = "排序字段：bah/cid/openerNo/date/type/pages/sjh", example = "date")
            @RequestParam(required = false) String sortBy,
            @Parameter(description = "排序方向：asc/desc", example = "desc")
            @RequestParam(required = false) String sortOrder) {

        logger.info("获取统计数据：page={}, size={}, keyword={}, bah={}, sjh={}, type={}, startDate={}, endDate={}, sortBy={}, sortOrder={}",
                page, size, keyword, bah, sjh, type, startDate, endDate, sortBy, sortOrder);

        PaginationUtils.validatePageParams(page, size);
        if (size > 1000) {
            size = 1000;
        }

        String normalizedKeyword = normalize(keyword);
        String normalizedBah = normalize(bah);
        String normalizedSjh = normalize(sjh);
        String normalizedType = normalize(type);
        String normalizedStartDate = normalize(startDate);
        String normalizedEndDate = normalize(endDate);
        String normalizedSortBy = normalizeSortBy(sortBy);
        String normalizedSortOrder = normalizeSortOrder(sortOrder);

        List<Statistics> statistics = statisticsService.findWithConditionAndPagination(
                page,
                size,
                normalizedKeyword,
                normalizedBah,
                normalizedSjh,
                normalizedType,
                normalizedStartDate,
                normalizedEndDate,
                normalizedSortBy,
                normalizedSortOrder
        );
        Long totalCount = statisticsService.getTotalCountByCondition(
                normalizedKeyword,
                normalizedBah,
                normalizedSjh,
                normalizedType,
                normalizedStartDate,
                normalizedEndDate
        );

        PageResult<Statistics> pageResult = PageResult.of(statistics, totalCount, page, size);
        return Result.success(pageResult);
    }

    @Operation(summary = "根据病案号查询统计数据")
    @GetMapping("/bah/{bah}")
    public Result<List<Statistics>> getStatisticsByBah(
            @PathVariable
            @Parameter(description = "病案号", example = "00789508")
            String bah) {
        logger.info("查询病案号 {} 的统计数据", bah);

        if (bah == null || bah.isEmpty()) {
            return Result.fail("病案号不能为空");
        }

        List<Statistics> statistics = statisticsService.findByBah(bah);
        return Result.success(statistics);
    }

    @Operation(summary = "根据日期查询统计数据")
    @GetMapping("/date/{date}")
    public Result<List<Statistics>> getStatisticsByDate(
            @PathVariable
            @Parameter(description = "日期，格式：YYYY-MM-DD 或 YYYY.MM.DD", example = "2024-01-15")
            String date) {
        logger.info("查询日期 {} 的统计数据", date);

        if (date == null || date.isEmpty()) {
            return Result.fail("日期不能为空");
        }

        List<Statistics> statistics = statisticsService.findByDate(date);
        logger.info("查询到 {} 条记录", statistics.size());
        return Result.success(statistics);
    }

    @Operation(summary = "统计每个病案号的记录数和总页数")
    @GetMapping("/bah-summary")
    public Result<List<BAHStatisticsDTO>> getBAHStatistics() {
        logger.info("统计每个病案号的记录数和总页数");
        List<BAHStatisticsDTO> bahStats = statisticsService.getBAHStatistics();
        return Result.success(bahStats);
    }

    @Operation(summary = "统计每个日期的记录数和总页数")
    @GetMapping("/date-summary")
    public Result<List<DateStatisticsDTO>> getDateStatistics() {
        logger.info("统计每个日期的记录数和总页数");
        List<DateStatisticsDTO> dateStats = statisticsService.getDateStatistics();
        return Result.success(dateStats);
    }

    @Operation(summary = "按条件统计每日数据（支持日期范围和类型）")
    @GetMapping("/date-summary/condition")
    public Result<List<DateStatisticsDTO>> getDateStatisticsByCondition(
            @Parameter(description = "开始日期，格式：YYYY-MM-DD", example = "2024-01-01")
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @Parameter(description = "结束日期，格式：YYYY-MM-DD", example = "2024-12-31")
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @Parameter(description = "类型", example = "type1")
            @RequestParam(required = false) String type) {

        logger.info("按条件统计：startDate={}, endDate={}, type={}", startDate, endDate, type);

        String startDateStr = (startDate != null) ? startDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) : null;
        String endDateStr = (endDate != null) ? endDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) : null;

        if (startDate == null && endDate != null) {
            startDateStr = endDate.withMonth(1).withDayOfMonth(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }

        if (startDate != null && endDate == null) {
            endDateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }

        List<DateStatisticsDTO> dateStats = statisticsService.getDateStatisticsByCondition(startDateStr, endDateStr, type);
        return Result.success(dateStats);
    }

    @Operation(summary = "获取总体统计信息")
    @GetMapping("/summary")
    public Result<Map<String, Object>> getTotalStatistics() {
        logger.info("获取总体统计信息");

        Map<String, Object> summary = new HashMap<>();
        Map<String, Object> totalStats = statisticsService.getTotalStatistics();
        summary.put("total", totalStats);

        Long uniqueBAHCount = statisticsService.getUniqueBAHCount();
        summary.put("uniqueBAHCount", uniqueBAHCount);

        List<Map<String, Object>> typeStats = statisticsService.getTypeStatistics();
        summary.put("byType", typeStats);

        return Result.success(summary);
    }

    @Operation(summary = "按类型统计")
    @GetMapping("/type-summary")
    public Result<List<Map<String, Object>>> getTypeStatistics() {
        logger.info("按类型统计");
        List<Map<String, Object>> typeStats = statisticsService.getTypeStatistics();
        return Result.success(typeStats);
    }

    @Operation(summary = "获取综合统计面板数据")
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> getDashboardData() {
        logger.info("获取综合统计面板数据");

        Map<String, Object> dashboard = new HashMap<>();

        Map<String, Object> totalStats = statisticsService.getTotalStatistics();
        dashboard.put("overview", totalStats);

        dashboard.put("uniqueBAHCount", statisticsService.getUniqueBAHCount());

        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
        List<DateStatisticsDTO> recentDateStats = statisticsService.getDateStatisticsByCondition(
                thirtyDaysAgo.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                null
        );
        dashboard.put("recentTrend", recentDateStats);

        List<BAHStatisticsDTO> bahStats = statisticsService.getBAHStatistics();
        if (bahStats.size() > 10) {
            bahStats = bahStats.subList(0, 10);
        }
        dashboard.put("topBAH", bahStats);

        return Result.success(dashboard);
    }

    @Operation(summary = "导出统计明细 CSV")
    @GetMapping("/export/csv")
    public void exportCsv(
            @Parameter(description = "关键字，匹配 cid/openerNo/date/type")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "病案号模糊匹配")
            @RequestParam(required = false) String bah,
            @Parameter(description = "上架号模糊匹配")
            @RequestParam(required = false) String sjh,
            @Parameter(description = "类型精确匹配")
            @RequestParam(required = false) String type,
            @Parameter(description = "开始日期")
            @RequestParam(required = false) String startDate,
            @Parameter(description = "结束日期")
            @RequestParam(required = false) String endDate,
            HttpServletResponse response) {

        String normalizedKeyword = normalize(keyword);
        String normalizedBah = normalize(bah);
        String normalizedSjh = normalize(sjh);
        String normalizedType = normalize(type);
        String normalizedStartDate = normalize(startDate);
        String normalizedEndDate = normalize(endDate);

        int exportLimit = 100000;
        List<Statistics> list = statisticsService.findWithConditionAndPagination(
                1, exportLimit, normalizedKeyword, normalizedBah, normalizedSjh,
                normalizedType, normalizedStartDate, normalizedEndDate, "date", "asc");
        if (list.size() >= exportLimit) {
            logger.warn("统计数据导出达到上限 {} 条，数据可能不完整", exportLimit);
        }

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"statistics.csv\"");
        response.setCharacterEncoding("UTF-8");

        try (OutputStreamWriter writer = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8)) {
            writer.write('\uFEFF');
            writer.write("病案号,设备,负责人,日期,类型,页数,上架号\n");
            for (Statistics s : list) {
                writer.write(escapeCsv(s.getBah()));
                writer.write(',');
                writer.write(escapeCsv(s.getCid()));
                writer.write(',');
                writer.write(escapeCsv(s.getOpenerNo()));
                writer.write(',');
                writer.write(escapeCsv(s.getDate()));
                writer.write(',');
                writer.write(escapeCsv(s.getType()));
                writer.write(',');
                writer.write(s.getPages() != null ? String.valueOf(s.getPages()) : "");
                writer.write(',');
                writer.write(escapeCsv(s.getSjh()));
                writer.write('\n');
            }
            writer.flush();
        } catch (Exception e) {
            logger.error("导出 CSV 失败", e);
            response.setStatus(500);
        }
    }

    private String escapeCsv(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeSortBy(String sortBy) {
        String value = normalize(sortBy);
        if (value == null) {
            return "date";
        }
        switch (value) {
            case "bah":
            case "cid":
            case "openerNo":
            case "date":
            case "type":
            case "pages":
            case "sjh":
                return value;
            default:
                return "date";
        }
    }

    private String normalizeSortOrder(String sortOrder) {
        String value = normalize(sortOrder);
        if (value == null) {
            return "desc";
        }
        if ("asc".equalsIgnoreCase(value) || "ascending".equalsIgnoreCase(value)) {
            return "asc";
        }
        return "desc";
    }
}
