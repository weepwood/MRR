package com.zjcxph.imgapi.dto.resp;

import lombok.Data;

/**
 * 日期统计 DTO
 */
@Data
public class DateStatisticsDTO {
    private String date;          // 日期
    private Long recordCount;     // 记录数
    private Long totalPages;      // 总页数

    public DateStatisticsDTO() {
    }

    public DateStatisticsDTO(String date, Long recordCount, Long totalPages) {
        this.date = date;
        this.recordCount = recordCount;
        this.totalPages = totalPages;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
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
