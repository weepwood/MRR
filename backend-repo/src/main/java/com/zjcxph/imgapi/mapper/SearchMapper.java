package com.zjcxph.imgapi.mapper;

import com.zjcxph.imgapi.entity.Patient;
import com.zjcxph.imgapi.utils.MedicalRecordCodeUtils;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SearchMapper {
    String PATIENT_SELECT_COLUMNS = "id, idcard, bah, name, ruyuan, admissiontime, department, binqu, chuangwei";
    String BAH_SEARCH_EXPRESSION = "CASE WHEN bah ~ '^[0-9]+$' " +
            "THEN COALESCE(NULLIF(LTRIM(bah, '0'), ''), '0') ELSE bah END";
    String SCAN_BAH_SEARCH_EXPRESSION = "CASE WHEN BAH ~ '^[0-9]+$' " +
            "THEN COALESCE(NULLIF(LTRIM(BAH, '0'), ''), '0') ELSE BAH END";

    /*
     * 通过身份证号查询病案号
     */
    @Select("select " + PATIENT_SELECT_COLUMNS + " from mr_patient where idcard = #{idCard} " +
            "order by admissiontime desc nulls last, ruyuan desc nulls last, id desc")
    List<Patient> findBAHByIDCard(String idCard);

    @Select("select " + PATIENT_SELECT_COLUMNS + " from mr_patient " +
            "where bah = #{normalizedBah} or " + BAH_SEARCH_EXPRESSION + " = #{searchCode}")
    List<Patient> findPatientByBah(
            @Param("normalizedBah") String normalizedBah,
            @Param("searchCode") String searchCode
    );

    /**
     * 查询病案号在实际影像记录中出现过的全部上架号，用于身份证搜索结果唯一定位档案袋。
     */
    @Select("select distinct SJH from mr_scan where " +
            "(BAH = #{normalizedBah} or " + SCAN_BAH_SEARCH_EXPRESSION + " = #{searchCode}) " +
            "and SJH is not null and trim(SJH) <> '' order by SJH")
    List<String> findSjhByBah(
            @Param("normalizedBah") String normalizedBah,
            @Param("searchCode") String searchCode
    );

    /**
     * 保留旧的一参数调用方式，内部自动兼容有无前导零的病案号。
     */
    default List<Patient> findPatientByBah(String bah) {
        return findPatientByBah(
                MedicalRecordCodeUtils.normalizeOrEmpty(bah),
                MedicalRecordCodeUtils.toSearchTerm(bah)
        );
    }

    @Select("<script>" +
            "select " + PATIENT_SELECT_COLUMNS + " from mr_patient" +
            " <where>" +
            "   <if test='keyword != null and keyword != \"\"'>" +
            "     and (bah like '%' || #{keyword} || '%' or name like '%' || #{keyword} || '%' or idcard like '%' || #{keyword} || '%' or department like '%' || #{keyword} || '%' or binqu like '%' || #{keyword} || '%' or chuangwei like '%' || #{keyword} || '%')" +
            "   </if>" +
            " </where>" +
            " order by id limit #{size} offset #{offset}" +
            "</script>")
    List<Patient> findAllPaginated(@Param("offset") int offset, @Param("size") int size, @Param("keyword") String keyword);

    @Select("<script>" +
            "select count(*) from mr_patient" +
            " <where>" +
            "   <if test='keyword != null and keyword != \"\"'>" +
            "     and (bah like '%' || #{keyword} || '%' or name like '%' || #{keyword} || '%' or idcard like '%' || #{keyword} || '%' or department like '%' || #{keyword} || '%' or binqu like '%' || #{keyword} || '%' or chuangwei like '%' || #{keyword} || '%')" +
            "   </if>" +
            " </where>" +
            "</script>")
    int countAll(@Param("keyword") String keyword);
}
