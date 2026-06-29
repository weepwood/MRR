package com.zjcxph.imgapi.entity;

import lombok.Data;

@Data
public class Statistics {
    private Long id;
    private String bah;
    private String cid;
    private String openerNo;
    private String date;
    private String type;
    private Integer pages;
    private String sjh;

    public Statistics() {
    }

    public Statistics(String bah, String cid, String openerNo, String date, String type, Integer pages, String sjh) {
        this.bah = bah;
        this.cid = cid;
        this.openerNo = openerNo;
        this.date = date;
        this.type = type;
        this.pages = pages;
        this.sjh = sjh;
    }

}
