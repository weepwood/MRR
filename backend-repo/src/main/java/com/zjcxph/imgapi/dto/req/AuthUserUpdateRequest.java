package com.zjcxph.imgapi.dto.req;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

@Data
public class AuthUserUpdateRequest {
    private String displayName;

    @NotBlank(message = "角色不能为空")
    private String roleCode;

    @NotBlank(message = "状态不能为空")
    private String status;

}
