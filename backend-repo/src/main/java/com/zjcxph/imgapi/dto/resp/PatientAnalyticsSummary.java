package com.zjcxph.imgapi.dto.resp;

import java.util.List;

public record PatientAnalyticsSummary(
        int year,
        long totalRecords,
        long totalArchives,
        long yearArchives,
        long missingIdCardRecords,
        long confirmedMultiRecordGroups,
        long suspectedMultiRecordGroups,
        List<DateCount> dateCounts,
        List<DepartmentCount> departmentCounts
) {
    public record DateCount(String date, long count) {
    }

    public record DepartmentCount(String department, long count) {
    }
}
