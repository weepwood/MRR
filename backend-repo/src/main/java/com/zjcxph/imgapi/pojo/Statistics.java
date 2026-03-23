package com.zjcxph.imgapi.pojo;

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

    public String getBah() {
        return bah;
    }

    public void setBah(String bah) {
        this.bah = bah;
    }

    public String getCid() {
        return cid;
    }

    public void setCid(String cid) {
        this.cid = cid;
    }

    public String getOpenerNo() {
        return openerNo;
    }

    public void setOpenerNo(String openerNo) {
        this.openerNo = openerNo;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getPages() {
        return pages;
    }

    public void setPages(Integer pages) {
        this.pages = pages;
    }
}
