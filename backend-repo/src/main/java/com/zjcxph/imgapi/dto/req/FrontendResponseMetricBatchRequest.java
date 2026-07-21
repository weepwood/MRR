package com.zjcxph.imgapi.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class FrontendResponseMetricBatchRequest {

    @NotEmpty
    @Size(max = 200)
    private List<@Valid FrontendResponseMetricRequest> metrics;
}
