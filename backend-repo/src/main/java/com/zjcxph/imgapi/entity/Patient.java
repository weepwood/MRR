package com.zjcxph.imgapi.entity;

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
    private String admissiontime ;
    // 住院科室
    private String department;

    public Patient(Integer id, String idCard, String bah, String name, String admissiontime,
                  String department) {
        this.id = id;
        this.idCard = idCard;
        this.bah = bah;
        this.name = name;
        this.admissiontime = admissiontime;
        this.department = department;
    }

    public Patient() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getIdCard() {
        return idCard;
    }

    public void setIdCard(String idCard) {
        this.idCard = idCard;
    }

//    public String getBrxh() {
//        return brxh;
//    }
//
//    public void setBrxh(String brxh) {
//        this.brxh = brxh;
//    }

    public String getBah() {
        return bah;
    }

    public void setBah(String bah) {
        this.bah = bah;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAdmissionTime() {
        return admissiontime;
    }

    public void setAdmissionTime(String admissionTime) {
        this.admissiontime = admissionTime;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
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
