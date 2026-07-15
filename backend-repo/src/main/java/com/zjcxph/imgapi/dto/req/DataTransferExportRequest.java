package com.zjcxph.imgapi.dto.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DataTransferExportRequest {
    @NotBlank
    private String entityType;

    private Long startId;
    private Long endId;

    @Min(10_000)
    @Max(2_000_000)
    private Integer rowsPerPart;
}
