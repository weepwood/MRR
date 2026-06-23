package com.zjcxph.imgapi.common;

import com.zjcxph.imgapi.utils.PermissionResolver;
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
        return "ADMIN".equalsIgnoreCase(roleCode);
    }

    public boolean hasPermission(String permission) {
        return PermissionResolver.hasPermission(permissions, permission);
    }
}
