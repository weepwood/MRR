package com.zjcxph.imgapi.mapper;

import com.zjcxph.imgapi.pojo.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    @Select("select * from mr_user where id = #{id}")
    User findById(Long id);
}
