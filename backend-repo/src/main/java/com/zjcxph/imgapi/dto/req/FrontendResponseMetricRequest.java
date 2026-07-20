package com.zjcxph.imgapi.dto.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;

@Data
public class FrontendResponseMetricRequest {

    @NotBlank
    @Size(max = 64)
    private String requestId;

    @NotBlank
    @Size(max = 255)
    @Pattern(regexp = "^/[^?#]*$", message = "routePattern 必须是无查询参数的接口模板")
    private String routePattern;

    @NotBlank
    @Size(max = 10)
    private String method;

    @Min(100)
    @Max(599)
    private Integer httpStatus;
    private Integer businessCode;

    @NotNull
    private Boolean success;

    @NotNull
    @PositiveOrZero
    @Max(600000)
    private Long clientDurationMs;

    @PositiveOrZero
    @Max(600000)
    private Long serverDurationMs;

    @Min(0)
    @Max(5)
    private Integer retryCount;

    @Pattern(regexp = "^(succeeded|failed|canceled)$")
    private String retryOutcome;

    @NotNull
    private Instant occurredAt;
}
