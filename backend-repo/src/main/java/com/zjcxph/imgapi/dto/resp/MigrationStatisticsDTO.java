package com.zjcxph.imgapi.dto.resp;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class MigrationStatisticsDTO {
    private long totalCount;
    private long migratedCount;
    private long pendingCount;
    private long failedCount;
    private double percentage;

}
