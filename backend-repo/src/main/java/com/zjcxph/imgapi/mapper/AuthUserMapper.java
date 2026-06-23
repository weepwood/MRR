package com.zjcxph.imgapi.mapper;

import com.zjcxph.imgapi.entity.AuthUser;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
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

    @Update("update mr_auth_user set password_hash = #{passwordHash} where id = #{id}")
    int updatePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash);

    @Update("update mr_auth_user set status = #{status} where id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    @Insert("insert into mr_auth_user (username, display_name, password_hash, role_code, status) " +
            "values (#{username}, #{displayName}, #{passwordHash}, #{roleCode}, #{status})")
    int insertUser(AuthUser user);

    @Select({
            "<script>",
            "SELECT u.*, r.name as role_name, r.permissions as permissions_csv",
            "FROM mr_auth_user u LEFT JOIN mr_auth_role r ON u.role_code = r.code",
            "<where>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (LOWER(u.username) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(u.display_name, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(r.name, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(u.role_code, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%'))",
            "  </if>",
            "  <if test='roleCode != null and roleCode != \"\"'>",
            "    AND u.role_code = #{roleCode}",
            "  </if>",
            "  <if test='status != null and status != \"\"'>",
            "    AND LOWER(u.status) = LOWER(#{status})",
            "  </if>",
            "</where>",
            "ORDER BY u.id LIMIT #{limit} OFFSET #{offset}",
            "</script>"
    })
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "username", column = "username"),
            @Result(property = "displayName", column = "display_name"),
            @Result(property = "passwordHash", column = "password_hash"),
            @Result(property = "roleCode", column = "role_code"),
            @Result(property = "roleName", column = "role_name"),
            @Result(property = "permissionsCsv", column = "permissions_csv"),
            @Result(property = "status", column = "status"),
            @Result(property = "lastLoginAt", column = "last_login_at"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    List<AuthUser> findAllWithPagination(@Param("offset") int offset,
                                          @Param("limit") int limit,
                                          @Param("keyword") String keyword,
                                          @Param("roleCode") String roleCode,
                                          @Param("status") String status);

    @Select({
            "<script>",
            "SELECT COUNT(*)",
            "FROM mr_auth_user u LEFT JOIN mr_auth_role r ON u.role_code = r.code",
            "<where>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (LOWER(u.username) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(u.display_name, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(r.name, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(u.role_code, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%'))",
            "  </if>",
            "  <if test='roleCode != null and roleCode != \"\"'>",
            "    AND u.role_code = #{roleCode}",
            "  </if>",
            "  <if test='status != null and status != \"\"'>",
            "    AND LOWER(u.status) = LOWER(#{status})",
            "  </if>",
            "</where>",
            "</script>"
    })
    int countAll(@Param("keyword") String keyword,
                 @Param("roleCode") String roleCode,
                 @Param("status") String status);
}
