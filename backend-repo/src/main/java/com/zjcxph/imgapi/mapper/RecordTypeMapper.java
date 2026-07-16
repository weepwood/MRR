package com.zjcxph.imgapi.mapper;

import com.zjcxph.imgapi.entity.RecordTypeDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RecordTypeMapper {

    @Select("SELECT btype, type_code, type_name, keywords, negative_keywords, enabled, sort_order " +
            "FROM mr_record_type_dict WHERE enabled = TRUE ORDER BY sort_order, btype")
    List<RecordTypeDefinition> findEnabled();

    @Select("SELECT EXISTS(SELECT 1 FROM mr_record_type_dict WHERE btype = #{btype} AND enabled = TRUE)")
    boolean existsEnabled(@Param("btype") Integer btype);
}
