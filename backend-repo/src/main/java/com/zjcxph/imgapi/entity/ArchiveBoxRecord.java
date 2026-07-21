package com.zjcxph.imgapi.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体病案装箱位置记录。
 *
 * <p>一条记录描述一份实体病案当前所在箱号；缺失病案允许实际箱号为空，
 * 并通过 expectedBoxNo 与 remark 保留原计划位置和异常说明。</p>
 */
@Data
public class ArchiveBoxRecord {
    private Long id;
    private String bah;
    private String sjh;
    private String boxNo;
    private String expectedBoxNo;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
