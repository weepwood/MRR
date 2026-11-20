package com.zjcxph.imgapi.dto.resp.browse;

import java.util.List;

/**
 * 单表结构描述。
 */
public record DbBrowseTableSchema(
        String tableKey,
        String label,
        boolean personalData,
        boolean requireFilter,
        List<String> keyColumns,
        int maxPageSize,
        List<DbBrowseColumnView> columns,
        int omittedColumnCount
) {
}
