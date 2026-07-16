package com.zjcxph.imgapi.entity;

import com.zjcxph.imgapi.utils.MedicalRecordCodeUtils;
import lombok.Data;

import java.time.LocalDate;

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
    // 入院日期
    private LocalDate ruyuan;
    // 入院时间
    private String admissiontime;
    // 住院科室
    private String department;
    // 病区
    private String binqu;
    // 床位
    private String chuangwei;

    public Patient(Integer id, String idCard, String bah, String name, String admissiontime,
                   String department) {
        this(id, idCard, bah, name, null, admissiontime, department, null, null);
    }

    public Patient(Integer id, String idCard, String bah, String name, LocalDate ruyuan,
                   String admissiontime, String department, String binqu, String chuangwei) {
        this.id = id;
        this.idCard = idCard;
        setBah(bah);
        this.name = name;
        this.ruyuan = ruyuan;
        this.admissiontime = admissiontime;
        this.department = department;
        this.binqu = binqu;
        this.chuangwei = chuangwei;
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
                ", ruyuan=" + ruyuan +
                ", admissionTime=" + admissiontime +
                ", department='" + department + '\'' +
                ", binqu='" + binqu + '\'' +
                ", chuangwei='" + chuangwei + '\'' +
                '}';
    }
}
