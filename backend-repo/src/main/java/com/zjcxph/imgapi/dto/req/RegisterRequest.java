package com.zjcxph.imgapi.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
@Schema(description = "用户注册请求")
public class RegisterRequest {
    @NotBlank(message = "用户名不能为空")
    @Schema(description = "用户名", example = "doctor_new")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 18, message = "密码长度为6到18位")
    @Schema(description = "密码", example = "password123")
    private String password;

    @Schema(description = "显示名称", example = "新医生")
    private String displayName;

    @Schema(description = "角色代码：DOCTOR 或 NURSE，默认 DOCTOR", example = "DOCTOR")
    private String roleCode;
}
