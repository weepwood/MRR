package com.zjcxph.imgapi.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "注册申请审核通过请求")
public class RegistrationApprovalRequest {
    @NotBlank(message = "角色不能为空")
    @Schema(description = "审核通过后授予的角色代码", example = "DOCTOR")
    private String roleCode;
}
