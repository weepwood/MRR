package com.zjcxph.imgapi.dto.resp;

import lombok.Data;

/**
 * 档案装箱总体摘要。
 */
@Data
public class ArchiveBoxSummaryDTO {
    private long totalRecords;
    private long totalBoxes;
    private long missingCount;
    private long abnormalCount;
}
