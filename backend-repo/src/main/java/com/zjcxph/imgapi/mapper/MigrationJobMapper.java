package com.zjcxph.imgapi.mapper;

import com.zjcxph.imgapi.entity.MigrationJob;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface MigrationJobMapper {

    @Select("SELECT * FROM migration_job ORDER BY created_at DESC LIMIT #{size} OFFSET #{offset}")
    List<MigrationJob> findAllPaginated(@Param("offset") int offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM migration_job")
    int countAll();

    @Select("SELECT * FROM migration_job WHERE id = #{id}")
    MigrationJob findById(@Param("id") Long id);

    @Select("SELECT * FROM migration_job " +
            "WHERE status IN ('running', 'pending') ORDER BY created_at DESC LIMIT 1")
    MigrationJob findLatestActive();

    @Insert("INSERT INTO migration_job (status, total_count, processed_count, failed_count, rate, " +
            "created_by, max_scan_id, started_at, created_at, updated_at) " +
            "VALUES (#{status}, #{totalCount}, #{processedCount}, #{failedCount}, #{rate}, " +
            "#{createdBy}, #{maxScanId}, #{startedAt}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(MigrationJob job);

    @Update("UPDATE migration_job SET status = #{status}, processed_count = #{processedCount}, " +
            "failed_count = #{failedCount}, rate = #{rate}, error_message = #{errorMessage}, " +
            "max_scan_id = #{maxScanId}, started_at = #{startedAt}, completed_at = #{completedAt}, " +
            "updated_at = NOW() WHERE id = #{id}")
    int update(MigrationJob job);

    @Update("UPDATE migration_job SET status = 'failed', " +
            "error_message = #{reason}, completed_at = NOW(), updated_at = NOW() " +
            "WHERE status IN ('pending', 'running')")
    int markActiveJobsInterrupted(@Param("reason") String reason);
}
