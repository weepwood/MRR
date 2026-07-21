package com.zjcxph.imgapi.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 病案主数据。
 *
 * <p>上架号是业务唯一键，但允许缺失；数据库主键始终使用 id。</p>
 */
@Data
public class ArchiveRecord {
    private Long id;
    private String sjh;
    private String bah;
    private String patientId;
    private String patientName;
    private String inpatientDepartment;
    private String deviceId;
    private String operatorNo;
    private LocalDate archiveDate;
    private LocalDate dischargeDate;
    private String archiveType;
    private Integer pageCount;
    private Integer sourceStatisticsId;
    private Long scanCount;
    private Long scanPageCount;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
