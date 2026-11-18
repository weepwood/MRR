package com.zjcxph.imgapi.dto.resp.browse;

import java.util.List;
import java.util.Map;

/**
 * 一页数据。
 *
 * @param estimatedRows    {@code pg_class.reltuples} 估算值；未开放估算时为 {@code null}
 * @param truncatedColumns 因长度上限被截断的列名
 * @param omittedColumnCount 整列未返回的列数（不暴露列名）
 */
public record DbBrowsePage(
        String tableKey,
        List<DbBrowseColumnView> columns,
        List<Map<String, Object>> rows,
        int page,
        int size,
        boolean hasMore,
        String nextCursor,
        Long estimatedRows,
        boolean estimated,
        int omittedColumnCount,
        List<String> truncatedColumns,
        long tookMs
) {
}
