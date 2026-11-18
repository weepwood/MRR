package com.zjcxph.imgapi.dto.resp.browse;

/**
 * 表清单条目。只返回当前账号有权访问的表，被过滤掉的表不出现、也不回显。
 */
public record DbBrowseTableSummary(
        String tableKey,
        String label,
        String description,
        boolean personalData,
        boolean requireFilter,
        Long estimatedRows,
        boolean estimated,
        int columnCount
) {
}
