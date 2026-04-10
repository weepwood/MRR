package com.zjcxph.imgapi.dto.resp;

import lombok.Data;

@Data
public class MigrationStatusCountDTO {
    private String migrationStatus;
    private long count;
}