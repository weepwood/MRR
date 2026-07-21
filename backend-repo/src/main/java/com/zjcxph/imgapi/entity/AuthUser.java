package com.zjcxph.imgapi.entity;

import lombok.Data;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
public class AuthUser {
    private Long id;
    private String username;
    private String displayName;
    private String passwordHash;
    private String roleCode;
    private String roleName;
    private String permissionsCsv;
    private String status;
    private Boolean mustChangePassword;
    private Integer passwordVersion;
    private LocalDateTime passwordChangedAt;
    private LocalDateTime temporaryPasswordExpiresAt;
    private Long createdBy;
    private LocalDateTime passwordResetAt;
    private Long passwordResetBy;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @JsonIgnore
    public String getPasswordHash() {
        return passwordHash;
    }

    @JsonIgnore
    public String getPermissionsCsv() {
        return permissionsCsv;
    }

    public boolean isPasswordChangeRequired() {
        return Boolean.TRUE.equals(mustChangePassword);
    }

    public int effectivePasswordVersion() {
        return passwordVersion == null || passwordVersion < 1 ? 1 : passwordVersion;
    }

    public List<String> getPermissions() {
        if (permissionsCsv == null || permissionsCsv.isBlank()) {
            return Collections.emptyList();
        }
        List<String> permissions = new ArrayList<>();
        for (String permission : permissionsCsv.split(",")) {
            String trimmed = permission.trim();
            if (!trimmed.isEmpty()) {
                permissions.add(trimmed);
            }
        }
        return permissions;
    }

    public boolean hasPermission(String permission) {
        return permission != null && getPermissions().contains(permission);
    }
}
