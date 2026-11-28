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

    @Insert("INSERT INTO app.mr_security_change_audit(event_type,actor_user_id,target_user_id,target_role_code,permissions_edited) "
            + "VALUES (#{event},#{actor,jdbcType=BIGINT},#{target,jdbcType=BIGINT},#{role,jdbcType=VARCHAR},#{permissionsEdited,jdbcType=BOOLEAN})")
    @org.apache.ibatis.annotations.Options(timeout = 3)
    int insertSecurityChangeAudit(@Param("event") String event, @Param("actor") Long actor,
                                  @Param("target") Long target, @Param("role") String role,
                                  @Param("permissionsEdited") Boolean permissionsEdited);


    String USER_COLUMNS = "u.id as id, u.username as username, u.display_name as displayName, " +
            "u.password_hash as passwordHash, u.role_code as roleCode, r.name as roleName, " +
            "r.permissions as permissionsCsv, u.status as status, " +
            "u.contact_info as contactInfo, u.apply_remark as applyRemark, " +
            "u.applied_at as appliedAt, u.reviewed_at as reviewedAt, " +
            "u.reviewed_by as reviewedBy, u.reject_reason as rejectReason, " +
            "u.must_change_password as mustChangePassword, u.password_version as passwordVersion, " +
            "u.password_changed_at as passwordChangedAt, " +
            "u.temporary_password_expires_at as temporaryPasswordExpiresAt, " +
            "u.created_by as createdBy, u.password_reset_at as passwordResetAt, " +
            "u.password_reset_by as passwordResetBy, u.last_login_at as lastLoginAt, " +
            "u.created_at as createdAt, u.updated_at as updatedAt ";

    // Per-request authentication does not need credential hashes or registration/contact metadata.
    String SESSION_COLUMNS = "u.id as id, u.username as username, u.display_name as displayName, " +
            "u.role_code as roleCode, r.name as roleName, r.permissions as permissionsCsv, " +
            "u.status as status, u.must_change_password as mustChangePassword, " +
            "u.password_version as passwordVersion, " +
            "u.temporary_password_expires_at as temporaryPasswordExpiresAt, u.last_login_at as lastLoginAt ";

    /** A sentinel row preserves revocation precedence even for deleted users. */
    class SessionState extends AuthUser {
        private Boolean tokenRevoked;
        public Boolean getTokenRevoked() { return tokenRevoked; }
        public void setTokenRevoked(Boolean tokenRevoked) { this.tokenRevoked = tokenRevoked; }
    }

    @Select("select " + SESSION_COLUMNS + ", v.revoked as tokenRevoked " +
            "from (select exists(select 1 from app.token_revocation " +
            "where token_hash = #{tokenHash} and expires_at > CURRENT_TIMESTAMP) as revoked) v " +
            "left join app.mr_auth_user u on u.id = #{id,jdbcType=BIGINT} " +
            "left join app.mr_auth_role r on r.code = u.role_code")
    @org.apache.ibatis.annotations.Options(useCache = false, timeout = 3)
    SessionState observeSession(@Param("id") Long id, @Param("tokenHash") String tokenHash);

    @Select("select " + SESSION_COLUMNS +
            "from mr_auth_user u left join mr_auth_role r on r.code = u.role_code where u.id = #{id}")
    AuthUser findSessionById(@Param("id") Long id);

    /** Common role lock is acquired first by management operations. Fence concurrent credential writes on the actor row. */
    @Select("select id from mr_auth_user where id = #{id} for no key update")
    @org.apache.ibatis.annotations.Options(useCache = false, flushCache = org.apache.ibatis.annotations.Options.FlushCachePolicy.TRUE)
    Long lockAuthorizationActor(@Param("id") Long id);

    @Select("select " + USER_COLUMNS +
            "from mr_auth_user u left join mr_auth_role r on r.code = u.role_code " +
            "where u.username = #{username}")
    AuthUser findByUsername(@Param("username") String username);

    @Select("select " + USER_COLUMNS +
            "from mr_auth_user u left join mr_auth_role r on r.code = u.role_code " +
            "where u.id = #{id}")
    AuthUser findById(@Param("id") Long id);

    @Update("update mr_auth_user set last_login_at = #{lastLoginAt}, updated_at = CURRENT_TIMESTAMP where id = #{id}")
    int updateLastLoginAt(@Param("id") Long id, @Param("lastLoginAt") LocalDateTime lastLoginAt);

    @Update("update mr_auth_user set display_name = #{displayName}, role_code = #{roleCode}, " +
            "password_version = password_version + CASE WHEN status IS DISTINCT FROM #{status} THEN 1 ELSE 0 END, " +
            "status = #{status}, updated_at = CURRENT_TIMESTAMP where id = #{id}")
    int updateUser(AuthUser user);

    @Update("update mr_auth_user set password_hash = #{passwordHash}, password_changed_at = CURRENT_TIMESTAMP, " +
            "must_change_password = false, temporary_password_expires_at = null, " +
            "password_version = password_version + 1, updated_at = CURRENT_TIMESTAMP where id = #{id} "
            + "and status = 'active' and password_version = #{expectedVersion} "
            + "and must_change_password = #{required} "
            + "and (not #{required} or temporary_password_expires_at is null "
            + "or temporary_password_expires_at > clock_timestamp())")
    int changePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash,
                       @Param("expectedVersion") int expectedVersion, @Param("required") boolean required);

    @Update("update mr_auth_user set password_hash = #{passwordHash}, must_change_password = true, " +
            "temporary_password_expires_at = #{expiresAt}, password_reset_at = CURRENT_TIMESTAMP, " +
            "password_reset_by = #{resetBy}, password_version = password_version + 1, " +
            "updated_at = CURRENT_TIMESTAMP where id = #{id} and status = 'active' "
            + "and password_version = #{expectedVersion}")
    int resetPassword(@Param("id") Long id,
                      @Param("passwordHash") String passwordHash,
                      @Param("expiresAt") LocalDateTime expiresAt,
                      @Param("resetBy") Long resetBy,
                      @Param("expectedVersion") int expectedVersion);

    // Account-state transitions invalidate both old Bearer and image-cookie credential versions.
    @Update("update mr_auth_user set status = #{status}, " +
            "password_version = password_version + CASE WHEN status IS DISTINCT FROM #{status} THEN 1 ELSE 0 END, " +
            "updated_at = CURRENT_TIMESTAMP where id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    @Insert("insert into mr_auth_user (username, display_name, password_hash, role_code, status, " +
            "contact_info, apply_remark, applied_at, must_change_password, password_version, " +
            "temporary_password_expires_at, created_by) " +
            "values (#{username}, #{displayName}, #{passwordHash}, #{roleCode}, #{status}, " +
            "#{contactInfo}, #{applyRemark}, #{appliedAt}, #{mustChangePassword}, #{passwordVersion}, " +
            "#{temporaryPasswordExpiresAt}, #{createdBy})")
    int insertUser(AuthUser user);

    @Update("update mr_auth_user set role_code = #{roleCode}, status = 'active', " +
            "reviewed_at = CURRENT_TIMESTAMP, reviewed_by = #{reviewedBy}, reject_reason = null, " +
            "updated_at = CURRENT_TIMESTAMP where id = #{id} and lower(status) = 'pending'")
    int approveRegistration(@Param("id") Long id,
                            @Param("roleCode") String roleCode,
                            @Param("reviewedBy") Long reviewedBy);

    @Update("update mr_auth_user set status = 'rejected', reviewed_at = CURRENT_TIMESTAMP, " +
            "reviewed_by = #{reviewedBy}, reject_reason = #{rejectReason}, updated_at = CURRENT_TIMESTAMP " +
            "where id = #{id} and lower(status) = 'pending'")
    int rejectRegistration(@Param("id") Long id,
                           @Param("rejectReason") String rejectReason,
                           @Param("reviewedBy") Long reviewedBy);

    // Retain the previous decision until the next review; no role or credential changes.
    @Update("update mr_auth_user set status = 'pending', updated_at = CURRENT_TIMESTAMP " +
            "where id = #{id} and lower(status) = 'rejected'")
    int reopenRegistration(@Param("id") Long id);

    @Select("select count(*) from mr_auth_user where upper(role_code) = 'ADMIN' and lower(status) = 'active'")
    int countActiveAdmins();

    @Select({
            "<script>",
            "SELECT u.id, u.username, u.display_name, u.role_code, u.status, u.contact_info, u.apply_remark,",
            "u.applied_at, u.reviewed_at, u.reviewed_by, u.reject_reason, u.must_change_password, u.password_version,",
            "u.password_changed_at, u.temporary_password_expires_at, u.created_by, u.password_reset_at,",
            "u.password_reset_by, u.last_login_at, u.created_at, u.updated_at, r.name as role_name, r.permissions as permissions_csv",
            "FROM mr_auth_user u LEFT JOIN mr_auth_role r ON u.role_code = r.code",
            "<where>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (LOWER(u.username) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(u.display_name, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(u.contact_info, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
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
            "ORDER BY CASE WHEN LOWER(u.status) = 'pending' THEN 0 ELSE 1 END, " +
                    "COALESCE(u.applied_at, u.created_at) DESC, u.id DESC LIMIT #{limit} OFFSET #{offset}",
            "</script>"
    })
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "username", column = "username"),
            @Result(property = "displayName", column = "display_name"),
            @Result(property = "roleCode", column = "role_code"),
            @Result(property = "roleName", column = "role_name"),
            @Result(property = "permissionsCsv", column = "permissions_csv"),
            @Result(property = "status", column = "status"),
            @Result(property = "contactInfo", column = "contact_info"),
            @Result(property = "applyRemark", column = "apply_remark"),
            @Result(property = "appliedAt", column = "applied_at"),
            @Result(property = "reviewedAt", column = "reviewed_at"),
            @Result(property = "reviewedBy", column = "reviewed_by"),
            @Result(property = "rejectReason", column = "reject_reason"),
            @Result(property = "mustChangePassword", column = "must_change_password"),
            @Result(property = "passwordVersion", column = "password_version"),
            @Result(property = "passwordChangedAt", column = "password_changed_at"),
            @Result(property = "temporaryPasswordExpiresAt", column = "temporary_password_expires_at"),
            @Result(property = "createdBy", column = "created_by"),
            @Result(property = "passwordResetAt", column = "password_reset_at"),
            @Result(property = "passwordResetBy", column = "password_reset_by"),
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
            "      OR LOWER(COALESCE(u.contact_info, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
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
