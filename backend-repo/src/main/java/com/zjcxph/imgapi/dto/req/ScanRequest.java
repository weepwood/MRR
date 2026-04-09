package com.zjcxph.imgapi.dto.req;

import lombok.Data;

@Data
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

}
