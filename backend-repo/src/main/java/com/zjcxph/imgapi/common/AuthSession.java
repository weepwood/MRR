package com.zjcxph.imgapi.common;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class AuthSession {
    private Long id;
    private String username;
    private String displayName;
    private String roleCode;
    private String roleName;
    private List<String> permissions = new ArrayList<>();
    private String status;
    private LocalDateTime lastLoginAt;

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions == null ? new ArrayList<>() : new ArrayList<>(permissions);
    }

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(roleCode)
                || (permissions != null && (permissions.contains("user:manage") || permissions.contains("role:manage")));
    }

    public boolean hasPermission(String permission) {
        return permission != null && permissions != null && permissions.contains(permission);
    }
}
