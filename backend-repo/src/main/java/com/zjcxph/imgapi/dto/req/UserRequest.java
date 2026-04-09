package com.zjcxph.imgapi.dto.req;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

@Data
public class UserRequest {
    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

}
