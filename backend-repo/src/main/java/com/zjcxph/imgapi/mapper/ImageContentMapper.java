package com.zjcxph.imgapi.mapper;

import com.zjcxph.imgapi.entity.Scan;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ImageContentMapper {

    @Select("SELECT * FROM mr_scan WHERE id = #{id} AND uploadflag != 0")
    Scan findActiveById(@Param("id") Integer id);
}
