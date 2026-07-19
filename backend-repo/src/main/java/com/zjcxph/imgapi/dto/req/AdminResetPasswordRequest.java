package com.zjcxph.imgapi.dto.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminResetPasswordRequest {
    @NotBlank(message = "请输入当前管理员密码进行确认")
    private String administratorPassword;

    @Min(value = 1, message = "临时密码有效期至少为 1 小时")
    @Max(value = 168, message = "临时密码有效期不能超过 168 小时")
    private Integer temporaryPasswordValidHours = 24;
}
