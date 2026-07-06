package com.zjcxph.imgapi.mapper;

import com.zjcxph.imgapi.entity.MigrationJob;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface MigrationJobMapper {

    @Select("select * from migration_job order by created_at desc limit #{size} offset #{offset}")
    List<MigrationJob> findAllPaginated(@Param("offset") int offset, @Param("size") int size);

    @Select("select count(*) from migration_job")
    int countAll();

    @Select("select * from migration_job where id = #{id}")
    MigrationJob findById(@Param("id") Long id);

    @Select("select * from migration_job where status = 'running' or status = 'pending' order by created_at desc limit 1")
    MigrationJob findLatestActive();

    @Insert("insert into migration_job (status, total_count, processed_count, failed_count, rate, created_by, started_at, created_at, updated_at) "
            + "values (#{status}, #{totalCount}, #{processedCount}, #{failedCount}, #{rate}, #{createdBy}, #{startedAt}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(MigrationJob job);

    @Update("update migration_job set status = #{status}, processed_count = #{processedCount}, "
            + "failed_count = #{failedCount}, rate = #{rate}, error_message = #{errorMessage}, "
            + "started_at = #{startedAt}, completed_at = #{completedAt}, updated_at = NOW() where id = #{id}")
    int update(MigrationJob job);
}
