package com.zjcxph.imgapi.entity;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Setter
@Getter
public class ImageMigrationLog {
    private Long id;
    private Integer scanId;
    private String localPath;
    private String ossUrl;
    private String migrationStatus;
    private String errorMessage;
    private Long fileSize;
    private String checksumMd5;
    private Date migratedAt;
    private Date verifiedAt;
    private Date createdAt;
    private Date updatedAt;

}
