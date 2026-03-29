package com.zjcxph.imgapi.mapper;

import com.zjcxph.imgapi.entity.Patient;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SearchMapper {
    /*
    *   通过身份证号查询病案号
    * */
    @Select("select id, idcard, bah, name, admissiontime, department from mr_patient where idcard = #{idCard}")
    List<Patient> findBAHByIDCard(String idCard);
}