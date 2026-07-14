package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.dto.resp.IdCardArchiveSearchResponse;
import com.zjcxph.imgapi.entity.Patient;

import java.util.List;

public interface SearchService {
    // 根据身份证号获取该身份证号对应的病案号列表
    List<Patient> getBAHByID(String ID);

    // 根据身份证号获取可唯一定位影像档案袋的病案列表
    List<IdCardArchiveSearchResponse.ArchiveCase> getArchiveCasesByID(String idCard);

    // 根据病案号获取患者信息
    List<Patient> getPatientByBah(String bah);
}
