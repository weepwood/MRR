package com.zjcxph.imgapi.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExternalArchiveStoredGrant {
    private String clientId;
    private String externalUserId;
    private Boolean allowDownload;
    private String grantJson;
    private LocalDateTime expiresAt;
}
