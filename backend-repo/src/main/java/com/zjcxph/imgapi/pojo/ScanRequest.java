package com.zjcxph.imgapi.pojo;

public class ScanRequest {
    private String brxh;
    private String bah;
    private String filename;
    private Integer btype;
    private Integer pages;
    private String openerNo;
    private String uploadDate;
    private Integer uploadFlag;
    private String folder;

    public ScanRequest() {
    }

    public ScanRequest(String brxh, String bah, String filename, Integer btype, Integer pages,
                       String openerNo, String uploadDate, Integer uploadFlag, String folder) {
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

    public String getBrxh() {
        return brxh;
    }

    public void setBrxh(String brxh) {
        this.brxh = brxh;
    }

    public String getBah() {
        return bah;
    }

    public void setBah(String bah) {
        this.bah = bah;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public Integer getBtype() {
        return btype;
    }

    public void setBtype(Integer btype) {
        this.btype = btype;
    }

    public Integer getPages() {
        return pages;
    }

    public void setPages(Integer pages) {
        this.pages = pages;
    }

    public String getOpenerNo() {
        return openerNo;
    }

    public void setOpenerNo(String openerNo) {
        this.openerNo = openerNo;
    }

    public String getUploadDate() {
        return uploadDate;
    }

    public void setUploadDate(String uploadDate) {
        this.uploadDate = uploadDate;
    }

    public Integer getUploadFlag() {
        return uploadFlag;
    }

    public void setUploadFlag(Integer uploadFlag) {
        this.uploadFlag = uploadFlag;
    }

    public String getFolder() {
        return folder;
    }

    public void setFolder(String folder) {
        this.folder = folder;
    }
}
