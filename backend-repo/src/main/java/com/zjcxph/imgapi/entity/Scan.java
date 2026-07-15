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
