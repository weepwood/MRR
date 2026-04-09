package com.zjcxph.imgapi.entity;

import lombok.Data;

@Data
public class Statistics {
    private String bah;
    private String cid;
    private String openerNo;
    private String date;
    private String type;
    private Integer pages;

    public Statistics() {
    }

    public Statistics(String bah, String cid, String openerNo, String date, String type, Integer pages) {
        this.bah = bah;
        this.cid = cid;
        this.openerNo = openerNo;
        this.date = date;
        this.type = type;
        this.pages = pages;
    }

}
