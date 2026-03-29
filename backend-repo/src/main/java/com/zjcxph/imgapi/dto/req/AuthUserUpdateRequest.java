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

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
