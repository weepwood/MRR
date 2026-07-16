package com.zjcxph.imgapi.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ArchiveSearchHistory {
    private Long id;
    private Long userId;
    private String bah;
    private String sjh;
    private boolean success;
    private Integer imageCount;
    private String failureReason;
    private boolean favorite;
    private LocalDateTime searchedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
