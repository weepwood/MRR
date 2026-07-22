package com.zjcxph.imgapi.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class AuthUserProfileDTO {
    private Long id;
    private String username;
    private String displayName;
    private String roleCode;
    private String roleName;
    private List<String> permissions = new ArrayList<>();
    private String status;
    private String contactInfo;
    private String applyRemark;
    private LocalDateTime appliedAt;
    private LocalDateTime reviewedAt;
    private Long reviewedBy;
    private String rejectReason;
    private Boolean mustChangePassword;
    private Integer passwordVersion;
    private LocalDateTime passwordChangedAt;
    private LocalDateTime temporaryPasswordExpiresAt;
    private LocalDateTime passwordResetAt;
    private Long passwordResetBy;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
