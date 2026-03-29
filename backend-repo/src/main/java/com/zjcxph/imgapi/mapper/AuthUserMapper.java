package com.zjcxph.imgapi.mapper;

import com.zjcxph.imgapi.entity.AuthUser;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AuthUserMapper {

    @Select("select " +
            "u.id as id, " +
            "u.username as username, " +
            "u.display_name as displayName, " +
            "u.password_hash as passwordHash, " +
            "u.role_code as roleCode, " +
            "r.name as roleName, " +
            "r.permissions as permissionsCsv, " +
            "u.status as status, " +
            "u.last_login_at as lastLoginAt " +
            "from mr_auth_user u " +
            "left join mr_auth_role r on r.code = u.role_code " +
            "where u.username = #{username}")
    AuthUser findByUsername(@Param("username") String username);

    @Select("select " +
            "u.id as id, " +
            "u.username as username, " +
            "u.display_name as displayName, " +
            "u.password_hash as passwordHash, " +
            "u.role_code as roleCode, " +
            "r.name as roleName, " +
            "r.permissions as permissionsCsv, " +
            "u.status as status, " +
            "u.last_login_at as lastLoginAt " +
            "from mr_auth_user u " +
            "left join mr_auth_role r on r.code = u.role_code " +
            "where u.id = #{id}")
    AuthUser findById(@Param("id") Long id);

    @Select("select " +
            "u.id as id, " +
            "u.username as username, " +
            "u.display_name as displayName, " +
            "u.password_hash as passwordHash, " +
            "u.role_code as roleCode, " +
            "r.name as roleName, " +
            "r.permissions as permissionsCsv, " +
            "u.status as status, " +
            "u.last_login_at as lastLoginAt " +
            "from mr_auth_user u " +
            "left join mr_auth_role r on r.code = u.role_code " +
            "order by u.id")
    List<AuthUser> findAll();

    @Update("update mr_auth_user set last_login_at = #{lastLoginAt} where id = #{id}")
    int updateLastLoginAt(@Param("id") Long id, @Param("lastLoginAt") LocalDateTime lastLoginAt);

    @Update("update mr_auth_user " +
            "set display_name = #{displayName}, " +
            "role_code = #{roleCode}, " +
            "status = #{status} " +
            "where id = #{id}")
    int updateUser(AuthUser user);

    @Update("update mr_auth_user set status = #{status} where id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    @Insert("insert into mr_auth_user (username, display_name, password_hash, role_code, status) " +
            "values (#{username}, #{displayName}, #{passwordHash}, #{roleCode}, #{status})")
    int insertUser(AuthUser user);
}
