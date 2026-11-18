package com.zjcxph.imgapi.dto.resp.browse;

import java.util.List;

/**
 * 响应中的列元数据。只包含真正返回的列；{@code FULL} 掩码列不出现在这里。
 */
public record DbBrowseColumnView(
        String name,
        String label,
        String type,
        boolean filterable,
        boolean orderable,
        String mask,
        List<String> allowedOps
) {
}
