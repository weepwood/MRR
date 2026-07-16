package com.zjcxph.imgapi.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ArchiveIpBinding {
    private Long id;
    private LocalDate accessDate;
    private String userid;
    private String boundIp;
    private Integer ipChangeCount;
    private LocalDateTime firstAccessAt;
    private LocalDateTime lastAccessAt;
}
