package com.zjcxph.imgapi.dto.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminCreateUserRequest {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 40, message = "用户名长度应为 3 到 40 位")
    @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "用户名只能包含字母、数字、点、下划线和短横线")
    private String username;

    @Size(max = 60, message = "显示名称不能超过 60 个字符")
    private String displayName;

    @NotBlank(message = "请选择角色")
    private String roleCode;

    private String status = "active";

    @Min(value = 1, message = "临时密码有效期至少为 1 小时")
    @Max(value = 168, message = "临时密码有效期不能超过 168 小时")
    private Integer temporaryPasswordValidHours = 24;
}
