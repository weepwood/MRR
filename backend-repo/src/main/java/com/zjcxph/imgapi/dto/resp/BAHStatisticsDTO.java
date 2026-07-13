package com.zjcxph.imgapi.dto.resp;

import com.zjcxph.imgapi.utils.MedicalRecordCodeUtils;
import lombok.Data;

/**
 * 病案统计 DTO
 */
@Data
public class BAHStatisticsDTO {
    private String bah;           // 病案号
    private Long recordCount;     // 记录数（有多少条记录）
    private Long totalPages;      // 总页数

    public BAHStatisticsDTO() {
    }

    public BAHStatisticsDTO(String bah, Long recordCount, Long totalPages) {
        setBah(bah);
        this.recordCount = recordCount;
        this.totalPages = totalPages;
    }

    public void setBah(String bah) {
        this.bah = MedicalRecordCodeUtils.normalize(bah);
    }
}
