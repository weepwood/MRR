package com.zjcxph.imgapi.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SystemErrorStatusRequest {
    @NotBlank(message = "处理状态不能为空")
    private String status;
}
