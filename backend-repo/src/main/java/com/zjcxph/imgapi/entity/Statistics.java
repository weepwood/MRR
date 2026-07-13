package com.zjcxph.imgapi.entity;

import com.zjcxph.imgapi.utils.MedicalRecordCodeUtils;
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
        setBah(bah);
        this.cid = cid;
        this.openerNo = openerNo;
        this.date = date;
        this.type = type;
        this.pages = pages;
        setSjh(sjh);
    }

    public void setBah(String bah) {
        this.bah = MedicalRecordCodeUtils.normalize(bah);
    }

    public void setSjh(String sjh) {
        this.sjh = MedicalRecordCodeUtils.normalize(sjh);
    }
}
