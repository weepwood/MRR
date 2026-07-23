package com.zjcxph.imgapi.dto.resp;

import lombok.Data;

@Data
public class SystemErrorOverviewDTO {
    private long totalGroups;
    private long totalOccurrences;
    private long openGroups;
    private long acknowledgedGroups;
    private long resolvedGroups;
    private long errorGroups;
    private long warnGroups;
    private long recentOccurrences;
}
