package com.zjcxph.imgapi.entity;

import com.zjcxph.imgapi.utils.MedicalRecordCodeUtils;
import lombok.Data;

@Data
public class Patient {
    private Integer id;
    // 身份证号
    private String idCard;
    // 病人序号
//    private String brxh;
    // 病案号
    private String bah;
    // 用户名
    private String name;
    // 入院时间
    private String admissiontime;
    // 住院科室
    private String department;

    public Patient(Integer id, String idCard, String bah, String name, String admissiontime,
                   String department) {
        this.id = id;
        this.idCard = idCard;
        setBah(bah);
        this.name = name;
        this.admissiontime = admissiontime;
        this.department = department;
    }

    public Patient() {
    }

    public void setBah(String bah) {
        this.bah = MedicalRecordCodeUtils.normalize(bah);
    }
    
    @Override
    public String toString() {
        return "Patient{" +
                "id=" + id +
                ", idCard='" + idCard + '\'' +
//                ", brxh='" + brxh + '\'' +
                ", bah='" + bah + '\'' +
                ", name='" + name + '\'' +
                ", admissionTime=" + admissiontime +
                ", department='" + department + '\'' +
                '}';
    }
}
