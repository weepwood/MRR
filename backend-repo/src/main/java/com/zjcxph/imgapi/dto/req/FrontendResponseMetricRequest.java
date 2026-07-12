package com.zjcxph.imgapi.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FrontendResponseMetricRequest {

    @NotBlank
    @Size(max = 64)
    private String requestId;

    @NotBlank
    @Size(max = 255)
    private String routePattern;

    @NotBlank
    @Size(max = 10)
    private String method;

    private Integer httpStatus;
    private Integer businessCode;

    @NotNull
    private Boolean success;

    @NotNull
    @PositiveOrZero
    private Long clientDurationMs;

    @PositiveOrZero
    private Long serverDurationMs;

    @NotNull
    private LocalDateTime occurredAt;
}
