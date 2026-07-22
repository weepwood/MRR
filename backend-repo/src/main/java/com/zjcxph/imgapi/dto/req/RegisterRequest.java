package com.zjcxph.imgapi.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "用户注册申请")
public class RegisterRequest {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 40, message = "用户名长度应为3到40位")
    @Schema(description = "用户名", example = "doctor_new")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 12, max = 64, message = "密码长度应为12到64位")
    @Schema(description = "密码", example = "Password123!")
    private String password;

    @NotBlank(message = "显示名称不能为空")
    @Size(max = 80, message = "显示名称不能超过80个字符")
    @Schema(description = "显示名称", example = "新医生")
    private String displayName;

    @Size(max = 200, message = "联系方式不能超过200个字符")
    @Schema(description = "联系方式，可填写手机号、工号或科室联系方式", example = "内线 6123")
    private String contactInfo;

    @Size(max = 500, message = "申请说明不能超过500个字符")
    @Schema(description = "申请说明", example = "肿瘤科医生，申请查阅本科室病案")
    private String applyRemark;
}
