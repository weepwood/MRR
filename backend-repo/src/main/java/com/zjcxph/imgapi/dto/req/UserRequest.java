package com.zjcxph.imgapi.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;

@Data
@Schema(description = "用户登录请求")
public class UserRequest {
    @NotBlank(message = "用户名不能为空")
    @Schema(description = "用户名", example = "br_admin")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Schema(description = "密码", example = "br_password")
    private String password;

}
