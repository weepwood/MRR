package com.zjcxph.imgapi.entity;

import lombok.Data;

import java.util.Date;


@Data
public class Scan {
    private Integer id;
    private String brxh;
    private String bah;
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

    public Scan() {}

    public Scan(Integer id, String brxh, String bah, String filename, Integer btype, Integer pages,
                String openerNo, Date uploadDate, Integer uploadFlag, String folder) {
        this.id = id;
        this.brxh = brxh;
        this.bah = bah;
        this.filename = filename;
        this.btype = btype;
        this.pages = pages;
        this.openerNo = openerNo;
        this.uploadDate = uploadDate;
        this.uploadFlag = uploadFlag;
        this.folder = folder;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getBrxh() { return brxh; }
    public void setBrxh(String brxh) { this.brxh = brxh; }
    public String getBah() { return bah; }
    public void setBah(String bah) { this.bah = bah; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public Integer getBtype() { return btype; }
    public void setBtype(Integer btype) { this.btype = btype; }
    public Integer getPages() { return pages; }
    public void setPages(Integer pages) { this.pages = pages; }
    public String getOpenerNo() { return openerNo; }
    public void setOpenerNo(String openerNo) { this.openerNo = openerNo; }
    public Date getUploadDate() { return uploadDate; }
    public void setUploadDate(Date uploadDate) { this.uploadDate = uploadDate; }
    public Integer getUploadFlag() { return uploadFlag; }
    public void setUploadFlag(Integer uploadFlag) { this.uploadFlag = uploadFlag; }
    public String getFolder() { return folder; }
    public void setFolder(String folder) { this.folder = folder; }

    // OSS field accessors
    public String getOssUrl() { return ossUrl; }
    public void setOssUrl(String ossUrl) { this.ossUrl = ossUrl; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getChecksumMd5() { return checksumMd5; }
    public void setChecksumMd5(String checksumMd5) { this.checksumMd5 = checksumMd5; }
    public String getMigrationStatus() { return migrationStatus; }
    public void setMigrationStatus(String migrationStatus) { this.migrationStatus = migrationStatus; }
    public Date getMigratedAt() { return migratedAt; }
    public void setMigratedAt(Date migratedAt) { this.migratedAt = migratedAt; }
}
