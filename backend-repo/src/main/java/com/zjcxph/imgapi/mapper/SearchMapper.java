package com.zjcxph.imgapi.mapper;

import com.zjcxph.imgapi.entity.Patient;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SearchMapper {
    /*
    *   通过身份证号查询病案号
    * */
    @Select("select id, idcard, bah, name, admissiontime, department from mr_patient where idcard = #{idCard}")
    List<Patient> findBAHByIDCard(String idCard);

    @Select("select id, idcard, bah, name, admissiontime, department from mr_patient where bah = #{bah}")
    List<Patient> findPatientByBah(String bah);

    @Select("<script>" +
            "select id, idcard, bah, name, admissiontime, department from mr_patient" +
            " <where>" +
            "   <if test='keyword != null and keyword != \"\"'>" +
            "     and (bah like '%' || #{keyword} || '%' or name like '%' || #{keyword} || '%' or idcard like '%' || #{keyword} || '%' or department like '%' || #{keyword} || '%')" +
            "   </if>" +
            " </where>" +
            " order by id limit #{size} offset #{offset}" +
            "</script>")
    List<Patient> findAllPaginated(@Param("offset") int offset, @Param("size") int size, @Param("keyword") String keyword);

    @Select("<script>" +
            "select count(*) from mr_patient" +
            " <where>" +
            "   <if test='keyword != null and keyword != \"\"'>" +
            "     and (bah like '%' || #{keyword} || '%' or name like '%' || #{keyword} || '%' or idcard like '%' || #{keyword} || '%' or department like '%' || #{keyword} || '%')" +
            "   </if>" +
            " </where>" +
            "</script>")
    int countAll(@Param("keyword") String keyword);
}