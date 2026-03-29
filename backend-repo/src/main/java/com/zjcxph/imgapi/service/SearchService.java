package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.entity.Patient;

import java.util.List;

public interface SearchService {
    // 根据身份证号获取该身份证号对应的病案号列表
    List<Patient> getBAHByID(String ID);
}
