package com.zjcxph.imgapi.dto.resp;

import lombok.Data;

import java.util.Date;

@Data
public class BAHDataResponseDTO {
    private Integer id;
    private String brxh;
    private String bah;
    private String filename;
    private Integer btype;
    private Integer pages;
    private String openerNo;
    private Date uploadDate;
    private Integer uploadFlag;
    private String img_url;

    public BAHDataResponseDTO() {
    }

    public BAHDataResponseDTO(Integer id, String brxh, String bah, String filename, Integer btype, Integer pages,
                              String openerNo, Date uploadDate, Integer uploadFlag, String img_url) {
        this.id = id;
        this.brxh = brxh;
        this.bah = bah;
        this.filename = filename;
        this.btype = btype;
        this.pages = pages;
        this.openerNo = openerNo;
        this.uploadDate = uploadDate;
        this.uploadFlag = uploadFlag;
        this.img_url = img_url;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
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

    public Date getUploadDate() {
        return uploadDate;
    }

    public void setUploadDate(Date uploadDate) {
        this.uploadDate = uploadDate;
    }

    public Integer getUploadFlag() {
        return uploadFlag;
    }

    public void setUploadFlag(Integer uploadFlag) {
        this.uploadFlag = uploadFlag;
    }

    public String getImg_url() {
        return img_url;
    }

    public void setImg_url(String img_url) {
        this.img_url = img_url;
    }
}
