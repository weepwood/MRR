package com.zjcxph.imgapi.dto.resp;

import lombok.Data;

import java.util.Date;

@Data
public class BAHDataResponseDTO {
    private Integer id;
    private String brxh;
    private String bah;
    private String sjh;
    private String filename;
    private Integer btype;
    private Integer pages;
    private String openerNo;
    private Date uploadDate;
    private Integer uploadFlag;
    private String img_url;
    private String ossUrl;
    private String folder;

    public BAHDataResponseDTO() {
    }

    public BAHDataResponseDTO(Integer id, String brxh, String bah, String sjh, String filename, Integer btype, Integer pages,
                              String openerNo, Date uploadDate, Integer uploadFlag, String img_url, String folder) {
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
        this.img_url = img_url;
        this.folder = folder;
    }

}
