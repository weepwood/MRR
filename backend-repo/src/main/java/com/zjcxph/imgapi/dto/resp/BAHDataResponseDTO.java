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
    private String ossUrl;

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

    public void setId(Integer id) {
        this.id = id;
    }

    public void setBrxh(String brxh) {
        this.brxh = brxh;
    }

    public void setBah(String bah) {
        this.bah = bah;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public void setBtype(Integer btype) {
        this.btype = btype;
    }

    public void setPages(Integer pages) {
        this.pages = pages;
    }

    public void setOpenerNo(String openerNo) {
        this.openerNo = openerNo;
    }

    public void setUploadDate(Date uploadDate) {
        this.uploadDate = uploadDate;
    }

    public void setUploadFlag(Integer uploadFlag) {
        this.uploadFlag = uploadFlag;
    }

    public void setImg_url(String img_url) {
        this.img_url = img_url;
    }

    public void setOssUrl(String ossUrl) {
        this.ossUrl = ossUrl;
    }
}
