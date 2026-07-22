package com.zjcxph.imgapi.mapper;

import com.zjcxph.imgapi.entity.ImageMigrationLog;
import com.zjcxph.imgapi.entity.Scan;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * OSS 迁移管理页面专用查询。
 *
 * <p>这里的列表查询只返回受限数量的数据，不执行大表精确总数统计。</p>
 */
public interface OssMigrationManagementMapper {

    @Select("<script>"
            + "SELECT * FROM mr_scan WHERE uploadflag != 0 "
            + "AND (oss_url IS NULL OR oss_url = '') "
            + "AND " + ScanMapper.MIGRATION_ELIGIBLE_EXPRESSION + " "
            + "<if test='folder != null and folder != \"\"'>AND folder = #{folder} </if>"
            + "<if test='bah != null and bah != \"\"'>AND BTRIM(BAH) = #{bah} </if>"
            + "<if test='sjh != null and sjh != \"\"'>AND BTRIM(SJH) = #{sjh} </if>"
            + "ORDER BY id LIMIT #{limit}"
            + "</script>")
    List<Scan> findPending(@Param("folder") String folder,
                           @Param("bah") String bah,
                           @Param("sjh") String sjh,
                           @Param("limit") int limit);

    @Select("<script>"
            + "SELECT * FROM mr_scan WHERE uploadflag != 0 "
            + "AND (oss_url IS NULL OR oss_url = '') "
            + "AND NOT (" + ScanMapper.VALID_SJH_EXPRESSION + ") "
            + "<if test='folder != null and folder != \"\"'>AND folder = #{folder} </if>"
            + "<if test='bah != null and bah != \"\"'>AND BTRIM(BAH) = #{bah} </if>"
            + "<if test='sjh != null and sjh != \"\"'>AND BTRIM(SJH) = #{sjh} </if>"
            + "ORDER BY id LIMIT #{limit}"
            + "</script>")
    List<Scan> findWaitingSjh(@Param("folder") String folder,
                              @Param("bah") String bah,
                              @Param("sjh") String sjh,
                              @Param("limit") int limit);

    @Select("<script>"
            + "SELECT * FROM image_migration_log "
            + "<where>"
            + "<if test='status != null and status != \"\"'>AND migration_status = #{status}</if>"
            + "<if test='scanId != null'>AND scan_id = #{scanId}</if>"
            + "</where>"
            + "ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    @Results(id = "ossMigrationManagementLogMap", value = {
            @Result(property = "id", column = "id"),
            @Result(property = "scanId", column = "scan_id"),
            @Result(property = "localPath", column = "local_path"),
            @Result(property = "ossUrl", column = "oss_url"),
            @Result(property = "migrationStatus", column = "migration_status"),
            @Result(property = "errorMessage", column = "error_message"),
            @Result(property = "fileSize", column = "file_size"),
            @Result(property = "checksumMd5", column = "checksum_md5"),
            @Result(property = "migratedAt", column = "migrated_at"),
            @Result(property = "verifiedAt", column = "verified_at"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    List<ImageMigrationLog> findLogs(@Param("status") String status,
                                     @Param("scanId") Integer scanId,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    @Select("<script>"
            + "SELECT COUNT(*) FROM image_migration_log "
            + "<where>"
            + "<if test='status != null and status != \"\"'>AND migration_status = #{status}</if>"
            + "<if test='scanId != null'>AND scan_id = #{scanId}</if>"
            + "</where>"
            + "</script>")
    long countLogs(@Param("status") String status, @Param("scanId") Integer scanId);
}
