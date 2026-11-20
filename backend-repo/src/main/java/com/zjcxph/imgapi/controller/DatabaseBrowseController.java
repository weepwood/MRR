package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Permissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.common.ResultCode;
import com.zjcxph.imgapi.dto.resp.browse.DbBrowsePage;
import com.zjcxph.imgapi.dto.resp.browse.DbBrowseTableSchema;
import com.zjcxph.imgapi.dto.resp.browse.DbBrowseTableSummary;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.service.DatabaseBrowseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 受控只读数据库浏览（ADR-0007）。
 *
 * <p>只提供 GET；不存在任何接收 SQL 文本的入口。功能由
 * {@code app.operations.db-browse-enabled} 控制，关闭时返回 404 语义而不是空数据。</p>
 */
@RestController
@RequestMapping("/api/v1/system/database/browse")
@Tag(name = "Database Browse", description = "受控只读数据库浏览（表数据查看，默认关闭）")
@RequirePermissions({Permissions.SYSTEM_DB_BROWSE})
public class DatabaseBrowseController {

    private final DatabaseBrowseService databaseBrowseService;

    public DatabaseBrowseController(DatabaseBrowseService databaseBrowseService) {
        this.databaseBrowseService = databaseBrowseService;
    }

    @Operation(summary = "获取当前账号可浏览的表清单")
    @GetMapping("/tables")
    public Result<List<DbBrowseTableSummary>> tables(HttpServletResponse response) {
        noStore(response);
        return Result.<List<DbBrowseTableSummary>>success("获取表清单成功")
                .data(databaseBrowseService.listTables());
    }

    @Operation(summary = "获取单表结构：列、可过滤、可排序与脱敏标记")
    @GetMapping("/tables/{tableKey}")
    public Result<DbBrowseTableSchema> schema(@PathVariable String tableKey, HttpServletResponse response) {
        noStore(response);
        return Result.<DbBrowseTableSchema>success("获取表结构成功")
                .data(databaseBrowseService.getSchema(tableKey));
    }

    @Operation(summary = "按表查询数据行（游标分页，只读）")
    @GetMapping("/tables/{tableKey}/rows")
    public Result<DbBrowsePage> rows(@PathVariable String tableKey,
                                     @RequestParam(required = false) String filter,
                                     @RequestParam(required = false) String orderBy,
                                     @RequestParam(defaultValue = "desc") String order,
                                     @RequestParam(defaultValue = "1") String page,
                                     @RequestParam(defaultValue = "50") String size,
                                     @RequestParam(required = false) String cursor,
                                     HttpServletResponse response) {
        // 先设缓存头再取数：异常响应同样不允许被缓存。
        noStore(response);
        // 分页参数按字符串接收后自行解析：交给 Spring 的 int 转换会在类型不匹配时
        // 落到全局 500，而契约要求 400 + 3002。
        int pageNo = parseCount(page, ResultCode.DB_BROWSE_PARAMETER_INVALID);
        int pageSize = parseCount(size, ResultCode.DB_BROWSE_PARAMETER_INVALID);
        DbBrowsePage data = databaseBrowseService.readRows(tableKey, filter, orderBy, order, pageNo, pageSize, cursor);
        response.setHeader("X-MRR-Browse-Rows", String.valueOf(data.rows().size()));
        return Result.<DbBrowsePage>success("获取数据成功").data(data);
    }

    private static int parseCount(String raw, ResultCode code) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(code.getCode(), code.getMessage());
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(code.getCode(), code.getMessage());
        }
    }

    /** 敏感响应禁止任何缓存，包括浏览器与中间代理。 */
    private static void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
    }
}
