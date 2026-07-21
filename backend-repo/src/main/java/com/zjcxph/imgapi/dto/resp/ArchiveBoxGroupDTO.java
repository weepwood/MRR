package com.zjcxph.imgapi.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单个箱号的装箱汇总。
 */
@Data
public class ArchiveBoxGroupDTO {
    private String boxNo;
    private long recordCount;
    private long abnormalCount;
    private LocalDateTime updatedAt;
}
