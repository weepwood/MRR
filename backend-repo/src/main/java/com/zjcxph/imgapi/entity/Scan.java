package com.zjcxph.imgapi.entity;

import lombok.Data;

import java.util.Date;

@Data
public class Scan {
    private Integer id;
    private Long archiveId;
    private String brxh;
    private String bah;
    private String sjh;
    private String filename;
    private Integer btype;
    private Integer pages;
    private String openerNo;
    private Date uploadDate;
    private Integer uploadFlag;
    private String folder;

    // 多来源定位字段：仅保存受控类型、节点 ID 和相对引用。
    private String sourceType;
    private String sourceNode;
    private String sourceRef;

    // OSS migration fields
    private String ossUrl;
    private Long fileSize;
    private String checksumMd5;
    private String migrationStatus;
    private Date migratedAt;

    public Scan() {
    }

    public Scan(Integer id, String brxh, String bah, String sjh, String filename, Integer btype, Integer pages,
                String openerNo, Date uploadDate, Integer uploadFlag, String folder) {
        this.id = id;
        this.brxh = brxh;
        this.bah = bah;
        this.sjh = sjh;
        this.filename = filename;
        this.btype = btype;
        this.pages = pages;
        this.openerNo = openerNo;
        this.uploadDate = uploadDate;
        this.uploadFlag = uploadFlag;
        this.folder = folder;
    }
}
