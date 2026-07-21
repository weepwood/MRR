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
    private Boolean mustChangePassword;
    private Integer passwordVersion;
    private LocalDateTime temporaryPasswordExpiresAt;
    private LocalDateTime lastLoginAt;

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions == null ? new ArrayList<>() : new ArrayList<>(permissions);
    }

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(roleCode);
    }

    public boolean isPasswordChangeRequired() {
        return Boolean.TRUE.equals(mustChangePassword);
    }

    public int effectivePasswordVersion() {
        return passwordVersion == null || passwordVersion < 1 ? 1 : passwordVersion;
    }

    public boolean hasPermission(String permission) {
        if (isAdmin()) {
            return true;
        }
        return PermissionResolver.hasPermission(permissions, permission);
    }
}
