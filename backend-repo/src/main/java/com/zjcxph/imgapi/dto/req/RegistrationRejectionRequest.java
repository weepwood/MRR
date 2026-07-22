package com.zjcxph.imgapi.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "注册申请审核拒绝请求")
public class RegistrationRejectionRequest {
    @NotBlank(message = "拒绝原因不能为空")
    @Size(max = 500, message = "拒绝原因不能超过500个字符")
    @Schema(description = "拒绝原因", example = "未提供有效的内部人员信息")
    private String rejectReason;
}
