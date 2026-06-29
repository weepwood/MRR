package com.zjcxph.imgapi.mapper;

import com.zjcxph.imgapi.entity.AuthRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface AuthRoleMapper {

    @Select("select code as code, name as name, description as description, permissions as permissions, sort_order as sortOrder from mr_auth_role order by sort_order, code")
    List<AuthRole> findAll();

    @Select("select code as code, name as name, description as description, permissions as permissions, sort_order as sortOrder from mr_auth_role where code = #{code}")
    AuthRole findByCode(@Param("code") String code);

    @Update("UPDATE mr_auth_role SET name = #{name}, description = #{description}, permissions = #{permissions}, sort_order = #{sortOrder} WHERE code = #{code}")
    int update(AuthRole role);
}
