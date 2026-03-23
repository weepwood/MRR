package com.zjcxph.imgapi.pojo;

/**
 * 病案统计 DTO
 */
public class BAHStatisticsDTO {
    private String bah;           // 病案号
    private Long recordCount;     // 记录数（有多少条记录）
    private Long totalPages;      // 总页数

    public BAHStatisticsDTO() {
    }

    public BAHStatisticsDTO(String bah, Long recordCount, Long totalPages) {
        this.bah = bah;
        this.recordCount = recordCount;
        this.totalPages = totalPages;
    }

    public String getBah() {
        return bah;
    }

    public void setBah(String bah) {
        this.bah = bah;
    }

    public Long getRecordCount() {
        return recordCount;
    }

    public void setRecordCount(Long recordCount) {
        this.recordCount = recordCount;
    }

    public Long getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(Long totalPages) {
        this.totalPages = totalPages;
    }
}
