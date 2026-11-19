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
    /** 近 24 小时仍活跃的 ERROR 错误组数，供菜单角标使用。 */
    private long recentErrorGroups;
}
