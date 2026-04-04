package com.zjcxph.imgapi.mapper;

import com.zjcxph.imgapi.entity.ImageMigrationLog;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ImageMigrationLogMapper {

    @Insert("INSERT INTO image_migration_log (scan_id, local_path, oss_url, migration_status, " +
            "error_message, file_size, checksum_md5, migrated_at) " +
            "VALUES (#{scanId}, #{localPath}, #{ossUrl}, #{migrationStatus}, " +
            "#{errorMessage}, #{fileSize}, #{checksumMd5}, #{migratedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ImageMigrationLog log);

    @Update("UPDATE image_migration_log SET migration_status = #{migrationStatus}, " +
            "oss_url = #{ossUrl}, error_message = #{errorMessage}, " +
            "file_size = #{fileSize}, checksum_md5 = #{checksumMd5}, " +
            "migrated_at = #{migratedAt}, updated_at = NOW() " +
            "WHERE id = #{id}")
    int update(ImageMigrationLog log);

    @Select("SELECT * FROM image_migration_log WHERE scan_id = #{scanId} " +
            "ORDER BY created_at DESC LIMIT 1")
    ImageMigrationLog findByScanId(@Param("scanId") Integer scanId);

    @Select("SELECT * FROM image_migration_log WHERE migration_status = #{status} " +
            "ORDER BY created_at DESC LIMIT #{limit}")
    List<ImageMigrationLog> findByStatus(@Param("status") String status, @Param("limit") int limit);

    @Select("<script>" +
            "SELECT * FROM image_migration_log " +
            "<where>" +
            "<if test='status != null and status != \"\"'>AND migration_status = #{status}</if>" +
            "</where>" +
            "ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<ImageMigrationLog> findWithPagination(@Param("status") String status,
                                                @Param("offset") int offset,
                                                @Param("limit") int limit);

    @Select("<script>" +
            "SELECT COUNT(*) FROM image_migration_log " +
            "<where>" +
            "<if test='status != null and status != \"\"'>AND migration_status = #{status}</if>" +
            "</where>" +
            "</script>")
    long countWithFilter(@Param("status") String status);

    @Select("SELECT migration_status, COUNT(*) as cnt FROM image_migration_log " +
            "GROUP BY migration_status")
    @Results({
            @Result(property = "migrationStatus", column = "migration_status"),
            @Result(property = "id", column = "cnt")
    })
    List<ImageMigrationLog> getStatusCounts();
}
